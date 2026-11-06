package com.expeysi.app.domain

data class TxSum(val type: TxType, val amountMinor: Long)

object BalanceCalculator {

    fun balanceMinor(openingBalanceMinor: Long, transactions: List<TxSum>): Long = openingBalanceMinor + transactions.sumOf { tx ->
        when (tx.type) {
            TxType.INCOME -> tx.amountMinor
            TxType.EXPENSE -> -tx.amountMinor
        }
    }

    fun totalMinor(type: TxType, transactions: List<TxSum>): Long = transactions.filter { it.type == type }.sumOf { it.amountMinor }
}
