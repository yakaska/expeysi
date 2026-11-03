package com.expeysi.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.domain.BalanceCalculator
import com.expeysi.app.domain.TxSum
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.home.AccountBalance
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AccountsViewModel @Inject constructor(
    accountDao: AccountDao,
    transactionDao: TransactionDao,
) : ViewModel() {

    val accounts: StateFlow<List<AccountBalance>> = combine(
        accountDao.observeActive(),
        transactionDao.observeActive(),
    ) { accounts, transactions ->
        accounts.map { account ->
            val sums = transactions
                .filter { it.accountId == account.id }
                .map { TxSum(TxType.fromWire(it.type), it.amountMinor) }
            AccountBalance(account, BalanceCalculator.balanceMinor(account.openingBalanceMinor, sums))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
