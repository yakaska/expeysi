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
    fun `компактное форматирование без копеек`() {
        val below = Money.compact(1_234_500L)
        assertEquals(Money.Scale.NONE, below.scale)
        assertEquals("12 345", below.digits)
    }

    @Test
    fun `компактное форматирование с копейками`() {
        val below = Money.compact(123_456L)
        assertEquals(Money.Scale.NONE, below.scale)
        assertEquals("1 234,56", below.digits)
    }

    @Test
    fun `компактное форматирование миллионов`() {
        val millions = Money.compact(670_000_000L)
        assertEquals(Money.Scale.MILLION, millions.scale)
        assertEquals("6,7", millions.digits)

        val exact = Money.compact(600_000_000L)
        assertEquals(Money.Scale.MILLION, exact.scale)
        assertEquals("6", exact.digits)
    }

    @Test
    fun `компактное форматирование миллиардов`() {
        val billions = Money.compact(120_000_000_000L)
        assertEquals(Money.Scale.BILLION, billions.scale)
        assertEquals("1,2", billions.digits)
    }

    @Test
    fun `компактное форматирование округляет вверх`() {
        // 9 999 999 999,90 → 10 млрд.
        val rounded = Money.compact(999_999_999_990L)
        assertEquals(Money.Scale.BILLION, rounded.scale)
        assertEquals("10", rounded.digits)
    }

    @Test
    fun `компактное форматирование уходит на следующий порядок`() {
        // 999 999 990 → не «1 000 млн», а «1 млрд».
        val bumped = Money.compact(99_999_999_900L)
        assertEquals(Money.Scale.BILLION, bumped.scale)
        assertEquals("1", bumped.digits)
    }

    @Test
    fun `компактное форматирование сохраняет знак`() {
        val negative = Money.compact(-670_000_000L)
        assertEquals(Money.Scale.MILLION, negative.scale)
        assertEquals("-6,7", negative.digits)
    }

    @Test
    fun `ноль отображается как обычное число`() {
        val zero = Money.compact(0)
        assertEquals(Money.Scale.NONE, zero.scale)
        assertEquals("0", zero.digits)
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
