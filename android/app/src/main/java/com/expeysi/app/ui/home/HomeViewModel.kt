package com.expeysi.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.SelectedBalanceStore
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.BalanceCalculator
import com.expeysi.app.domain.Periods
import com.expeysi.app.domain.TxDayAmount
import com.expeysi.app.domain.TxSum
import com.expeysi.app.domain.TxType
import com.expeysi.app.domain.WindowSums
import com.expeysi.app.domain.Windows
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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

data class HomeUiState(
    val balances: List<BalanceEntity> = emptyList(),
    val selectedBalance: BalanceEntity? = null,
    val currencySymbol: String = "",
    val accounts: List<AccountBalance> = emptyList(),
    val expense: WindowSums = WindowSums(0, 0, 0),
    val income: WindowSums = WindowSums(0, 0, 0),
    val recent: List<TransactionItem> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    balanceDao: BalanceDao,
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    currencyDao: CurrencyDao,
    private val selectedBalanceStore: SelectedBalanceStore,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val selection = combine(
        balanceDao.observeActive(),
        selectedBalanceStore.selectedId,
    ) { balances, selectedId ->
        balances to (balances.firstOrNull { it.id == selectedId } ?: balances.firstOrNull())
    }

    val uiState: StateFlow<HomeUiState> = combine(
        selection,
        accountDao.observeAll(),
        transactionDao.observeActive(),
        categoryDao.observeAll(),
        currencyDao.observeAll(),
    ) { (balances, selected), accounts, transactions, categories, currencies ->
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

        val categoryById = categories.associateBy { it.id }
        val accountById = accounts.associateBy { it.id }
        val recent = balanceTxs.take(50).map { tx ->
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

        HomeUiState(
            balances = balances,
            selectedBalance = selected,
            currencySymbol = symbol,
            accounts = accountBalances,
            expense = windowSums(TxType.EXPENSE),
            income = windowSums(TxType.INCOME),
            recent = recent,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectBalance(id: String) {
        selectedBalanceStore.select(id)
    }
}
