package com.expeysi.app.ui.categoryreport

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
import com.expeysi.app.domain.DateRangeFilter
import com.expeysi.app.domain.HistoryPeriod
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

data class CategoryReportUiState(
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryIds: Set<String> = emptySet(),
    val items: List<TransactionItem> = emptyList(),
    val balanceMinor: Long = 0,
    val currencyCode: String = "",
    val currencySymbol: String = "",
)

private data class ReportData(
    val balance: BalanceEntity?,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
)

private data class ReportFilters(
    val period: HistoryPeriod,
    val customRange: DateRangeFilter?,
    val categoryIds: Set<String>,
)

@HiltViewModel
class CategoryReportViewModel @Inject constructor(
    balanceDao: BalanceDao,
    accountDao: AccountDao,
    categoryDao: CategoryDao,
    currencyDao: CurrencyDao,
    transactionDao: TransactionDao,
    selectedBalanceStore: SelectedBalanceStore,
) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()

    private val _period = MutableStateFlow(HistoryPeriod.MONTH)
    val period: StateFlow<HistoryPeriod> = _period.asStateFlow()

    private val _customRange = MutableStateFlow<DateRangeFilter?>(null)
    val customRange: StateFlow<DateRangeFilter?> = _customRange.asStateFlow()

    private val _selectedCategoryIds = MutableStateFlow(emptySet<String>())
    val selectedCategoryIds: StateFlow<Set<String>> = _selectedCategoryIds.asStateFlow()

    private val selection = combine(
        balanceDao.observeActive(),
        selectedBalanceStore.selectedId,
    ) { balances, selectedId ->
        balances.firstOrNull { it.id == selectedId } ?: balances.firstOrNull()
    }

    private val data = combine(
        selection,
        accountDao.observeActive(),
        categoryDao.observeAll(),
        transactionDao.observeActive(),
    ) { balance, accounts, categories, transactions ->
        ReportData(
            balance = balance,
            accounts = accounts,
            categories = categories.filter { !it.deleted },
            transactions = transactions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportData(null, emptyList(), emptyList(), emptyList()))

    private val currencies = currencyDao.observeAll()
        .map { list -> list.associate { it.code to it.symbol } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val filters = combine(
        _period,
        _customRange,
        _selectedCategoryIds,
    ) { period, range, categoryIds ->
        ReportFilters(period, range, categoryIds)
    }

    val uiState: StateFlow<CategoryReportUiState> = combine(
        data,
        currencies,
        filters,
    ) { report, symbols, currentFilters ->
        val accountIds = report.accounts
            .filter { it.balanceId == report.balance?.id }
            .map { it.id }
            .toSet()
        val accountById = report.accounts.associateBy { it.id }
        val categoryById = report.categories.associateBy { it.id }
        val symbol = report.balance?.let { symbols[it.currencyCode] ?: it.currencyCode } ?: ""
        val periodStart = currentFilters.period.startMillis(ZonedDateTime.now(zone))

        val items = filterCategoryTransactions(
            transactions = report.transactions,
            accountIds = accountIds,
            categoryIds = currentFilters.categoryIds,
            periodStartMillis = periodStart,
            customRange = currentFilters.customRange,
        ).map { transaction ->
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

        CategoryReportUiState(
            categories = report.categories.sortedWith(compareBy({ it.type }, { it.name })),
            selectedCategoryIds = currentFilters.categoryIds,
            items = items,
            balanceMinor = categoryReportBalanceMinor(items.map { it.transaction }),
            currencyCode = report.balance?.currencyCode ?: "",
            currencySymbol = symbol,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryReportUiState())

    fun toggleCategory(id: String) {
        _selectedCategoryIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun selectPeriod(value: HistoryPeriod) {
        _period.value = value
        _customRange.value = null
    }

    fun selectRange(start: LocalDate, endInclusive: LocalDate) {
        _customRange.value = DateRangeFilter.ofDays(start, endInclusive, zone)
    }
}
