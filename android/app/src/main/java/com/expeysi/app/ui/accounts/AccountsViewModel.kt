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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AccountRow(
    val balance: AccountBalance,
)

/**
 * Группа счетов одного баланса: заголовок с иконкой и названием баланса,
 * под ним его счета. Безымянная группа — счета, оставшиеся без активного баланса.
 */
data class AccountGroup(
    val balanceId: String?,
    val balanceName: String,
    val iconKey: String,
    val colorKey: String,
    val rows: List<AccountRow>,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    currencyDao: CurrencyDao,
    balanceDao: BalanceDao,
) : ViewModel() {

    val groups: StateFlow<List<AccountGroup>> = combine(
        accountDao.observeActive(),
        transactionDao.observeActive(),
        currencyDao.observeAll(),
        balanceDao.observeActive(),
    ) { accounts, transactions, currencies, balances ->
        val symbolByCode = currencies.associate { it.code to it.symbol }
        val balanceById = balances.associateBy { it.id }
        val rowsByBalanceId = accounts
            .map { account ->
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
                )
            }
            .groupBy { it.balance.account.balanceId }
        balances.map { balance ->
            AccountGroup(
                balanceId = balance.id,
                balanceName = balance.name,
                iconKey = balance.iconKey,
                colorKey = balance.colorKey,
                rows = rowsByBalanceId[balance.id].orEmpty(),
            )
        } + rowsByBalanceId
            .filterKeys { id -> balances.none { it.id == id } }
            .map { (balanceId, rows) ->
                AccountGroup(balanceId = balanceId, balanceName = "", iconKey = "", colorKey = "default", rows = rows)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
