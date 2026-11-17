package com.expeysi.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.R
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.CounterpartyDao
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.DebtAdjustmentDao
import com.expeysi.app.data.db.DebtAdjustmentEntity
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.AccountTotal
import com.expeysi.app.domain.AggEntry
import com.expeysi.app.domain.Aggregates
import com.expeysi.app.domain.CategoryTotal
import com.expeysi.app.domain.DateRangeFilter
import com.expeysi.app.domain.DebtCalculator
import com.expeysi.app.domain.DebtWriteOffs
import com.expeysi.app.domain.HistoryPeriod
import com.expeysi.app.domain.TransferFees
import com.expeysi.app.domain.Transfers
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.common.UiText
import com.expeysi.app.ui.debts.currencyCodeOfAccount
import com.expeysi.app.ui.home.FeedEntries
import com.expeysi.app.ui.home.TransactionItem
import com.expeysi.app.ui.home.TransferAccount
import com.expeysi.app.ui.home.TransferCard
import com.expeysi.app.ui.home.TransferSide
import com.expeysi.app.ui.icons.CategoryPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.abs

/** Псевдотип «перевод» в аргументе type: обе ноги перевода, {transfer_out, transfer_in}. */
private const val TRANSFER_TYPES_WIRE = "transfer"

enum class HistoryViewMode { LIST, CATEGORIES, ACCOUNTS }

data class HistoryDetail(val categoryId: String, val accountId: String? = null)

data class HistoryUiState(
    val title: UiText = UiText.Plain(""),
    val isAccountMode: Boolean = false,
    /** Не null в режиме истории одного счёта: оттуда можно открыть его правку. */
    val accountId: String? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val items: List<TransactionItem> = emptyList(),
    val mode: HistoryViewMode = HistoryViewMode.LIST,
    val detail: HistoryDetail? = null,
    val detailTitle: UiText? = null,
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val accountTotals: List<AccountTotal> = emptyList(),
    val isIncomeOnly: Boolean = false,
    val isTransferOnly: Boolean = false,
    val totalExpenseMinor: Long = 0,
    val totalIncomeMinor: Long = 0,
    val currencySymbol: String = "",
)

private data class BaseData(
    val balances: List<BalanceEntity>,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    /** Списания «в отчётах» (countInStats): входят в итоги доходов/расходов. */
    val writeOffs: List<DebtAdjustmentEntity> = emptyList(),
    /** Все корректировки: списания закрывают долг и вне учёта в отчётах. */
    val adjustments: List<DebtAdjustmentEntity> = emptyList(),
)

private data class Filters(
    val period: HistoryPeriod,
    val customRange: DateRangeFilter?,
    val types: Set<TxType>,
    val categoryIds: Set<String>?,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    balanceDao: BalanceDao,
    accountDao: AccountDao,
    categoryDao: CategoryDao,
    counterpartyDao: CounterpartyDao,
    currencyDao: CurrencyDao,
    debtAdjustmentDao: DebtAdjustmentDao,
    transactionDao: TransactionDao,
) : ViewModel() {

    private val balanceId: String? = savedStateHandle["balanceId"]
    private val accountId: String? = savedStateHandle["accountId"]
    private val isAccountMode = accountId != null

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _period = MutableStateFlow(HistoryPeriod.MONTH)
    val period: StateFlow<HistoryPeriod> = _period.asStateFlow()

    private val _customRange = MutableStateFlow(
        savedStateHandle.get<Int>("days")?.takeIf { it > 0 }?.let { days ->
            val today = LocalDate.now(zone)
            DateRangeFilter.ofDays(today.minusDays(days - 1L), today, zone)
        },
    )
    val customRange: StateFlow<DateRangeFilter?> = _customRange.asStateFlow()

    private val _selectedTypes = MutableStateFlow(
        // «transfer» — псевдотип отчёта «Переводы»: обе ноги, реальное хранение TxType не знает.
        // Долговые операции показываются в списке со всеми: это реальные движения
        // денег, но в разрезки по категориям/счетам и итоги доходов/расходов
        // они не попадают — долг не доход и не расход.
        when (val wire = savedStateHandle.get<String>("type")) {
            TRANSFER_TYPES_WIRE -> setOf(TxType.TRANSFER_OUT, TxType.TRANSFER_IN)
            null -> TxType.entries.toSet()
            else -> setOf(TxType.fromWire(wire))
        },
    )
    val selectedTypes: StateFlow<Set<TxType>> = _selectedTypes.asStateFlow()

    private val _selectedCategoryIds = MutableStateFlow<Set<String>?>(null)
    val selectedCategoryIds: StateFlow<Set<String>?> = _selectedCategoryIds.asStateFlow()

    private val _mode = MutableStateFlow(HistoryViewMode.LIST)
    private val _detail = MutableStateFlow<HistoryDetail?>(null)

    private val base: StateFlow<BaseData> = combine(
        balanceDao.observeActive(),
        accountDao.observeActive(),
        categoryDao.observeAll(),
        transactionDao.observeActive(),
        debtAdjustmentDao.observeAll(),
    ) { balances, accounts, allCategories, transactions, adjustments ->
        BaseData(
            balances = balances,
            accounts = accounts,
            categories = allCategories.filter {
                !it.deleted && (it.type == TxType.EXPENSE.wire || it.type == TxType.INCOME.wire)
            },
            transactions = transactions,
            writeOffs = adjustments.filter { !it.deleted && it.countInStats },
            adjustments = adjustments,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BaseData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))

    private val currencies: StateFlow<Map<String, String>> = currencyDao.observeAll()
        .map { list -> list.associate { it.code to it.symbol } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Символы валют плюс имена контрагентов одним слотом: у combine лимит в пять потоков. */
    private val symbolsAndCounterpartyNames: StateFlow<Pair<Map<String, String>, Map<String, String>>> =
        combine(
            currencies,
            counterpartyDao.observeActive(),
        ) { symbols, counterparties ->
            symbols to counterparties.associate { it.id to it.name }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyMap<String, String>() to emptyMap(),
        )

    private val filters = combine(
        _period,
        _customRange,
        _selectedTypes,
        _selectedCategoryIds,
    ) { period, range, types, categoryIds ->
        Filters(period, range, types, categoryIds)
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        base,
        symbolsAndCounterpartyNames,
        filters,
        _mode,
        _detail,
    ) { data, symbolsAndNames, f, mode, detail ->
        val (symbols, counterpartyNames) = symbolsAndNames
        val scopeAccounts = if (isAccountMode) {
            data.accounts.filter { it.id == accountId }
        } else {
            data.accounts.filter { it.balanceId == balanceId }
        }
        val scopeIds = scopeAccounts.map { it.id }.toSet()
        val balance = data.balances.firstOrNull { it.id == balanceId }
            ?: scopeAccounts.firstOrNull()?.let { a -> data.balances.firstOrNull { it.id == a.balanceId } }
        val symbol = balance?.let { symbols[it.currencyCode] ?: it.currencyCode } ?: ""

        val title = if (isAccountMode) {
            scopeAccounts.firstOrNull()?.name?.let { UiText.Plain(it) } ?: UiText.Res(R.string.common_account)
        } else {
            balance?.name?.let { UiText.Plain(it) } ?: UiText.Res(R.string.common_balance)
        }

        val categoryById = data.categories.associateBy { it.id }
        val accountById = data.accounts.associateBy { it.id }
        val periodStart = f.period.startMillis(ZonedDateTime.now(zone))

        // Дата в фильтр-окне отчёта: у списаний то же окно, что у транзакций.
        val windowRange = f.customRange
        fun inWindow(date: Long): Boolean = when {
            windowRange != null -> date >= windowRange.startMillis && date < windowRange.endMillisExclusive
            periodStart != null -> date >= periodStart
            else -> true
        }

        val filtered = data.transactions
            .filter { it.accountId in scopeIds }
            .filter { inWindow(it.date) }
            .filter { TxType.fromWire(it.type) in f.types }
            .filter { f.categoryIds == null || it.categoryId in f.categoryIds }

        // Разбивки по категориям и счетам — только потоки денег; ноги переводов
        // в донышках анализов и в итогах доходов/расходов не участвуют.
        // Исключение — комиссия перевода: реальная потеря денег, входит в
        // расходы как псевдо-категория TransferFees.CATEGORY_ID на счёте-источнике.
        // Долговые операции тоже не входят: долг не доход и не расход, а своего
        // «псевдо-итога» в разрезках у долгов нет — их место в разделе «Долгов».
        val feeEntries = if (TxType.EXPENSE in f.types && f.categoryIds == null) {
            filtered
                .filter { TxType.fromWire(it.type) == TxType.TRANSFER_OUT && it.feeMinor > 0 }
                .map { AggEntry(TransferFees.CATEGORY_ID, it.accountId, TxType.EXPENSE, it.feeMinor) }
        } else {
            emptyList()
        }

        // Списание «в отчётах» — безналичный плюс/минус долга: у корректировки
        // нет ни счёта, ни категории, поэтому в разрезках и итогах оно живёт
        // строкой DebtWriteOffs.CATEGORY_ID (и псевдо-«счётом» там же) по валюте
        // скоупа. Плюс — как доход, минус — как расход (переключатель формы),
        // фильтр категорий к списанию неприменим — при нём списания не добавляются.
        val statWriteOffs = data.writeOffs
            .filter { it.currencyCode == balance?.currencyCode && inWindow(it.date) }
        val writeOffEntries = if (f.categoryIds == null) {
            statWriteOffs
                .filter {
                    (TxType.INCOME in f.types && it.amountMinor > 0L) ||
                        (TxType.EXPENSE in f.types && it.amountMinor < 0L)
                }
                .map {
                    AggEntry(
                        categoryId = DebtWriteOffs.CATEGORY_ID,
                        accountId = DebtWriteOffs.CATEGORY_ID,
                        type = if (it.amountMinor > 0L) TxType.INCOME else TxType.EXPENSE,
                        amountMinor = abs(it.amountMinor),
                    )
                }
        } else {
            emptyList()
        }

        val entries = filtered
            .filterNot { TxType.fromWire(it.type).isTransfer }
            .filterNot { TxType.fromWire(it.type).isDebt }
            .map {
                AggEntry(it.categoryId, it.accountId, TxType.fromWire(it.type), it.amountMinor)
            } + feeEntries + writeOffEntries

        val scopedTransactions = filtered
            .filter { detail == null || it.categoryId == detail.categoryId }
            .filter { detail?.accountId == null || it.accountId == detail?.accountId }
        // Партнёрская нога ищется в общем журнале независимо от скоупа: карточка
        // перевода всегда показывает обе стороны, даже если вторая нога в другом
        // балансе или ещё не доехала по синку.
        val legsByLinkedId = data.transactions
            .filter { !it.deleted && it.linkedId.isNotEmpty() }
            .groupBy { it.linkedId }
        // Пары «контрагент × валюта», закрытые списанием, — по всему хранилищу:
        // остаток долга глобален, а не по скоупу экрана. Строки этих пар
        // помечаются «долг списан».
        val balancesById = data.balances.associateBy { it.id }
        val currencyByAccount = data.accounts.associate { it.id to currencyCodeOfAccount(it, balancesById) }
        val closedByWriteOff = DebtCalculator.pairsClosedByWriteOff(
            DebtCalculator.transactionDeltas(data.transactions, { currencyByAccount[it].orEmpty() }) +
                DebtCalculator.adjustmentDeltas(data.adjustments),
            data.adjustments,
        )
        val txItems = scopedTransactions
            .let(Transfers::collapse)
            .map { tx ->
                val category = categoryById[tx.categoryId]
                val txType = TxType.fromWire(tx.type)
                val counterpartyName =
                    if (txType.isDebt) counterpartyNames[tx.counterpartyId].orEmpty() else ""
                TransactionItem(
                    transaction = tx,
                    categoryName = category?.name ?: "",
                    categoryIconKey = category?.iconKey ?: "",
                    categoryColorKey = category?.colorKey ?: CategoryPalette.DEFAULT_KEY,
                    accountName = accountById[tx.accountId]?.name ?: "",
                    currencySymbol = symbol,
                    counterpartyName = counterpartyName,
                    isTransfer = txType.isTransfer,
                    counterpartAccountName = accountById[tx.counterpartyAccountId]?.name ?: "",
                    isTransferPair = Transfers.isCollapsedPair(tx, scopedTransactions),
                    transferCard = buildTransferCard(tx, legsByLinkedId, accountById, symbols),
                    isDebtWrittenOff = txType.isDebt &&
                        tx.counterpartyId.isNotEmpty() &&
                        (tx.counterpartyId to currencyByAccount[tx.accountId].orEmpty()) in closedByWriteOff,
                )
            }
        // Списания «в отчётах» видят свои строки: итоги доходов/расходов
        // уже включают их, и в списке число должно быть объяснимо. Условия
        // те же, что у writeOffEntries выше: без фильтра категорий и при
        // совпадении знака с выбранным типом; в детализации категории
        // псевдо-строки не показываются — у неё нет операций.
        val items = (
            txItems +
                (
                    if (f.categoryIds == null && detail == null) {
                        statWriteOffs
                            .filter {
                                (TxType.INCOME in f.types && it.amountMinor > 0L) ||
                                    (TxType.EXPENSE in f.types && it.amountMinor < 0L)
                            }
                            .map { FeedEntries.writeOffItem(it, symbol) }
                    } else {
                        emptyList()
                    }
                    )
            )
            // Единая хронология по убыванию даты: операции и строки списаний
            // смешиваются в один поток ленты.
            .sortedByDescending { it.transaction.date }

        HistoryUiState(
            title = title,
            isAccountMode = isAccountMode,
            accountId = accountId,
            accounts = if (isAccountMode) emptyList() else scopeAccounts,
            categories = data.categories,
            items = items,
            mode = mode,
            detail = detail,
            detailTitle = detail?.let { d ->
                val categoryName = categoryById[d.categoryId]?.name
                val accountName = d.accountId?.let { accountById[it]?.name }
                // Имя категории может отсутствовать — фолбэк «Без категории» резолвится в UI.
                val categoryText = categoryName?.takeIf { it.isNotBlank() }?.let { UiText.Plain(it) }
                    ?: UiText.Res(R.string.report_uncategorized)
                when {
                    accountName == null -> categoryText
                    else -> UiText.Res(R.string.history_detail_title_parts, categoryText, accountName)
                }
            },
            categoryTotals = Aggregates.byCategory(entries),
            accountTotals = Aggregates.byAccount(entries),
            isIncomeOnly = f.types == setOf(TxType.INCOME),
            isTransferOnly = f.types == setOf(TxType.TRANSFER_OUT, TxType.TRANSFER_IN),
            totalExpenseMinor = entries.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor },
            totalIncomeMinor = entries.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor },
            currencySymbol = symbol,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectPeriod(value: HistoryPeriod) {
        _period.value = value
        _customRange.value = null
        _detail.value = null
    }

    fun selectRange(start: LocalDate, endInclusive: LocalDate) {
        _customRange.value = DateRangeFilter.ofDays(start, endInclusive, zone)
        _detail.value = null
    }

    fun toggleType(type: TxType) {
        _selectedTypes.update { current ->
            if (type in current) current - type else current + type
        }
        _detail.value = null
    }

    fun toggleCategory(id: String) {
        val allIds = uiState.value.categories.map { it.id }.toSet()
        _selectedCategoryIds.update { current ->
            val effective = current ?: allIds
            val updated = if (id in effective) effective - id else effective + id
            if (updated == allIds) null else updated
        }
        _detail.value = null
    }

    fun selectMode(value: HistoryViewMode) {
        _mode.value = value
        _detail.value = null
    }

    fun openDetail(categoryId: String, accountId: String? = null) {
        // Псевдо-категории комиссий и списаний не открываются: за ними нет
        // записей в БД.
        if (categoryId == TransferFees.CATEGORY_ID || categoryId == DebtWriteOffs.CATEGORY_ID) return
        _detail.value = HistoryDetail(categoryId, accountId)
    }

    fun closeDetail() {
        _detail.value = null
    }
}

/**
 * Развёрнутые данные перевода для карточки истории. Обе ноги ищутся в общем
 * журнале по связке; без ноги-партнёра счёт на той стороне берётся из
 * [TransactionEntity.counterpartyAccountId]. Суммы ног — в валюте каждого счёта:
 * у ноги списания это отправленное без комиссии ([TransactionEntity.amountMinor]
 * хранит «всего списано» с комиссией), у ноги зачисления — полученное.
 * null у доходов и расходов.
 */
private fun buildTransferCard(
    leg: TransactionEntity,
    legsByLinkedId: Map<String, List<TransactionEntity>>,
    accountById: Map<String, AccountEntity>,
    symbols: Map<String, String>,
): TransferCard? {
    val legType = TxType.fromWire(leg.type)
    if (!legType.isTransfer) return null
    val legs = legsByLinkedId[leg.linkedId].orEmpty()
    val outLeg = if (legType == TxType.TRANSFER_OUT) {
        leg
    } else {
        legs.firstOrNull { TxType.fromWire(it.type) == TxType.TRANSFER_OUT }
    }
    val inLeg = if (legType == TxType.TRANSFER_IN) {
        leg
    } else {
        legs.firstOrNull { TxType.fromWire(it.type) == TxType.TRANSFER_IN }
    }
    val sourceId = outLeg?.accountId ?: leg.counterpartyAccountId
    val destinationId = inLeg?.accountId ?: leg.counterpartyAccountId
    val source = accountById[sourceId]
    val destination = accountById[destinationId]

    fun cardAccount(account: AccountEntity?, id: String) = TransferAccount(
        id = id,
        name = account?.name ?: "",
        iconKey = account?.iconKey ?: "",
        colorKey = account?.colorKey ?: CategoryPalette.DEFAULT_KEY,
    )

    fun symbolOf(account: AccountEntity?): String = account
        ?.let { symbols[it.currencyCode] ?: it.currencyCode }
        .orEmpty()

    // Ключ серии одинаковых переводов подряд: счета-участники и валюты ног.
    // Собрать его можно только когда известны обе ноги и оба счёта.
    val groupKey = if (outLeg != null && inLeg != null && source != null && destination != null) {
        "$sourceId|$destinationId|${source.currencyCode}|${destination.currencyCode}"
    } else {
        ""
    }
    return TransferCard(
        source = cardAccount(source, sourceId),
        destination = cardAccount(destination, destinationId),
        sentMinor = outLeg?.let { it.amountMinor - it.feeMinor },
        sourceCurrencySymbol = symbolOf(source),
        sourceCurrencyCode = source?.currencyCode ?: "",
        // Комиссия хранится на ноге списания; у ноги зачисления она берётся у партнёра.
        feeMinor = outLeg?.feeMinor ?: 0L,
        receivedMinor = inLeg?.amountMinor,
        receivedCurrencySymbol = symbolOf(destination),
        receivedCurrencyCode = destination?.currencyCode ?: "",
        ownSide = if (legType == TxType.TRANSFER_OUT) TransferSide.SOURCE else TransferSide.DESTINATION,
        date = leg.date,
        note = leg.note,
        groupKey = groupKey,
    )
}
