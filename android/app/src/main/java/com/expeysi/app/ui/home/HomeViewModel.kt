package com.expeysi.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.BalanceCalculator
import com.expeysi.app.domain.TxSum
import com.expeysi.app.domain.TxType
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val accountName: String,
    val currencySymbol: String,
)

data class HomeUiState(
    val accounts: List<AccountBalance> = emptyList(),
    val recent: List<TransactionItem> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    currencyDao: CurrencyDao,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        accountDao.observeActive(),
        transactionDao.observeActive(),
        categoryDao.observeAll(),
        accountDao.observeAll(),
        currencyDao.observeAll(),
    ) { accounts, transactions, categories, allAccounts, currencies ->
        val categoryById = categories.associateBy { it.id }
        val accountById = allAccounts.associateBy { it.id }
        val symbolByCode = currencies.associate { it.code to it.symbol }

        val balances = accounts.map { account ->
            val sums = transactions
                .filter { it.accountId == account.id }
                .map { TxSum(TxType.fromWire(it.type), it.amountMinor) }
            AccountBalance(
                account = account,
                balanceMinor = BalanceCalculator.balanceMinor(account.openingBalanceMinor, sums),
                currencySymbol = symbolByCode[account.currencyCode] ?: account.currencyCode,
            )
        }

        val recent = transactions.take(50).map { tx ->
            val category = categoryById[tx.categoryId]
            val account = accountById[tx.accountId]
            TransactionItem(
                transaction = tx,
                categoryName = category?.name ?: "Без категории",
                categoryIconKey = category?.iconKey ?: "",
                accountName = account?.name ?: "",
                currencySymbol = symbolByCode[account?.currencyCode] ?: account?.currencyCode ?: "",
            )
        }

        HomeUiState(accounts = balances, recent = recent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
