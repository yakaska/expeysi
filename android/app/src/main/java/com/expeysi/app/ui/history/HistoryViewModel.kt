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
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.AccountTotal
import com.expeysi.app.domain.AggEntry
import com.expeysi.app.domain.Aggregates
import com.expeysi.app.domain.CategoryTotal
import com.expeysi.app.domain.DateRangeFilter
import com.expeysi.app.domain.HistoryPeriod
import com.expeysi.app.domain.Transfers
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.common.UiText
import com.expeysi.app.ui.icons.CategoryPalette
import com.expeysi.app.ui.home.TransactionItem
import com.expeysi.app.ui.home.TransferAccount
import com.expeysi.app.ui.home.TransferCard
import com.expeysi.app.ui.home.TransferSide
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
    currencyDao: CurrencyDao,
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
        categoryDao.observeActive(TxType.EXPENSE.wire),
        categoryDao.observeActive(TxType.INCOME.wire),
        transactionDao.observeActive(),
    ) { balances, accounts, expenseCategories, incomeCategories, transactions ->
        BaseData(
            balances = balances,
            accounts = accounts,
            categories = expenseCategories + incomeCategories,
            transactions = transactions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BaseData(emptyList(), emptyList(), emptyList(), emptyList()))

    private val currencies: StateFlow<Map<String, String>> = currencyDao.observeAll()
        .map { list -> list.associate { it.code to it.symbol } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
        currencies,
        filters,
        _mode,
        _detail,
    ) { data, symbols, f, mode, detail ->
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

        val filtered = data.transactions
            .filter { it.accountId in scopeIds }
            .filter { tx ->
                val range = f.customRange
                when {
                    range != null -> tx.date >= range.startMillis && tx.date < range.endMillisExclusive
                    periodStart != null -> tx.date >= periodStart
                    else -> true
                }
            }
            .filter { TxType.fromWire(it.type) in f.types }
            .filter { f.categoryIds == null || it.categoryId in f.categoryIds }

        // Разбивки по категориям и счетам — только потоки денег; ноги переводов
        // в донышках анализов и в итогах доходов/расходов не участвуют.
        val entries = filtered
            .filterNot { TxType.fromWire(it.type).isTransfer }
            .map {
                AggEntry(it.categoryId, it.accountId, TxType.fromWire(it.type), it.amountMinor)
            }

        val scopedTransactions = filtered
            .filter { detail == null || it.categoryId == detail.categoryId }
            .filter { detail?.accountId == null || it.accountId == detail?.accountId }
        // Партнёрская нога ищется в общем журнале независимо от скоупа: карточка
        // перевода всегда показывает обе стороны, даже если вторая нога в другом
        // балансе или ещё не доехала по синку.
        val legsByLinkedId = data.transactions
            .filter { !it.deleted && it.linkedId.isNotEmpty() }
            .groupBy { it.linkedId }
        val items = scopedTransactions
            .let(Transfers::collapse)
            .map { tx ->
                val category = categoryById[tx.categoryId]
                TransactionItem(
                    transaction = tx,
                    categoryName = category?.name ?: "",
                    categoryIconKey = category?.iconKey ?: "",
                    categoryColorKey = category?.colorKey ?: CategoryPalette.DEFAULT_KEY,
                    accountName = accountById[tx.accountId]?.name ?: "",
                    currencySymbol = symbol,
                    isTransfer = TxType.fromWire(tx.type).isTransfer,
                    counterpartAccountName = accountById[tx.counterpartyAccountId]?.name ?: "",
                    isTransferPair = Transfers.isCollapsedPair(tx, scopedTransactions),
                    transferCard = buildTransferCard(tx, legsByLinkedId, accountById, symbols),
                )
            }

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
