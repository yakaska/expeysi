package com.expeysi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowsTest {

    private val day = 86_400_000L
    private val today = 1_000_000_000L

    @Test
    fun `сегодня это только операции сегодняшнего дня`() {
        val txs = listOf(
            TxDayAmount(100, today),
            TxDayAmount(200, today - day),
        )
        assertEquals(100, Windows.sums(txs, today).dayMinor)
    }

    @Test
    fun `неделя это сегодня плюс шесть дней назад`() {
        val txs = listOf(
            TxDayAmount(1, today),
            TxDayAmount(1, today - 6 * day),
            TxDayAmount(1, today - 7 * day),
        )
        assertEquals(2, Windows.sums(txs, today).weekMinor)
    }

    @Test
    fun `месяц это сегодня плюс двадцать девять дней назад`() {
        val txs = listOf(
            TxDayAmount(1, today),
            TxDayAmount(1, today - 29 * day),
            TxDayAmount(1, today - 30 * day),
        )
        assertEquals(2, Windows.sums(txs, today).monthMinor)
    }

    @Test
    fun `пустая история даёт нули`() {
        assertEquals(WindowSums(0, 0, 0), Windows.sums(emptyList(), today))
    }
}
