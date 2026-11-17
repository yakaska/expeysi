package com.expeysi.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.R
import com.expeysi.app.data.FamilyRepository
import com.expeysi.app.data.FamilyStore
import com.expeysi.app.data.SelectedBalanceStore
import com.expeysi.app.data.analysis.AnalysisRepository
import com.expeysi.app.data.analysis.CategoryStat
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CounterpartyDao
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.DebtAdjustmentDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.data.importer.AccountKey
import com.expeysi.app.data.importer.BudgetImportError
import com.expeysi.app.data.importer.BudgetImportException
import com.expeysi.app.data.importer.BudgetImportService
import com.expeysi.app.data.importer.BudgetTransaction
import com.expeysi.app.data.importer.accountNets
import com.expeysi.app.data.importer.openingFromCurrent
import com.expeysi.app.domain.BalanceCalculator
import com.expeysi.app.domain.DebtCalculator
import com.expeysi.app.domain.Periods
import com.expeysi.app.domain.Transfers
import com.expeysi.app.domain.TxDayAmount
import com.expeysi.app.domain.TxSum
import com.expeysi.app.domain.TxType
import com.expeysi.app.domain.WindowSums
import com.expeysi.app.domain.Windows
import com.expeysi.app.ui.common.UiText
import com.expeysi.app.ui.debts.currencyCodeOfAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class AccountBalance(
    val account: AccountEntity,
    val balanceMinor: Long,
    val currencySymbol: String,
)

data class TransactionItem(
    val transaction: TransactionEntity,
    val categoryName: String,
    val categoryIconKey: String,
    val categoryColorKey: String,
    val accountName: String,
    val currencySymbol: String,
    /** У долговой операции — имя контрагента для подписи строки; у остальных пусто. */
    val counterpartyName: String = "",
    /** Нога перевода: рисуется иконкой перевода со подписью «откуда → куда». */
    val isTransfer: Boolean = false,
    val counterpartAccountName: String = "",
    /**
     * Строка свёрнутой пары, обе ноги которой видны в списке: рисуется нейтрально,
     * без знака. Одиночная нога показывает движение денег счёта: плюс или минус.
     */
    val isTransferPair: Boolean = false,
    /** Развёрнутые данные перевода для карточек истории; null у доходов и расходов. */
    val transferCard: TransferCard? = null,
    /**
     * Долговая операция, чей долг «контрагент × валюта» закрыт списанием:
     * в подписи строки ставится пометка «долг списан», иначе заём выглядит
     * как открытый долг. У не-долговых строк всегда false.
     */
    val isDebtWrittenOff: Boolean = false,
)

/** Счёт на стороне развёрнутой карточки перевода: имя, иконка и цвет аватара. */
data class TransferAccount(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorKey: String,
)

/** Какая нога перевода принадлежит скоупу текущего списка. */
enum class TransferSide { SOURCE, DESTINATION }

/**
 * Развёрнутые данные перевода для карточки в списке истории: оба счёта с
 * аватарами-иконками, суммы ног в валюте каждого счёта, комиссия отдельным
 * полем, одна дата и заметка на карточку. Партнёрская нога ищется по всему
 * хранилищу, независимо от скоупа списка.
 */
data class TransferCard(
    val source: TransferAccount,
    val destination: TransferAccount,
    /** Сколько списано со счёта-источника без комиссии; null — нога не доехала по синку. */
    val sentMinor: Long?,
    val sourceCurrencySymbol: String,
    /** ISO-код валюты источника: дробность суммы и сторона курса считаются по нему. */
    val sourceCurrencyCode: String,
    /** Комиссия сверх списания в валюте источника; 0 — нет. */
    val feeMinor: Long,
    /** Сколько зачислено на счёт-получатель; null — нога не доехала по синку. */
    val receivedMinor: Long?,
    val receivedCurrencySymbol: String,
    val receivedCurrencyCode: String,
    /** Чья нога в скоупе списка; для знаков и цвета относительно счёта. */
    val ownSide: TransferSide,
    /** Дата перевода; у пары ног общая. */
    val date: Long,
    val note: String,
    /**
     * Идентичность серии для группировки: счета-участники и валюты ног.
     * Пустой — обе ноги не найдены, серию собрать нельзя.
     */
    val groupKey: String,
)

data class ImportUiState(
    val inProgress: Boolean = false,
    val message: UiText? = null,
)

/** Одна строка экрана остатков импорта: счёт файла и нетто его операций. */
data class ImportBalanceRow(
    val accountName: String,
    val currencyCode: String,
    val balanceName: String?,
    val netMinor: Long,
)

data class ImportBalancesUiState(
    val visible: Boolean = false,
    val inProgress: Boolean = false,
    val rows: List<ImportBalanceRow> = emptyList(),
)

data class LeaveFamilyUiState(
    val inProgress: Boolean = false,
    val failed: Boolean = false,
)

private const val DEFAULT_PERIOD_DAYS = 30

data class HomeUiState(
    val balances: List<BalanceEntity> = emptyList(),
    val selectedBalance: BalanceEntity? = null,
    val currencySymbol: String = "",
    val accounts: List<AccountBalance> = emptyList(),
    val totalBalanceMinor: Long = 0,
    /** Итог по счетам каждого баланса; для шторки переключателя. */
    val balanceTotals: Map<String, Long> = emptyMap(),
    /** Символ валюты по id баланса; для шторки переключателя. */
    val balanceSymbols: Map<String, String> = emptyMap(),
    val expense: WindowSums = WindowSums(0, 0, 0),
    val income: WindowSums = WindowSums(0, 0, 0),
    val periodDays: Int = DEFAULT_PERIOD_DAYS,
    /** Расходы по категориям за выбранный период; пусто — блока нет. */
    val topCategories: List<CategoryStat> = emptyList(),
    val transactions: List<TransactionItem> = emptyList(),
    val hasMoreTransactions: Boolean = false,
    val familyId: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    balanceDao: BalanceDao,
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    counterpartyDao: CounterpartyDao,
    currencyDao: CurrencyDao,
    debtAdjustmentDao: DebtAdjustmentDao,
    private val selectedBalanceStore: SelectedBalanceStore,
    private val familyStore: FamilyStore,
    private val familyRepository: FamilyRepository,
    private val budgetImportService: BudgetImportService,
    private val analysisRepository: AnalysisRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()
    private val _importBalancesState = MutableStateFlow(ImportBalancesUiState())
    val importBalancesState: StateFlow<ImportBalancesUiState> = _importBalancesState.asStateFlow()

    /** Разобранный файл, ждущий подтверждения остатков на экране импорта. */
    private var pendingImport: BudgetImportService.ImportPreview? = null
    private val _leaveState = MutableStateFlow(LeaveFamilyUiState())
    val leaveState: StateFlow<LeaveFamilyUiState> = _leaveState.asStateFlow()

    private val periodDays = MutableStateFlow(savedStateHandle.get<Int>(KEY_PERIOD_DAYS) ?: DEFAULT_PERIOD_DAYS)

    private val selection = combine(
        balanceDao.observeActive(),
        selectedBalanceStore.selectedId,
    ) { balances, selectedId ->
        balances to (balances.firstOrNull { it.id == selectedId } ?: balances.firstOrNull())
    }.onEach { (_, selected) ->
        if (selected != null && selectedBalanceStore.selectedId.value != selected.id) {
            selectedBalanceStore.select(selected.id)
        }
    }

    private val transactionLimit = MutableStateFlow(TRANSACTION_PAGE_SIZE)
    private val feedAccountIds = combine(
        selection,
        accountDao.observeAll(),
    ) { (_, selected), accounts ->
        accounts.filter { !it.deleted && it.balanceId == selected?.id }.map { it.id }
    }.distinctUntilChanged().onEach {
        transactionLimit.value = TRANSACTION_PAGE_SIZE
    }

    /**
     * Пары «контрагент × валюта», чей долг закрыт списанием: пометка «долг
     * списан» в ленте. Считается по всем долговым операциям и корректировкам
     * хранилища — расчёт долга глобален, а не по скоупу ленты.
     */
    private val debtsClosedByWriteOff: StateFlow<Set<Pair<String, String>>> = combine(
        transactionDao.observeActive(),
        debtAdjustmentDao.observeAll(),
        accountDao.observeActive(),
        balanceDao.observeAll(),
    ) { transactions, adjustments, accounts, balances ->
        val balancesById = balances.associateBy { it.id }
        val currencyByAccount = accounts.associate { it.id to currencyCodeOfAccount(it, balancesById) }
        DebtCalculator.pairsClosedByWriteOff(
            DebtCalculator.transactionDeltas(transactions, { currencyByAccount[it].orEmpty() }) +
                DebtCalculator.adjustmentDeltas(adjustments),
            adjustments,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val transactionFeed = feedAccountIds.flatMapLatest { accountIds ->
        combine(
            transactionLimit.flatMapLatest { limit ->
                transactionDao.observeActiveForAccounts(accountIds, limit)
                    // Долговые операции идут в общую ленту со всеми: их место
                    // и в разделе «Долгов», но это реальные движения денег.
                    .map { transactions -> transactions to limit }
            },
            accountDao.observeAll(),
            categoryDao.observeAll(),
            // Символы валют, имена контрагентов и помеченные списания — один
            // слот: у combine лимит в пять потоков, а строкам долгов нужен
            // второй справочник.
            combine(
                currencyDao.observeAll(),
                counterpartyDao.observeActive(),
                debtsClosedByWriteOff,
            ) { currencies, counterparties, closedByWriteOff ->
                Triple(
                    currencies.associate { it.code to it.symbol },
                    counterparties.associate { it.id to it.name },
                    closedByWriteOff,
                )
            },
            selection,
        ) { (transactions, limit), accounts, categories, (symbolsByName, counterpartyNames, closedByWriteOff), (balances, selected) ->
            val symbol = selected?.let { symbolsByName[it.currencyCode] ?: it.currencyCode } ?: ""
            val categoryById = categories.associateBy { it.id }
            val accountById = accounts.associateBy { it.id }
            val balancesById = balances.associateBy { it.id }
            fun currencyOf(accountId: String): String =
                accountById[accountId]?.let { currencyCodeOfAccount(it, balancesById) }.orEmpty()
            // Пары переводов внутри одного баланса показываются одной строкой.
            // Нога, чей партнёр отрезан границей страницы, подтягивает его по
            // связке: на стыке страниц пара не разваливается на две строки.
            val page = Transfers.supplementSplitLegs(
                page = transactions,
                scopeIds = accountIds.toSet(),
                linkedLegs = transactionDao::byLinkedId,
            )
            val collapsedTransactions = Transfers.collapse(page)
            val items = collapsedTransactions.map { tx ->
                val category = categoryById[tx.categoryId]
                val txType = TxType.fromWire(tx.type)
                val counterpartyName = if (txType.isDebt) {
                    counterpartyNames[tx.counterpartyId].orEmpty()
                } else {
                    ""
                }
                TransactionItem(
                    transaction = tx,
                    categoryName = category?.name ?: "",
                    categoryIconKey = category?.iconKey ?: "",
                    categoryColorKey = category?.colorKey ?: "default",
                    accountName = accountById[tx.accountId]?.name ?: "",
                    currencySymbol = symbol,
                    counterpartyName = counterpartyName,
                    isTransfer = txType.isTransfer,
                    counterpartAccountName = accountById[tx.counterpartyAccountId]?.name ?: "",
                    isTransferPair = Transfers.isCollapsedPair(tx, page),
                    isDebtWrittenOff = txType.isDebt &&
                        tx.counterpartyId.isNotEmpty() &&
                        (tx.counterpartyId to currencyOf(tx.accountId)) in closedByWriteOff,
                )
            }
            // Признак следующей страницы считается по сырой странице: добранный
            // партнёр в лимит не входит и новую страницу не открывает.
            items to (transactions.size >= limit)
        }
    }

    private val summaryState = combine(
        selection,
        accountDao.observeAll(),
        transactionDao.observeActive(),
        currencyDao.observeAll(),
        debtAdjustmentDao.observeAll(),
    ) { (balances, selected), accounts, transactions, currencies, adjustments ->
        val symbolByCode = currencies.associate { it.code to it.symbol }
        val symbol = selected?.let { symbolByCode[it.currencyCode] ?: it.currencyCode } ?: ""

        val balanceAccounts = accounts.filter { !it.deleted && it.balanceId == selected?.id }
        val accountIds = balanceAccounts.map { it.id }.toSet()
        val balanceTxs = transactions.filter { it.accountId in accountIds }

        val accountBalances = balanceAccounts.map { account ->
            val sums = balanceTxs
                .filter { it.accountId == account.id }
                .map { TxSum(TxType.fromWire(it.type), it.amountMinor) }
            AccountBalance(account, BalanceCalculator.balanceMinor(account.openingBalanceMinor, sums), symbol)
        }

        val todayStart = Periods.day(LocalDate.now(zone), zone).startMillis
        fun txDayAmount(tx: TransactionEntity, amountMinor: Long) = TxDayAmount(
            amountMinor,
            Periods.day(Instant.ofEpochMilli(tx.date).atZone(zone).toLocalDate(), zone).startMillis,
        )
        // Списание долга «в отчётах» — безналичный плюс/минус: у корректировки
        // нет счёта, в сводку баланса попадает по валюте выбранного баланса;
        // плюс — как доход, минус — как расход.
        val statWriteOffs = adjustments
            .filter {
                !it.deleted && it.countInStats &&
                    selected != null && it.currencyCode == selected.currencyCode
            }
            .map { adjustment ->
                TxDayAmount(
                    adjustment.amountMinor,
                    Periods.day(
                        Instant.ofEpochMilli(adjustment.date).atZone(zone).toLocalDate(),
                        zone,
                    ).startMillis,
                )
            }
        fun windowSums(type: TxType, extra: List<TxDayAmount> = emptyList()) = Windows.sums(
            balanceTxs
                .filter { TxType.fromWire(it.type) == type }
                .map { txDayAmount(it, it.amountMinor) } +
                // Комиссия перевода — реальный расход дня: с ног-списаний
                // комиссия входит в окно расходов сверх обычных расходов;
                // у ноги зачисления комиссии нет.
                (
                    if (type == TxType.EXPENSE) {
                        balanceTxs
                            .filter { TxType.fromWire(it.type) == TxType.TRANSFER_OUT }
                            .map { txDayAmount(it, it.feeMinor) }
                    } else {
                        emptyList()
                    }
                    ) +
                extra,
            todayStart,
        )

        HomeUiState(
            balances = balances,
            selectedBalance = selected,
            currencySymbol = symbol,
            accounts = accountBalances,
            totalBalanceMinor = accountBalances.sumOf { it.balanceMinor },
            balanceTotals = balanceTotals(balances, accounts, transactions),
            balanceSymbols = balances.associate { it.id to (symbolByCode[it.currencyCode] ?: it.currencyCode) },
            expense = windowSums(
                TxType.EXPENSE,
                statWriteOffs.filter { it.amountMinor < 0L }.map { TxDayAmount(-it.amountMinor, it.dayStartMillis) },
            ),
            income = windowSums(
                TxType.INCOME,
                statWriteOffs.filter { it.amountMinor > 0L },
            ),
            familyId = familyStore.familyId.value,
        )
    }

    /** Топ расходов по категориям за выбранный период; тот же запрос, что у экрана анализа. */
    private val topCategoriesFeed = combine(
        selection,
        periodDays,
        // Реакция на изменения транзакций: добавление, правка, удаление и синхронизация.
        transactionDao.observeActive(),
    ) { (_, selected), days, _ ->
        selected?.id to days
    }.flatMapLatest { (balanceId, days) ->
        flow {
            val today = Periods.day(LocalDate.now(zone), zone)
            // Скользящее окно, как в сводке (Windows.sums): сегодня + дни назад.
            val start = today.startMillis - (days - 1) * DAY_MILLIS
            // За «сегодня» операции с датой в будущем не учитываем: сводка их не считает.
            val end = if (days == 1) today.endMillisExclusive else null
            val categories = if (balanceId == null) {
                emptyList()
            } else {
                runCatching {
                    analysisRepository
                        .categorySummary(balanceId, TxType.EXPENSE, start, end, null)
                        .categories
                }.getOrDefault(emptyList())
            }
            emit(categories)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        summaryState,
        transactionFeed,
        topCategoriesFeed,
        periodDays,
    ) { state, (transactions, hasMore), topCategories, days ->
        state.copy(
            transactions = transactions,
            hasMoreTransactions = hasMore,
            topCategories = topCategories,
            periodDays = days,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectBalance(id: String) {
        selectedBalanceStore.select(id)
    }

    /** Период верхней сводки и блока топ категорий: 1, 7 или 30 дней. */
    fun selectPeriod(days: Int) {
        if (periodDays.value == days) return
        periodDays.value = days
        savedStateHandle[KEY_PERIOD_DAYS] = days
    }

    /**
     * Файл импорта: сначала разбирается без записи. Внешняя таблица открывает
     * экран остатков («сколько на счете сейчас» — файла их не содержит),
     * файл «Бюджета» записывается сразу — его остатки лежат в строках файла.
     */
    fun importBudget(input: InputStream?) {
        if (_importState.value.inProgress || _importBalancesState.value.inProgress) return
        if (input == null) {
            _importState.update { it.copy(message = UiText.Res(R.string.import_error_open)) }
            return
        }
        viewModelScope.launch {
            _importState.update { it.copy(inProgress = true, message = null) }
            val preview = runCatching { withContext(Dispatchers.IO) { budgetImportService.parse(input) } }
                .onFailure { throwable ->
                    _importState.update {
                        it.copy(
                            inProgress = false,
                            message = (throwable as? BudgetImportException)?.let(::budgetImportMessage)
                                ?: UiText.Res(R.string.import_error_generic),
                        )
                    }
                }
                .getOrNull() ?: return@launch
            _importState.update { it.copy(inProgress = false) }
            if (preview.sheetFormat) {
                pendingImport = preview
                _importBalancesState.update {
                    ImportBalancesUiState(visible = true, rows = importBalanceRows(preview.items))
                }
            } else {
                runImport(preview.items, emptyMap())
            }
        }
    }

    /**
     * Подтверждение остатков с экрана: каждое введенное «сколько сейчас»
     * превращается в первоначальный остаток счета — разницу между введенным
     * и нетто операций файла. Пустые поля счета импорта не задают.
     */
    fun confirmImportBalances(filled: Map<Int, Long>) {
        val state = _importBalancesState.value
        if (!state.visible || state.inProgress) return
        val pending = pendingImport ?: return
        val overrides = state.rows.mapIndexedNotNull { index, row ->
            filled[index]?.let { current ->
                AccountKey(row.accountName, row.currencyCode, row.balanceName) to
                    openingFromCurrent(current, row.netMinor)
            }
        }.toMap()
        viewModelScope.launch {
            _importBalancesState.update { it.copy(inProgress = true) }
            runImport(pending.items, overrides)
            pendingImport = null
            _importBalancesState.value = ImportBalancesUiState()
        }
    }

    /** Экран остатков закрыт без импорта: разобранные строки забываются. */
    fun dismissImportBalances() {
        pendingImport = null
        _importBalancesState.value = ImportBalancesUiState()
    }

    private fun importBalanceRows(items: List<BudgetTransaction>): List<ImportBalanceRow> = accountNets(items).map { (key, net) ->
        ImportBalanceRow(
            accountName = key.name,
            currencyCode = key.currency,
            balanceName = key.balanceName,
            netMinor = net,
        )
    }

    private suspend fun runImport(items: List<BudgetTransaction>, overrides: Map<AccountKey, Long>) {
        _importState.update { it.copy(inProgress = true, message = null) }
        val message = try {
            val result = withContext(Dispatchers.IO) { budgetImportService.importParsed(items, overrides) }
            UiText.Res(R.string.import_done, result.transactionCount)
        } catch (e: BudgetImportException) {
            budgetImportMessage(e)
        } catch (e: Exception) {
            UiText.Res(R.string.import_error_generic)
        } finally {
            _importState.update { it.copy(inProgress = false) }
        }
        _importState.update { it.copy(message = message) }
    }

    /** Ошибка разбора CSV → текст для показа; строка CSV подставляется аргументом. */
    private fun budgetImportMessage(error: BudgetImportException): UiText {
        val line = error.line ?: 0
        return when (error.reason) {
            BudgetImportError.EMPTY_FILE -> UiText.Res(R.string.import_error_empty_file)
            // %1$s — название колонки формата, отсутствующей в файле.
            BudgetImportError.MISSING_COLUMN -> UiText.Res(R.string.import_error_missing_column, error.column.orEmpty())
            BudgetImportError.TOO_FEW_COLUMNS -> UiText.Res(R.string.import_error_too_few_columns, line)
            BudgetImportError.NO_TRANSACTIONS -> UiText.Res(R.string.import_error_no_transactions)
            BudgetImportError.EMPTY_ACCOUNT -> UiText.Res(R.string.import_error_empty_account, line)
            BudgetImportError.EMPTY_CATEGORY -> UiText.Res(R.string.import_error_empty_category, line)
            BudgetImportError.BAD_CURRENCY -> UiText.Res(R.string.import_error_bad_currency, line)
            BudgetImportError.BAD_AMOUNT -> UiText.Res(R.string.import_error_bad_amount, line)
            BudgetImportError.ZERO_AMOUNT -> UiText.Res(R.string.import_error_zero_amount, line)
            BudgetImportError.AMOUNT_TOO_LARGE -> UiText.Res(R.string.import_error_amount_too_large, line)
            BudgetImportError.BAD_DATE -> UiText.Res(R.string.import_error_bad_date, line)
            BudgetImportError.UNCLOSED_QUOTE -> UiText.Res(R.string.import_error_unclosed_quote, line)
        }
    }

    fun consumeImportMessage() {
        _importState.update { it.copy(message = null) }
    }

    fun leaveFamily() {
        if (_leaveState.value.inProgress) return
        viewModelScope.launch {
            _leaveState.value = LeaveFamilyUiState(inProgress = true)
            try {
                withContext(Dispatchers.IO) { familyRepository.leaveFamily() }
                selectedBalanceStore.clear()
            } catch (e: Exception) {
                _leaveState.value = LeaveFamilyUiState(failed = true)
            } finally {
                // Спиннер снимается и на успехе: иначе после возврата в семью
                // пункт меню навсегда блокируется вечным индикатором.
                _leaveState.update { it.copy(inProgress = false) }
            }
        }
    }

    fun consumeLeaveFamilyError() {
        _leaveState.update { it.copy(failed = false) }
    }

    fun loadMoreTransactions() {
        if (!uiState.value.hasMoreTransactions) return
        transactionLimit.update { it + TRANSACTION_PAGE_SIZE }
    }

    private companion object {
        const val TRANSACTION_PAGE_SIZE = 50
        const val DAY_MILLIS = 86_400_000L
        const val KEY_PERIOD_DAYS = "home_period_days"
    }
}
