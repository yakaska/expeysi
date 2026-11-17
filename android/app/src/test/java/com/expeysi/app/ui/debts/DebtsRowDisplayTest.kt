package com.expeysi.app.ui.debts

import com.expeysi.app.R
import com.expeysi.app.domain.DebtCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtsRowDisplayTest {

    private fun balance(currencyCode: String, balanceMinor: Long): DebtCalculator.Balance =
        DebtCalculator.Balance(counterpartyId = "cp", currencyCode = currencyCode, balanceMinor = balanceMinor)

    /* --------------------------- Тон и части строки --------------------------- */

    @Test
    fun `положительный остаток - вам должны - зелёный`() {
        val display = debtsRowDisplay(listOf(balance("RUB", 50_000)))
        assertEquals(DebtsTone.POSITIVE, display.tone)
        assertFalse(display.settled)
        assertEquals(
            listOf(DebtsBalancePart(50_000, "RUB", R.string.debts_row_owed_to_me)),
            display.parts,
        )
    }

    @Test
    fun `отрицательный остаток - вы должны - красный`() {
        val display = debtsRowDisplay(listOf(balance("RUB", -30_000)))
        assertEquals(DebtsTone.NEGATIVE, display.tone)
        assertFalse(display.settled)
        assertEquals(
            listOf(DebtsBalancePart(30_000, "RUB", R.string.debts_row_owed_by_me)),
            display.parts,
        )
    }

    @Test
    fun `нулевой остаток - расчёты закрыты и серый`() {
        val display = debtsRowDisplay(listOf(balance("RUB", 0)))
        assertTrue(display.settled)
        assertEquals(DebtsTone.NEUTRAL, display.tone)
        assertTrue(display.parts.isEmpty())
    }

    @Test
    fun `смешанные валюты одного направления - зелёный и обе части видны`() {
        val display = debtsRowDisplay(
            listOf(balance("RUB", 100_000), balance("VND", 50_000)),
        )
        assertEquals(DebtsTone.POSITIVE, display.tone)
        assertFalse(display.settled)
        assertEquals(2, display.parts.size)
        assertEquals(
            listOf(
                DebtsBalancePart(100_000, "RUB", R.string.debts_row_owed_to_me),
                DebtsBalancePart(50_000, "VND", R.string.debts_row_owed_to_me),
            ),
            display.parts,
        )
    }

    @Test
    fun `смешанные направления - нейтральный серый, у частей свои строки`() {
        val display = debtsRowDisplay(
            listOf(balance("RUB", 100_000), balance("VND", -50_000)),
        )
        assertEquals(DebtsTone.NEUTRAL, display.tone)
        assertFalse(display.settled)
        assertEquals(
            listOf(
                DebtsBalancePart(100_000, "RUB", R.string.debts_row_owed_to_me),
                DebtsBalancePart(50_000, "VND", R.string.debts_row_owed_by_me),
            ),
            display.parts,
        )
    }

    @Test
    fun `все валюты в нуле - расчёты закрыты`() {
        val display = debtsRowDisplay(
            listOf(balance("RUB", 0), balance("VND", 0)),
        )
        assertTrue(display.settled)
        assertEquals(DebtsTone.NEUTRAL, display.tone)
        assertTrue(display.parts.isEmpty())
    }

    @Test
    fun `нулевые валюты скрываются, порядок ненулевых сохранён`() {
        val display = debtsRowDisplay(
            listOf(balance("RUB", 0), balance("VND", 70_000), balance("USD", 10_000)),
        )
        assertEquals(DebtsTone.POSITIVE, display.tone)
        assertEquals(
            listOf(
                DebtsBalancePart(70_000, "VND", R.string.debts_row_owed_to_me),
                DebtsBalancePart(10_000, "USD", R.string.debts_row_owed_to_me),
            ),
            display.parts,
        )
    }

    @Test
    fun `без операций вовсе - расчёты закрыты`() {
        val display = debtsRowDisplay(emptyList())
        assertTrue(display.settled)
        assertEquals(DebtsTone.NEUTRAL, display.tone)
    }

    /* ------------------------------- Инициалы ------------------------------- */

    @Test
    fun `инициалы - первые буквы двух слов`() {
        assertEquals("АП", counterpartyInitials("Аня Петрова"))
    }

    @Test
    fun `инициалы из одного слова`() {
        assertEquals("Г", counterpartyInitials("Гугл"))
    }

    @Test
    fun `инициалы приводятся к верхнему регистру`() {
        assertEquals("ИИ", counterpartyInitials("иван иванов"))
    }

    @Test
    fun `лишние пробелы схлопываются`() {
        assertEquals("ON", counterpartyInitials("  Olle\tNordmann  "))
    }

    @Test
    fun `инициалы не длиннее двух букв`() {
        assertEquals("ИП", counterpartyInitials("Илья Петрович Самозванов-Крутоярынский"))
    }

    @Test
    fun `пустое имя - пустые инициалы`() {
        assertEquals("", counterpartyInitials(""))
        assertEquals("", counterpartyInitials("   "))
    }

    @Test
    fun `знаки препинания перед словом пропускаются`() {
        assertEquals("ОР", counterpartyInitials("«ООО» Ромашка"))
    }
}
