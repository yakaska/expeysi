package com.expeysi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceCalculatorTest {

    @Test
    fun `пустая история даёт начальный остаток`() {
        assertEquals(150_00L, BalanceCalculator.balanceMinor(150_00L, emptyList()))
    }

    @Test
    fun `баланс это остаток плюс доходы минус расходы`() {
        val txs = listOf(
            TxSum(TxType.INCOME, 10_000_00L),
            TxSum(TxType.INCOME, 500_00L),
            TxSum(TxType.EXPENSE, 299_99L),
        )
        assertEquals(10_200_01L, BalanceCalculator.balanceMinor(0L, txs))
    }

    @Test
    fun `расходы могут уводить баланс в минус`() {
        val txs = listOf(TxSum(TxType.EXPENSE, 1_000_00L))
        assertEquals(-500_00L, BalanceCalculator.balanceMinor(500_00L, txs))
    }

    @Test
    fun `итог по типу считает только свои операции`() {
        val txs = listOf(
            TxSum(TxType.INCOME, 100_00L),
            TxSum(TxType.EXPENSE, 40_00L),
            TxSum(TxType.EXPENSE, 60_00L),
        )
        assertEquals(100_00L, BalanceCalculator.totalMinor(TxType.INCOME, txs))
        assertEquals(100_00L, BalanceCalculator.totalMinor(TxType.EXPENSE, txs))
    }
}
