package com.expeysi.app.domain

data class TxSum(val type: TxType, val amountMinor: Long)

object BalanceCalculator {

    /**
     * Ноги перевода двигают деньги между счетами: списание — уменьшает остаток
     * источника, зачисление — увеличивает остаток получателя. Долговые операции
     * ([TxType.isDebt]) двигают деньги по счёту так же: выданный долг и
     * отправленное погашение снижают остаток, взятый долг и полученное
     * погашение — поднимают. В пределах одного баланса обе ноги сокращаются до
     * нуля по итогу баланса; в разных балансах каждая нога учитывается в
     * валюте своего счёта.
     */
    fun balanceMinor(openingBalanceMinor: Long, transactions: List<TxSum>): Long = openingBalanceMinor + transactions.sumOf { tx ->
        when (tx.type) {
            TxType.INCOME, TxType.TRANSFER_IN, TxType.DEBT_BORROW, TxType.DEBT_REPAY_IN -> tx.amountMinor
            TxType.EXPENSE, TxType.TRANSFER_OUT, TxType.DEBT_LEND, TxType.DEBT_REPAY_OUT -> -tx.amountMinor
        }
    }

    /** Доходы и расходы в сводках; ноги переводов и долги в эти итоги не попадают. */
    fun totalMinor(type: TxType, transactions: List<TxSum>): Long = transactions.filter { it.type == type }.sumOf { it.amountMinor }
}
