package com.expeysi.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class HistoryViewMode { LIST, CATEGORIES, ACCOUNTS }

data class HistoryDetail(val categoryId: String, val accountId: String? = null)

data class HistoryUiState(
    val title: String = "",
    val isAccountMode: Boolean = false,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val items: List<TransactionItem> = emptyList(),
    val mode: HistoryViewMode = HistoryViewMode.LIST,
    val detail: HistoryDetail? = null,
    val detailTitle: String = "",
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val accountTotals: List<AccountTotal> = emptyList(),
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
    val accountIds: Set<String>?,
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
        savedStateHandle.get<String>("type")?.let { setOf(TxType.fromWire(it)) } ?: TxType.entries.toSet(),
    )
    val selectedTypes: StateFlow<Set<TxType>> = _selectedTypes.asStateFlow()

    /** null = выбраны все. */
    private val _selectedAccountIds = MutableStateFlow<Set<String>?>(null)
    val selectedAccountIds: StateFlow<Set<String>?> = _selectedAccountIds.asStateFlow()

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
        _period, _customRange, _selectedTypes, _selectedAccountIds, _selectedCategoryIds,
    ) { period, range, types, accountIds, categoryIds ->
        Filters(period, range, types, accountIds, categoryIds)
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        base, currencies, filters, _mode, _detail,
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
            scopeAccounts.firstOrNull()?.name ?: "Счёт"
        } else {
            balance?.name ?: "Баланс"
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
            .filter { mode == HistoryViewMode.ACCOUNTS || f.accountIds == null || it.accountId in f.accountIds }
            .filter { f.categoryIds == null || it.categoryId in f.categoryIds }

        val entries = filtered.map {
            AggEntry(it.categoryId, it.accountId, TxType.fromWire(it.type), it.amountMinor)
        }

        val items = filtered
            .filter { detail == null || it.categoryId == detail.categoryId }
            .filter { detail?.accountId == null || it.accountId == detail?.accountId }
            .map { tx ->
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

        HistoryUiState(
            title = title,
            isAccountMode = isAccountMode,
            accounts = if (isAccountMode) emptyList() else scopeAccounts,
            categories = data.categories,
            items = items,
            mode = mode,
            detail = detail,
            detailTitle = detail?.let { d ->
                val categoryName = categoryById[d.categoryId]?.name ?: "Без категории"
                val accountName = d.accountId?.let { accountById[it]?.name }
                listOfNotNull(categoryName, accountName).joinToString(" · ")
            } ?: "",
            categoryTotals = Aggregates.byCategory(entries),
            accountTotals = Aggregates.byAccount(entries),
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

    fun toggleAccount(id: String) {
        val allIds = uiState.value.accounts.map { it.id }.toSet()
        _selectedAccountIds.update { current ->
            val effective = current ?: allIds
            val updated = if (id in effective) effective - id else effective + id
            if (updated == allIds) null else updated
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
