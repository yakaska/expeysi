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
    fun `ввод форматируется с группировкой и запятой`() {
        assertEquals("1 234 567,89", Money.formatInput("1234567.89"))
        assertEquals("1 234 567,89", Money.formatInput("1 234 567,89"))
        assertEquals("10 000,", Money.formatInput("10000."))
        assertEquals("-1 234,5", Money.formatInput("-1234,5"))
        assertEquals("", Money.formatInput(""))
    }

    @Test
    fun `некорректный ввод не перезаписывается форматированием`() {
        assertEquals("1.2.3", Money.formatInput("1.2.3"))
        assertEquals("abc", Money.formatInput("abc"))
    }

    @Test
    fun `вставленная сумма поддерживает разные пробелы`() {
        assertEquals(123_456L, Money.parseToMinor("1\u00A0234,56"))
        assertEquals(123_456L, Money.parseToMinor("\n1\u202F234,56 "))
        assertEquals("1 234,56", Money.formatInput("1\u00A0234,56"))
        assertEquals("1 234,56", Money.formatInput("1\u202F234,56"))
    }

    @Test
    fun `парсинг и форматирование согласованы`() {
        assertEquals(123_456L, Money.parseToMinor(Money.format(123_456)))
    }
}
