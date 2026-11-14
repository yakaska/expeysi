package com.expeysi.app.domain

data class TxSum(val type: TxType, val amountMinor: Long)

object BalanceCalculator {

    /**
     * Ноги перевода двигают деньги между счетами: списание — уменьшает остаток
     * источника, зачисление — увеличивает остаток получателя. В пределах одного
     * баланса обе ноги сокращаются до нуля по итогу баланса; в разных балансах
     * каждая нога учитывается в валюте своего счёта.
     */
    fun balanceMinor(openingBalanceMinor: Long, transactions: List<TxSum>): Long = openingBalanceMinor + transactions.sumOf { tx ->
        when (tx.type) {
            TxType.INCOME, TxType.TRANSFER_IN -> tx.amountMinor
            TxType.EXPENSE, TxType.TRANSFER_OUT -> -tx.amountMinor
        }
    }

    /** Доходы и расходы в сводках; ноги перевода в эти итоги не попадают. */
    fun totalMinor(type: TxType, transactions: List<TxSum>): Long = transactions.filter { it.type == type }.sumOf { it.amountMinor }
}
