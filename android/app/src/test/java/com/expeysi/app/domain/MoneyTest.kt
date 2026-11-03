package com.expeysi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `парсинг рублей и копеек`() {
        assertEquals(12_345L, Money.parseToMinor("123,45"))
        assertEquals(12_345L, Money.parseToMinor("123.45"))
        assertEquals(12_340L, Money.parseToMinor("123.4"))
        assertEquals(100_000L, Money.parseToMinor("1000"))
        assertEquals(5L, Money.parseToMinor("0,05"))
    }

    @Test
    fun `парсинг мусора даёт null`() {
        assertNull(Money.parseToMinor(""))
        assertNull(Money.parseToMinor("abc"))
        assertNull(Money.parseToMinor("1.2.3"))
        assertNull(Money.parseToMinor("12,345"))
        assertNull(Money.parseToMinor(","))
    }

    @Test
    fun `форматирование с группировкой и копейками`() {
        assertEquals("0,00", Money.format(0))
        assertEquals("1,05", Money.format(105))
        assertEquals("1 234,56", Money.format(123_456))
        assertEquals("-50,00", Money.format(-5_000))
        assertEquals("12 345 678,90", Money.format(1_234_567_890L))
    }

    @Test
    fun `парсинг и форматирование согласованы`() {
        assertEquals(123_456L, Money.parseToMinor(Money.format(123_456)))
    }
}
