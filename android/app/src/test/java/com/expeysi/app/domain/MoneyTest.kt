package com.expeysi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `парсинг основного и дробных единиц`() {
        assertEquals(1_234_500L, Money.parseToMinor("123,45"))
        assertEquals(1_234_500L, Money.parseToMinor("123.45"))
        assertEquals(1_234_000L, Money.parseToMinor("123.4"))
        assertEquals(10_000_000L, Money.parseToMinor("1000"))
        assertEquals(500L, Money.parseToMinor("0,05"))
        assertEquals(1L, Money.parseToMinor("0,0001"))
    }

    @Test
    fun `парсинг до четырёх знаков дробной части`() {
        assertEquals(12_345_600L, Money.parseToMinor("1 234,56"))
        assertEquals(123_456L, Money.parseToMinor("12,3456"))
        assertEquals(123_110L, Money.parseToMinor("12,311"))
        assertEquals(1_234_000L, Money.parseToMinor("123,4"))
    }

    @Test
    fun `парсинг мусора даёт null`() {
        assertNull(Money.parseToMinor(""))
        assertNull(Money.parseToMinor("abc"))
        assertNull(Money.parseToMinor("1.2.3"))
        assertNull(Money.parseToMinor("12,34567"))
        assertNull(Money.parseToMinor(","))
    }

    @Test
    fun `форматирование с группировкой и значащими знаками`() {
        assertEquals("0", Money.format(0))
        assertEquals("0,0105", Money.format(105))
        assertEquals("1,05", Money.format(10_500))
        assertEquals("12,3456", Money.format(123_456))
        assertEquals("-5", Money.format(-50_000))
        assertEquals("-0,05", Money.format(-500))
        assertEquals("123 456,789", Money.format(1_234_567_890L))
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
    fun `введённые нули заменяют префил а не слипаются с ним`() {
        assertEquals("0", Money.formatInput("0"))
        assertEquals("5", Money.formatInput("05"))
        assertEquals("0", Money.formatInput("000"))
        assertEquals("0,5", Money.formatInput("0,5"))
        assertEquals("0,05", Money.formatInput("0,05"))
        assertEquals("-5", Money.formatInput("-05"))
        assertEquals("10 000", Money.formatInput("010000"))
    }

    @Test
    fun `некорректный ввод не перезаписывается форматированием`() {
        assertEquals("1.2.3", Money.formatInput("1.2.3"))
        assertEquals("abc", Money.formatInput("abc"))
    }

    @Test
    fun `компактное форматирование без дробной части`() {
        val below = Money.compact(123_450_000L)
        assertEquals(Money.Scale.NONE, below.scale)
        assertEquals("12\u00A0345", below.digits)
    }

    @Test
    fun `компактное форматирование с дробной частью`() {
        val below = Money.compact(12_345_600L)
        assertEquals(Money.Scale.NONE, below.scale)
        assertEquals("1 234,56", below.digits)
    }

    @Test
    fun `только значащие знаки без нулевых хвостов`() {
        assertEquals("12,34", Money.format(123_400))
        assertEquals("12,311", Money.format(123_110))
        assertEquals("123,4", Money.format(1_234_000))
        assertEquals("123,4", Money.formatAuto(1_234_000))
    }

    @Test
    fun `компактное форматирование миллионов`() {
        val millions = Money.compact(67_000_000_000L)
        assertEquals(Money.Scale.MILLION, millions.scale)
        assertEquals("6,7", millions.digits)

        val exact = Money.compact(60_000_000_000L)
        assertEquals(Money.Scale.MILLION, exact.scale)
        assertEquals("6", exact.digits)
    }

    @Test
    fun `компактное форматирование миллиардов`() {
        val billions = Money.compact(12_000_000_000_000L)
        assertEquals(Money.Scale.BILLION, billions.scale)
        assertEquals("1,2", billions.digits)
    }

    @Test
    fun `компактное форматирование округляет вверх`() {
        // 9 999 999 999,90 → 10 млрд.
        val rounded = Money.compact(99_999_999_999_000L)
        assertEquals(Money.Scale.BILLION, rounded.scale)
        assertEquals("10", rounded.digits)
    }

    @Test
    fun `компактное форматирование уходит на следующий порядок`() {
        // 999 999 990 → не «1 000 млн», а «1 млрд».
        val bumped = Money.compact(9_999_999_990_000L)
        assertEquals(Money.Scale.BILLION, bumped.scale)
        assertEquals("1", bumped.digits)
    }

    @Test
    fun `компактное форматирование сохраняет знак`() {
        val negative = Money.compact(-67_000_000_000L)
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
        assertEquals(12_345_600L, Money.parseToMinor("1\u00A0234,56"))
        assertEquals(12_345_600L, Money.parseToMinor("\n1\u202F234,56 "))
        assertEquals("1 234,56", Money.formatInput("1\u00A0234,56"))
        assertEquals("1 234,56", Money.formatInput("1\u202F234,56"))
    }

    @Test
    fun `парсинг и форматирование согласованы`() {
        assertEquals(12_345_600L, Money.parseToMinor(Money.format(12_345_600)))
        assertEquals(123_110L, Money.parseToMinor(Money.format(123_110)))
    }

    @Test
    fun `форматирование без дробной части`() {
        assertEquals("0", Money.formatWhole(0))
        assertEquals("9", Money.formatWhole(90_000))
        assertEquals("99", Money.formatWhole(999_999))
        assertEquals("100", Money.formatWhole(1_000_000))
        assertEquals("100\u00A0000", Money.formatWhole(1_000_000_000))
        assertEquals("-487", Money.formatWhole(-4_875_000))
        assertEquals("922\u00A0337\u00A0203\u00A0685\u00A0477", Money.formatWhole(Long.MAX_VALUE))
        assertEquals("-922\u00A0337\u00A0203\u00A0685\u00A0477", Money.formatWhole(Long.MIN_VALUE))
    }

    @Test
    fun `автоформатирование показывает только значащие знаки`() {
        assertEquals("0", Money.formatAuto(0))
        assertEquals("1", Money.formatAuto(10_000L))
        assertEquals("1,5", Money.formatAuto(15_000L))
        assertEquals("12,34", Money.formatAuto(123_400))
        assertEquals("12,311", Money.formatAuto(123_110))
        assertEquals("1 234,56", Money.formatAuto(12_345_600))
        assertEquals("58\u00A0000", Money.formatAuto(580_000_000L))
        assertEquals("-1,5", Money.formatAuto(-15_000L))
        assertEquals("-58\u00A0000", Money.formatAuto(-580_000_000L))
    }

    @Test
    fun `поле редактирования получает сумму через формат ввода`() {
        assertEquals("58 000", Money.formatInput(Money.formatAuto(580_000_000L)))
        assertEquals("1 234,56", Money.formatInput(Money.formatAuto(12_345_600)))
        assertEquals("-5", Money.formatInput(Money.formatAuto(-50_000)))
        assertEquals(580_000_000L, Money.parseToMinor("58 000"))
        assertEquals(12_345_600L, Money.parseToMinor(Money.formatInput(Money.formatAuto(12_345_600))))
    }

    @Test
    fun `компактная строка без крупных порядков`() {
        val million = "млн"
        val billion = "млрд"
        assertEquals("0", Money.formatCompact(0, million, billion))
        assertEquals("9,99", Money.formatCompact(99_900, million, billion))
        // У дробных значений format() группирует разряды обычным пробелом.
        assertEquals("9 999,99", Money.formatCompact(99_999_900, million, billion))
        assertEquals("10\u00A0000", Money.formatCompact(100_000_000, million, billion))
        assertEquals("455 841,37", Money.formatCompact(4_558_413_700, million, billion))
        assertEquals("-455 841,37", Money.formatCompact(-4_558_413_700, million, billion))
        assertEquals("48 750,25", Money.formatCompact(487_502_500, million, billion))
        assertEquals("-0,99", Money.formatCompact(-9_900, million, billion))
    }

    @Test
    fun `компактная строка с единицами порядка`() {
        val million = "млн"
        val billion = "млрд"
        assertEquals("6,7\u00A0млн", Money.formatCompact(67_000_000_000L, million, billion))
        assertEquals("6\u00A0млн", Money.formatCompact(60_000_000_000L, million, billion))
        assertEquals("-6,7\u00A0млн", Money.formatCompact(-67_000_000_000L, million, billion))
        assertEquals("1,2\u00A0млрд", Money.formatCompact(12_000_000_000_000L, million, billion))
    }

    @Test
    fun `округление компактной строки не даёт 1000 млн`() {
        // 999 950 000 основных единиц → не «1000,0 млн», а «1 млрд».
        assertEquals("1\u00A0млрд", Money.formatCompact(9_999_500_000_000L, "млн", "млрд"))
        assertEquals("10\u00A0млн", Money.formatCompact(99_995_000_000L, "млн", "млрд"))
    }

    @Test
    fun `экстремальные значения не бросают исключений`() {
        Money.formatWhole(Long.MAX_VALUE)
        Money.formatWhole(Long.MIN_VALUE)
        Money.formatCompact(Long.MAX_VALUE, "млн", "млрд")
        Money.compact(Long.MAX_VALUE)
        Money.compact(Long.MIN_VALUE)
        // NONE-путь после переполнения формата возвращает разряды обычным пробелом.
        assertEquals("922 337 203 685 477,5807", Money.formatCompact(Long.MAX_VALUE, "млн", "млрд"))
    }
}
