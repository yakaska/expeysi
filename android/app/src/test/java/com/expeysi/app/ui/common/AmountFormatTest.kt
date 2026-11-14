package com.expeysi.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountFormatTest {

    private val nbsp = "\u00A0"

    @Test
    fun `сумма без дробной части группируется неразрывным пробелом`() {
        assertEquals("178${nbsp}449", AmountFormat.amount(1_784_490_000L))
        assertEquals("1${nbsp}234", AmountFormat.amount(12_340_000L))
    }

    @Test
    fun `ноль без дробной части`() {
        assertEquals("0", AmountFormat.amount(0L))
    }

    @Test
    fun `дробная часть выводится после запятой`() {
        assertEquals("1${nbsp}234,56", AmountFormat.amount(12_345_600L))
        assertEquals("0,05", AmountFormat.amount(500L))
    }

    @Test
    fun `отрицательная сумма со знаком`() {
        assertEquals("-1${nbsp}234,56", AmountFormat.amount(-12_345_600L))
        assertEquals("-178${nbsp}449", AmountFormat.amount(-1_784_490_000L))
    }

    @Test
    fun `с валютой`() {
        assertEquals("178${nbsp}449$nbsp₽", AmountFormat.withCurrency(1_784_490_000L, "₽"))
    }

    @Test
    fun `курс один к одному и с дробной частью`() {
        assertEquals("1", AmountFormat.rate(1_000L, 1_000L))
        assertEquals("0,25", AmountFormat.rate(250L, 1_000L))
        assertEquals("1,25", AmountFormat.rate(1_250L, 1_000L))
        assertEquals("26,3", AmountFormat.rate(26_300L, 1_000L))
    }

    @Test
    fun `крупный курс целым числом с группировкой`() {
        assertEquals("26${nbsp}300", AmountFormat.rate(2_630_000L, 100L))
        assertEquals("1${nbsp}000", AmountFormat.rate(1_000_00L, 100L))
        assertEquals("2,5", AmountFormat.rate(5_000L, 2_000L))
    }

    @Test
    fun `малый курс тремя значащими цифрами`() {
        assertEquals("0,0512", AmountFormat.rate(512L, 10_000L))
        assertEquals("0,000034", AmountFormat.rate(34L, 1_000_000L))
    }

    @Test
    fun `нулевые хвосты дробной части срезаются`() {
        assertEquals("1,5", AmountFormat.rate(1_500L, 1_000L))
        assertEquals("0,2", AmountFormat.rate(200L, 1_000L))
    }
}
