package com.expeysi.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.FamilyRepository
import com.expeysi.app.data.FamilyStore
import com.expeysi.app.data.SelectedBalanceStore
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.data.importer.BudgetImportService
import com.expeysi.app.domain.BalanceCalculator
import com.expeysi.app.domain.Periods
import com.expeysi.app.domain.TxDayAmount
import com.expeysi.app.domain.TxSum
import com.expeysi.app.domain.TxType
import com.expeysi.app.domain.WindowSums
import com.expeysi.app.domain.Windows
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
)

data class ImportUiState(
    val inProgress: Boolean = false,
    val message: String? = null,
)

data class LeaveFamilyUiState(
    val inProgress: Boolean = false,
    val failed: Boolean = false,
)

data class HomeUiState(
    val balances: List<BalanceEntity> = emptyList(),
    val selectedBalance: BalanceEntity? = null,
    val currencySymbol: String = "",
    val accounts: List<AccountBalance> = emptyList(),
    val totalBalanceMinor: Long = 0,
    val expense: WindowSums = WindowSums(0, 0, 0),
    val income: WindowSums = WindowSums(0, 0, 0),
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
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()
    private val _leaveState = MutableStateFlow(LeaveFamilyUiState())
    val leaveState: StateFlow<LeaveFamilyUiState> = _leaveState.asStateFlow()

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
            val items = transactions.map { tx ->
                val category = categoryById[tx.categoryId]
                TransactionItem(
                    transaction = tx,
                    categoryName = category?.name ?: "Без категории",
                    categoryIconKey = category?.iconKey ?: "",
                    categoryColorKey = category?.colorKey ?: "default",
                    accountName = accountById[tx.accountId]?.name ?: "",
                    currencySymbol = symbol,
                )
            }
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
            expense = windowSums(TxType.EXPENSE),
            income = windowSums(TxType.INCOME),
            familyId = familyStore.familyId.value,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        summaryState,
        transactionFeed,
    ) { state, (transactions, hasMore) ->
        state.copy(transactions = transactions, hasMoreTransactions = hasMore)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectBalance(id: String) {
        selectedBalanceStore.select(id)
    }

    fun importBudget(input: InputStream?) {
        if (_importState.value.inProgress) return
        if (input == null) {
            _importState.update { it.copy(message = "Не удалось открыть файл") }
            return
        }
        viewModelScope.launch {
            _importState.update { it.copy(inProgress = true, message = null) }
            val message = try {
                val result = withContext(Dispatchers.IO) { budgetImportService.import(input) }
                "Импортировано операций: ${result.transactionCount}"
            } catch (e: Exception) {
                e.message ?: "Не удалось импортировать файл"
            } finally {
                _importState.update { it.copy(inProgress = false) }
            }
            _importState.update { it.copy(message = message) }
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
    }
}
