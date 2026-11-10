package com.expeysi.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountFormatTest {

    private val nbsp = "\u00A0"

    @Test
    fun `сумма без копеек группируется неразрывным пробелом`() {
        assertEquals("178${nbsp}449", AmountFormat.amount(17_844_900L))
        assertEquals("1${nbsp}234", AmountFormat.amount(123_400L))
    }

    @Test
    fun `ноль без копеек`() {
        assertEquals("0", AmountFormat.amount(0L))
    }

    @Test
    fun `копейки выводятся после запятой`() {
        assertEquals("1${nbsp}234,56", AmountFormat.amount(123_456L))
        assertEquals("0,05", AmountFormat.amount(5L))
    }

    @Test
    fun `отрицательная сумма со знаком`() {
        assertEquals("-1${nbsp}234,56", AmountFormat.amount(-123_456L))
        assertEquals("-178${nbsp}449", AmountFormat.amount(-17_844_900L))
    }

    @Test
    fun `с валютой`() {
        assertEquals("178${nbsp}449$nbsp₽", AmountFormat.withCurrency(17_844_900L, "₽"))
    }
}
