package com.expeysi.app.ui.debts

import com.expeysi.app.R
import com.expeysi.app.data.db.CounterpartyEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.DebtCalculator
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtsRowDisplayTest {

    private fun balance(currencyCode: String, balanceMinor: Long): DebtCalculator.Balance = DebtCalculator.Balance(counterpartyId = "cp", currencyCode = currencyCode, balanceMinor = balanceMinor)

    /** Строка списка с одним контрагентом: имя задаёт порядок NAME-сортировки. */
    private fun row(
        id: String,
        name: String,
        vararg balances: Pair<String, Long>,
        overdue: Boolean = false,
    ): DebtsRow = DebtsRow(
        counterparty = CounterpartyEntity(id = id, name = name, type = "person", updatedAt = 0L),
        balances = balances.map { (code, minor) -> DebtCalculator.Balance(id, code, minor) },
        overdue = overdue,
    )

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

    /* ----------------------- Итоги, сортировка и фильтр ----------------------- */

    @Test
    fun `итоги считают закрытых и архивных тоже`() {
        // Секция скрывает строку, но не отменяет сам долг: debtsTotals
        // вызывается в viewModel по всем строкам без фильтра секций.
        val rows = listOf(
            row("1", "Аня", "RUB" to 100_000L),
            row("2", "Борис", "RUB" to 0L),
            row("3", "Вера", "USD" to -40_000L),
        )

        val totals = debtsTotals(rows)

        assertEquals(mapOf("RUB" to 100_000L), totals.owedToMe)
        assertEquals(mapOf("USD" to 40_000L), totals.owedByMe)
    }

    @Test
    fun `сортировка по имени идёт по алфавиту`() {
        val rows = listOf(
            row("1", "Борис", "RUB" to 900_000L),
            row("2", "Аня", "RUB" to 100_000L),
            row("3", "Вера"),
        )

        assertEquals(
            listOf("2", "1", "3"),
            sortDebtsRows(rows, DebtsSort.NAME).map { it.counterparty.id },
        )
    }

    @Test
    fun `сортировка по остатку идёт по модулю`() {
        // Знак остатка не важен: «тяжелее» тот, кто должен больше в любую сторону.
        val rows = listOf(
            row("1", "Аня", "RUB" to 30_000L),
            row("2", "Борис", "RUB" to -50_000L),
            row("3", "Вера", "RUB" to 0L),
        )

        assertEquals(
            listOf("2", "1", "3"),
            sortDebtsRows(rows, DebtsSort.BALANCE).map { it.counterparty.id },
        )
    }

    @Test
    fun `сначала просроченные, дальше по модулю остатка`() {
        val rows = listOf(
            row("1", "Аня", "RUB" to 500_000L),
            row("2", "Борис", "RUB" to 900_000L, overdue = true),
            row("3", "Вера", "RUB" to 0L),
        )

        assertEquals(
            listOf("2", "1", "3"),
            sortDebtsRows(rows, DebtsSort.OVERDUE).map { it.counterparty.id },
        )
    }

    @Test
    fun `фильтр стороны отсекает чужие стороны`() {
        val rows = listOf(
            row("1", "Аня", "RUB" to 100_000L),
            row("2", "Борис", "RUB" to -50_000L),
            // Смешанная строка проходит оба фильтра: в разных валютах долг
            // лежит по обе стороны.
            row("3", "Вера", "RUB" to 10_000L, "USD" to -5_000L),
        )

        assertEquals(
            listOf("1", "3"),
            filterDebtsRows(rows, DebtsSideFilter.OWED_TO_ME).map { it.counterparty.id },
        )
        assertEquals(
            listOf("2", "3"),
            filterDebtsRows(rows, DebtsSideFilter.OWED_BY_ME).map { it.counterparty.id },
        )
        assertEquals(
            listOf("1", "2", "3"),
            filterDebtsRows(rows, DebtsSideFilter.ALL).map { it.counterparty.id },
        )
    }

    @Test
    fun `просрочен только срок при открытом остатке`() {
        val todayStart = 1_000_000L
        fun tx(dueDate: Long?) = TransactionEntity(
            id = "t",
            type = TxType.DEBT_LEND.wire,
            amountMinor = 100_000L,
            accountId = "acc",
            categoryId = "",
            date = 0L,
            counterpartyId = "c1",
            dueDate = dueDate,
            updatedAt = 0L,
        )
        val balances = listOf(DebtCalculator.Balance("c1", "RUB", 100_000L))

        // Срок вчера, долг открыт — просрочен.
        assertEquals(
            setOf("c1"),
            overdueCounterpartyIds(listOf(tx(dueDate = 999_999L)), balances, todayStart) { "RUB" },
        )
        // Долг закрыт списанием или погашением — просрочки в списке нет.
        assertTrue(
            overdueCounterpartyIds(
                listOf(tx(dueDate = 999_999L)),
                listOf(DebtCalculator.Balance("c1", "RUB", 0L)),
                todayStart,
            ) { "RUB" }.isEmpty(),
        )
        // Срок сегодня или позже — не просрочено.
        assertTrue(
            overdueCounterpartyIds(listOf(tx(dueDate = 1_000_000L)), balances, todayStart) { "RUB" }
                .isEmpty(),
        )
        // Без срока — не просрочено.
        assertTrue(
            overdueCounterpartyIds(listOf(tx(dueDate = null)), balances, todayStart) { "RUB" }
                .isEmpty(),
        )
    }
}
