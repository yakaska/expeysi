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
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.data.importer.BudgetImportError
import com.expeysi.app.data.importer.BudgetImportException
import com.expeysi.app.data.importer.BudgetImportService
import com.expeysi.app.domain.BalanceCalculator
import com.expeysi.app.domain.Periods
import com.expeysi.app.domain.Transfers
import com.expeysi.app.domain.TxDayAmount
import com.expeysi.app.domain.TxSum
import com.expeysi.app.domain.TxType
import com.expeysi.app.domain.WindowSums
import com.expeysi.app.domain.Windows
import com.expeysi.app.ui.common.UiText
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
    /** Нога перевода: рисуется иконкой перевода со подписью «откуда → куда». */
    val isTransfer: Boolean = false,
    val counterpartAccountName: String = "",
    /**
     * Строка свёрнутой пары, обе ноги которой видны в списке: рисуется нейтрально,
     * без знака. Одиночная нога показывает движение денег счёта: плюс или минус.
     */
    val isTransferPair: Boolean = false,
)

data class ImportUiState(
    val inProgress: Boolean = false,
    val message: UiText? = null,
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
    currencyDao: CurrencyDao,
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

    private val transactionFeed = feedAccountIds.flatMapLatest { accountIds ->
        combine(
            transactionLimit.flatMapLatest { limit ->
                transactionDao.observeActiveForAccounts(accountIds, limit).map { it to limit }
            },
            accountDao.observeAll(),
            categoryDao.observeAll(),
            currencyDao.observeAll(),
            selection,
        ) { (transactions, limit), accounts, categories, currencies, (_, selected) ->
            val symbolByCode = currencies.associate { it.code to it.symbol }
            val symbol = selected?.let { symbolByCode[it.currencyCode] ?: it.currencyCode } ?: ""
            val categoryById = categories.associateBy { it.id }
            val accountById = accounts.associateBy { it.id }
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
                TransactionItem(
                    transaction = tx,
                    categoryName = category?.name ?: "",
                    categoryIconKey = category?.iconKey ?: "",
                    categoryColorKey = category?.colorKey ?: "default",
                    accountName = accountById[tx.accountId]?.name ?: "",
                    currencySymbol = symbol,
                    isTransfer = TxType.fromWire(tx.type).isTransfer,
                    counterpartAccountName = accountById[tx.counterpartyAccountId]?.name ?: "",
                    isTransferPair = Transfers.isCollapsedPair(tx, page),
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
    ) { (balances, selected), accounts, transactions, currencies ->
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
        fun windowSums(type: TxType) = Windows.sums(
            balanceTxs
                .filter { TxType.fromWire(it.type) == type }
                .map {
                    val txDay = Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
                    TxDayAmount(it.amountMinor, Periods.day(txDay, zone).startMillis)
                },
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
            expense = windowSums(TxType.EXPENSE),
            income = windowSums(TxType.INCOME),
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

    fun importBudget(input: InputStream?) {
        if (_importState.value.inProgress) return
        if (input == null) {
            _importState.update { it.copy(message = UiText.Res(R.string.import_error_open)) }
            return
        }
        viewModelScope.launch {
            _importState.update { it.copy(inProgress = true, message = null) }
            val message = try {
                val result = withContext(Dispatchers.IO) { budgetImportService.import(input) }
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
