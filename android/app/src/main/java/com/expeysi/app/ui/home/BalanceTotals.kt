package com.expeysi.app.ui.home

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.BalanceCalculator
import com.expeysi.app.domain.TxSum
import com.expeysi.app.domain.TxType

/** Код валюты, показываемый в чипе баланса: пусто, если название уже его содержит. */
internal fun chipCurrencyCode(name: String, currencyCode: String): String = if (currencyCode.isBlank() || name.contains(currencyCode, ignoreCase = true)) "" else currencyCode

/**
 * Итог по счетам каждого баланса: начальные остатки счетов + операции.
 * Удалённые счета и операции не учитываются, балансы без счетов дают 0.
 */
internal fun balanceTotals(
    balances: List<BalanceEntity>,
    accounts: List<AccountEntity>,
    transactions: List<TransactionEntity>,
): Map<String, Long> {
    val activeAccounts = accounts.filterNot { it.deleted }
    val txsByAccount = transactions
        .filterNot { it.deleted }
        .groupBy { it.accountId }
    return balances.associate { balance ->
        val total = activeAccounts
            .filter { it.balanceId == balance.id }
            .sumOf { account ->
                val sums = (txsByAccount[account.id] ?: emptyList())
                    .map { TxSum(TxType.fromWire(it.type), it.amountMinor) }
                BalanceCalculator.balanceMinor(account.openingBalanceMinor, sums)
            }
        balance.id to total
    }
}
