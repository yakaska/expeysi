package com.expeysi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class TransferCalcTest {

    @Test
    fun `зачисление равно списанию умноженному на курс`() {
        assertEquals("7 250", TransferCalc.receiveText("100", "72,5"))
        assertEquals("89,18", TransferCalc.receiveText("1,23", "72,5"))
        assertEquals("2 469,12", TransferCalc.receiveText("1 234,56", "2"))
        assertEquals("0,01", TransferCalc.receiveText("0,05", "0,123"))
    }

    @Test
    fun `зачисление не пересчитывается при непригодном вводе`() {
        assertNull(TransferCalc.receiveText("", "72,5"))
        assertNull(TransferCalc.receiveText("abc", "72,5"))
        assertNull(TransferCalc.receiveText("-100", "72,5"))
        assertNull(TransferCalc.receiveText("0", "72,5"))
        assertNull(TransferCalc.receiveText("100", ""))
        assertNull(TransferCalc.receiveText("100", "abc"))
        assertNull(TransferCalc.receiveText("100", "0"))
        assertNull(TransferCalc.receiveText("100", "-1"))
    }

    @Test
    fun `поддерживаются обе десятичные точки и пробелы разрядов`() {
        assertEquals("7 250", TransferCalc.receiveText("100.0", "72.5"))
        assertEquals("72 500", TransferCalc.receiveText("1 000", "72,5"))
        assertEquals("2 469,12", TransferCalc.receiveText("1\u00A0234,56", "2"))
        assertEquals(BigDecimal("72.5"), TransferCalc.parse("72,5"))
        assertEquals(BigDecimal("72.5"), TransferCalc.parse("72.5"))
        assertNull(TransferCalc.parse(""))
        assertNull(TransferCalc.parse("abc"))
        assertNull(TransferCalc.parse("-1,5"))
    }

    @Test
    fun `курс равен отношению зачисления к списанию`() {
        assertEquals("72,5", TransferCalc.rateText("100", "7 250"))
        assertEquals("1", TransferCalc.rateText("250", "250"))
        assertEquals("0", TransferCalc.rateText("100", "0"))
    }

    @Test
    fun `курс не пересчитывается при непригодном вводе`() {
        assertNull(TransferCalc.rateText("", "7 250"))
        assertNull(TransferCalc.rateText("0", "7 250"))
        assertNull(TransferCalc.rateText("100", "abc"))
        assertNull(TransferCalc.rateText("100", "-1"))
    }

    @Test
    fun `курс округляется до шести знаков HALF_UP`() {
        assertEquals("0,333333", TransferCalc.rateText("3", "1"))
        assertEquals("0,666667", TransferCalc.rateText("3", "2"))
        assertEquals("0,142857", TransferCalc.rateText("7", "1"))
        // 1,0000001 при шести знаках округляется до единицы.
        assertEquals("1", TransferCalc.rateText("10 000 000", "10 000 001"))
        // Мельче шести знаков курс не бывает: округление даёт нулевой курс.
        assertEquals("0", TransferCalc.rateText("100 000 000", "1"))
    }

    // Обратный курс с округлением отличается от исходного в последних знаках.
    @Test
    fun `обратный курс`() {
        assertEquals("0,013793", TransferCalc.invertedRateText("72,5"))
        assertEquals("72,500544", TransferCalc.invertedRateText("0,013793"))
        assertNull(TransferCalc.invertedRateText(""))
        assertNull(TransferCalc.invertedRateText("abc"))
        assertNull(TransferCalc.invertedRateText("0"))
    }

    @Test
    fun `одинаковые валюты — курс один, зачисление зеркалит списание`() {
        assertEquals("1", TransferCalc.rateText("58 000", "58 000"))
        assertEquals("58 000", TransferCalc.receiveText("58 000", "1"))
    }

    @Test
    fun `разворот перевода меняет суммы местами и инвертирует курс`() {
        val swapped = TransferCalc.swap("1 234,56", "8 950,13", "72,5")
        assertEquals("8 950,13", swapped.sendText)
        assertEquals("1 234,56", swapped.receiveText)
        assertEquals("0,013793", swapped.rateText)
    }

    @Test
    fun `разворот перевода без курса не подставляет курс`() {
        val swapped = TransferCalc.swap("100", "", "")
        assertEquals("", swapped.sendText)
        assertEquals("100", swapped.receiveText)
        assertNull(swapped.rateText)
    }

    @Test
    fun `готовность перевода`() {
        val from = "acc-1"
        val to = "acc-2"
        // Разные валюты: нужны курс и зачисление.
        assertEquals(true, TransferCalc.canSave(from, to, false, "100", "7 250", "72,5", ""))
        assertEquals(false, TransferCalc.canSave(from, to, false, "100", "", "", ""))
        assertEquals(false, TransferCalc.canSave(from, to, false, "100", "7 250", "", ""))
        assertEquals(false, TransferCalc.canSave(from, to, false, "100", "0", "72,5", ""))
        // Одинаковые валюты: одного списания достаточно.
        assertEquals(true, TransferCalc.canSave(from, to, true, "100", "", "", "50"))
        assertEquals(true, TransferCalc.canSave(from, to, true, "100", "300", "", ""))
    }

    @Test
    fun `готовность перевода требует разные счета и положительное списание`() {
        assertEquals(false, TransferCalc.canSave(null, "acc-2", false, "100", "7 250", "72,5", ""))
        assertEquals(false, TransferCalc.canSave("acc-1", null, false, "100", "7 250", "72,5", ""))
        assertEquals(false, TransferCalc.canSave("acc-1", "acc-1", false, "100", "7 250", "72,5", ""))
        assertEquals(false, TransferCalc.canSave("acc-1", "acc-2", false, "", "7 250", "72,5", ""))
        assertEquals(false, TransferCalc.canSave("acc-1", "acc-2", false, "0", "7 250", "72,5", ""))
        assertEquals(false, TransferCalc.canSave("acc-1", "acc-2", false, "abc", "7 250", "72,5", ""))
    }

    @Test
    fun `готовность перевода требует неотрицательную комиссию`() {
        assertEquals(true, TransferCalc.canSave("acc-1", "acc-2", false, "100", "7 250", "72,5", ""))
        assertEquals(true, TransferCalc.canSave("acc-1", "acc-2", false, "100", "7 250", "72,5", "0"))
        assertEquals(true, TransferCalc.canSave("acc-1", "acc-2", false, "100", "7 250", "72,5", "1,5"))
        assertEquals(false, TransferCalc.canSave("acc-1", "acc-2", false, "100", "7 250", "72,5", "-1"))
        assertEquals(false, TransferCalc.canSave("acc-1", "acc-2", false, "100", "7 250", "72,5", "a,"))
    }

    @Test
    fun `точный курс по суммам ног без округления`() {
        assertEquals(
            0,
            TransferCalc.exactRate(150, 450)!!.compareTo(BigDecimal("3")),
        )
        // Непрерывающаяся дробь хранит 34 значащие цифры, а не шесть знаков.
        assertEquals(34, TransferCalc.exactRate(300, 100)!!.precision())
        assertNull(TransferCalc.exactRate(0, 100))
        assertNull(TransferCalc.exactRate(100, -1))
    }

    @Test
    fun `зачисление считается без промежуточного округления курса`() {
        // Зачисление по дробному курсу не теряет копейки: 300 × 1/3 = 100,
        // хотя хранимый курс — 0,333… (34 знака) с хвостом девяток в произведении.
        val third = TransferCalc.exactRate(300, 100)!!
        assertEquals(100L, TransferCalc.receiveMinor(300, third))
        assertEquals(12300L, TransferCalc.receiveMinor(36900, third))
        // Крупный курс и точное умножение: 100 × 25 000 = 2 500 000.
        assertEquals(2_500_000L, TransferCalc.receiveMinor(100, BigDecimal("25000")))
        assertNull(TransferCalc.receiveMinor(0, BigDecimal("25000")))
        assertNull(TransferCalc.receiveMinor(100, BigDecimal.ZERO))
        assertNull(TransferCalc.receiveMinor(100, BigDecimal("-1")))
    }

    @Test
    fun `обратный курс точный и разворачивается обратно`() {
        // 1 / 25 000 = 0,00004 точно; обратный разворот возвращает 25 000 без дрейфа.
        val inverted = TransferCalc.invertRate(BigDecimal("25000"))!!
        assertEquals(0, inverted.compareTo(BigDecimal("0.00004")))
        val roundTrip = TransferCalc.invertRate(inverted)!!
        assertEquals(0, roundTrip.compareTo(BigDecimal("25000")))
        assertNull(TransferCalc.invertRate(BigDecimal.ZERO))
        assertNull(TransferCalc.invertRate(BigDecimal("-1")))
    }

    @Test
    fun `показ курса округляется до шести значащих цифр`() {
        assertEquals("25 000", TransferCalc.formatRateDisplay(BigDecimal("25000")))
        assertEquals("72,5", TransferCalc.formatRateDisplay(BigDecimal("72.5")))
        // Дробный курс: шесть значащих цифр вместо шести знаков после запятой.
        assertEquals("0,333333", TransferCalc.formatRateDisplay(TransferCalc.exactRate(300, 100)!!))
        // Мелкие курсы не съедаются шестью знаками после запятой.
        assertEquals("0,0000395", TransferCalc.formatRateDisplay(BigDecimal("0.0000395")))
        // Седьмой знак и дальше срезаются HALF_UP.
        assertEquals("1 234 570", TransferCalc.formatRateDisplay(BigDecimal("1234567")))
    }
}
