package com.expeysi.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.BalanceDao
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

data class AccountRow(
    val balance: AccountBalance,
    val balanceName: String,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    currencyDao: CurrencyDao,
    balanceDao: BalanceDao,
) : ViewModel() {

    val accounts: StateFlow<List<AccountRow>> = combine(
        accountDao.observeActive(),
        transactionDao.observeActive(),
        currencyDao.observeAll(),
        balanceDao.observeAll(),
    ) { accounts, transactions, currencies, balances ->
        val symbolByCode = currencies.associate { it.code to it.symbol }
        val balanceById = balances.associateBy { it.id }
        accounts.map { account ->
            val sums = transactions
                .filter { it.accountId == account.id }
                .map { TxSum(TxType.fromWire(it.type), it.amountMinor) }
            val balance = balanceById[account.balanceId]
            val symbol = balance?.let { symbolByCode[it.currencyCode] ?: it.currencyCode } ?: ""
            AccountRow(
                balance = AccountBalance(
                    account = account,
                    balanceMinor = BalanceCalculator.balanceMinor(account.openingBalanceMinor, sums),
                    currencySymbol = symbol,
                ),
                balanceName = balance?.name ?: "",
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
