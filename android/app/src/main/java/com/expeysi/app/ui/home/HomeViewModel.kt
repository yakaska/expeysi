package com.expeysi.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.CategoryDao
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
)

data class TransactionItem(
    val transaction: TransactionEntity,
    val categoryName: String,
    val categoryIconKey: String,
    val accountName: String,
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
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        accountDao.observeActive(),
        transactionDao.observeActive(),
        categoryDao.observeAll(),
    ) { accounts, transactions, categories ->
        val categoryById = categories.associateBy { it.id }
        val accountById = accounts.associateBy { it.id }

        val balances = accounts.map { account ->
            val sums = transactions
                .filter { it.accountId == account.id }
                .map { TxSum(TxType.fromWire(it.type), it.amountMinor) }
            AccountBalance(account, BalanceCalculator.balanceMinor(account.openingBalanceMinor, sums))
        }

        val recent = transactions.take(50).map { tx ->
            val category = categoryById[tx.categoryId]
            TransactionItem(
                transaction = tx,
                categoryName = category?.name ?: "Без категории",
                categoryIconKey = category?.iconKey ?: "",
                accountName = accountById[tx.accountId]?.name ?: "",
            )
        }

        HomeUiState(accounts = balances, recent = recent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
