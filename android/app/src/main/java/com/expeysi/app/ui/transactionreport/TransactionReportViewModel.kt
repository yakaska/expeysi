package com.expeysi.app.ui.transactionreport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.SelectedBalanceStore
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
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.home.TransactionItem
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

enum class TransactionReportMode { OPERATIONS, CATEGORIES, ACCOUNTS }

data class TransactionReportUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val accountTotals: List<AccountTotal> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val items: List<TransactionItem> = emptyList(),
    val selectedAccountIds: Set<String>? = null,
    val selectedAccountId: String? = null,
    val selectedCategoryId: String? = null,
    val totalMinor: Long = 0,
    val currencySymbol: String = "",
)

private data class TransactionReportData(
    val balance: BalanceEntity?,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
)

private data class TransactionReportFilters(
    val period: HistoryPeriod,
    val customRange: DateRangeFilter?,
    val accountIds: Set<String>?,
)

@HiltViewModel
class TransactionReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    balanceDao: BalanceDao,
    accountDao: AccountDao,
    categoryDao: CategoryDao,
    currencyDao: CurrencyDao,
    transactionDao: TransactionDao,
    selectedBalanceStore: SelectedBalanceStore,
) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val type = TxType.fromWire(checkNotNull(savedStateHandle["type"]))
    val isIncome = type == TxType.INCOME

    private val _period = MutableStateFlow(HistoryPeriod.MONTH)
    val period: StateFlow<HistoryPeriod> = _period.asStateFlow()

    private val _customRange = MutableStateFlow<DateRangeFilter?>(null)
    val customRange: StateFlow<DateRangeFilter?> = _customRange.asStateFlow()

    private val _mode = MutableStateFlow(TransactionReportMode.OPERATIONS)
    val mode: StateFlow<TransactionReportMode> = _mode.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    private val _selectedAccountId = MutableStateFlow<String?>(null)
    private val _selectedAccountIds = MutableStateFlow<Set<String>?>(null)

    private val selection = combine(
        balanceDao.observeActive(),
        selectedBalanceStore.selectedId,
    ) { balances, selectedId ->
        balances.firstOrNull { it.id == selectedId } ?: balances.firstOrNull()
    }

    private val data = combine(
        selection,
        accountDao.observeActive(),
        categoryDao.observeActive(type.wire),
        transactionDao.observeActive(),
    ) { balance, accounts, categories, transactions ->
        TransactionReportData(balance, accounts, categories, transactions)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TransactionReportData(null, emptyList(), emptyList(), emptyList()),
    )

    private val currencies = currencyDao.observeAll()
        .map { list -> list.associate { it.code to it.symbol } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val filters = combine(
        _period,
        _customRange,
        _selectedAccountIds,
        ::TransactionReportFilters,
    )

    val uiState: StateFlow<TransactionReportUiState> = combine(
        data,
        currencies,
        filters,
        _selectedCategoryId,
        _selectedAccountId,
    ) { report, symbols, currentFilters, selectedCategoryId, selectedAccountId ->
        val scopeAccounts = report.accounts.filter { it.balanceId == report.balance?.id }
        val accountIds = scopeAccounts.map { it.id }.toSet()
        val accountById = report.accounts.associateBy { it.id }
        val categoryById = report.categories.associateBy { it.id }
        val symbol = report.balance?.let { symbols[it.currencyCode] ?: it.currencyCode } ?: ""
        val periodStart = currentFilters.period.startMillis(ZonedDateTime.now(zone))

        val transactions = report.transactions
            .filter { it.accountId in accountIds }
            .filter { currentFilters.accountIds == null || it.accountId in currentFilters.accountIds }
            .filter { TxType.fromWire(it.type) == type }
            .filter { transaction ->
                val range = currentFilters.customRange
                when {
                    range != null -> transaction.date >= range.startMillis && transaction.date < range.endMillisExclusive
                    periodStart != null -> transaction.date >= periodStart
                    else -> true
                }
            }
        val items = transactions
            .filter { selectedCategoryId == null || it.categoryId == selectedCategoryId }
            .filter { selectedAccountId == null || it.accountId == selectedAccountId }
            .map { transaction ->
                val category = categoryById[transaction.categoryId]
                TransactionItem(
                    transaction = transaction,
                    categoryName = category?.name ?: "",
                    categoryIconKey = category?.iconKey ?: "",
                    categoryColorKey = category?.colorKey ?: "default",
                    accountName = accountById[transaction.accountId]?.name ?: "",
                    currencySymbol = symbol,
                )
            }
        val entries = transactions.map { AggEntry(it.categoryId, it.accountId, type, it.amountMinor) }

        TransactionReportUiState(
            accounts = scopeAccounts,
            accountTotals = Aggregates.byAccount(entries),
            categories = report.categories,
            categoryTotals = Aggregates.byCategory(entries),
            items = items,
            selectedAccountIds = currentFilters.accountIds,
            selectedAccountId = selectedAccountId,
            selectedCategoryId = selectedCategoryId,
            totalMinor = transactions.sumOf { it.amountMinor },
            currencySymbol = symbol,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionReportUiState())

    fun toggleAccount(id: String) {
        val allIds = uiState.value.accounts.map { it.id }.toSet()
        _selectedAccountIds.update { current ->
            val effective = current ?: allIds
            val updated = if (id in effective) effective - id else effective + id
            if (updated == allIds) null else updated
        }
        closeCategory()
    }

    fun selectMode(value: TransactionReportMode) {
        _mode.value = value
        closeCategory()
    }

    fun selectPeriod(value: HistoryPeriod) {
        _period.value = value
        _customRange.value = null
        closeCategory()
    }

    fun selectRange(start: LocalDate, endInclusive: LocalDate) {
        _customRange.value = DateRangeFilter.ofDays(start, endInclusive, zone)
        closeCategory()
    }

    fun openCategory(categoryId: String, accountId: String? = null) {
        _selectedCategoryId.value = categoryId
        _selectedAccountId.value = accountId
    }

    fun closeCategory() {
        _selectedCategoryId.value = null
        _selectedAccountId.value = null
    }
}
