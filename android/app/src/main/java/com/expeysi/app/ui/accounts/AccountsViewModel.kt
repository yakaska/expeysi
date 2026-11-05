package com.expeysi.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.CurrencyDao
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
    currencyDao: CurrencyDao,
) : ViewModel() {

    val accounts: StateFlow<List<AccountBalance>> = combine(
        accountDao.observeActive(),
        transactionDao.observeActive(),
        currencyDao.observeAll(),
    ) { accounts, transactions, currencies ->
        val symbolByCode = currencies.associate { it.code to it.symbol }
        accounts.map { account ->
            val sums = transactions
                .filter { it.accountId == account.id }
                .map { TxSum(TxType.fromWire(it.type), it.amountMinor) }
            AccountBalance(
                account = account,
                balanceMinor = BalanceCalculator.balanceMinor(account.openingBalanceMinor, sums),
                currencySymbol = symbolByCode[account.currencyCode] ?: account.currencyCode,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
