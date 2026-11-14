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

    @Test
    fun `ноги перевода двигают остатки счетов в разные стороны`() {
        val txs = listOf(
            TxSum(TxType.TRANSFER_OUT, 300_00L),
            TxSum(TxType.TRANSFER_IN, 300_00L),
        )
        assertEquals(100_00L, BalanceCalculator.balanceMinor(400_00L, txs.filter { it.type == TxType.TRANSFER_OUT }))
        assertEquals(300_00L, BalanceCalculator.balanceMinor(0L, txs.filter { it.type == TxType.TRANSFER_IN }))
    }

    @Test
    fun `перевод внутри одного баланса сокращается в ноль по итогу баланса`() {
        val txs = listOf(
            TxSum(TxType.TRANSFER_OUT, 300_00L),
            TxSum(TxType.TRANSFER_IN, 300_00L),
        )
        assertEquals(1_000_00L, BalanceCalculator.balanceMinor(1_000_00L, txs))
    }

    @Test
    fun `ноги перевода не попадают в итоги доходов и расходов`() {
        val txs = listOf(
            TxSum(TxType.TRANSFER_OUT, 300_00L),
            TxSum(TxType.TRANSFER_IN, 250_00L),
        )
        assertEquals(0L, BalanceCalculator.totalMinor(TxType.INCOME, txs))
        assertEquals(0L, BalanceCalculator.totalMinor(TxType.EXPENSE, txs))
    }
}
