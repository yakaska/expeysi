package com.expeysi.app.data.analysis

import com.expeysi.app.data.db.DebtAdjustmentEntity
import com.expeysi.app.domain.DebtAdjustmentKind
import com.expeysi.app.domain.DebtWriteOffs
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Список псевдо-категории списаний долга для «Анализа финансов». */
class WriteOffOperationPageTest {

    private fun adjustment(
        id: String,
        amountMinor: Long,
        date: Long,
        counterpartyId: String = "c1",
        note: String = "",
    ) = DebtAdjustmentEntity(
        id = id,
        counterpartyId = counterpartyId,
        kind = DebtAdjustmentKind.WRITE_OFF.wire,
        amountMinor = amountMinor,
        currencyCode = "RUB",
        date = date,
        note = note,
        countInStats = true,
        updatedAt = 0L,
    )

    private val names = mapOf("c1" to "Аня Петрова", "c2" to "ООО Ромашка")

    @Test
    fun `расход берёт только минус, доход - только плюс`() {
        val adjustments = listOf(
            adjustment("a", amountMinor = -30_000L, date = 1L),
            adjustment("b", amountMinor = 50_000L, date = 2L),
        )

        val expense = writeOffOperationPage(adjustments, names, TxType.EXPENSE, "RUB", "₽", 20, 0)
        assertEquals(listOf("a"), expense.operations.map { it.id })
        assertEquals(30_000L, expense.totalMinor)

        val income = writeOffOperationPage(adjustments, names, TxType.INCOME, "RUB", "₽", 20, 0)
        assertEquals(listOf("b"), income.operations.map { it.id })
        assertEquals(50_000L, income.totalMinor)
    }

    @Test
    fun `строки отсортированы по убыванию даты`() {
        val adjustments = listOf(
            adjustment("old", amountMinor = -10_000L, date = 100L),
            adjustment("new", amountMinor = -20_000L, date = 300L),
            adjustment("mid", amountMinor = -30_000L, date = 200L),
        )

        val page = writeOffOperationPage(adjustments, names, TxType.EXPENSE, "RUB", "₽", 20, 0)

        assertEquals(listOf("new", "mid", "old"), page.operations.map { it.id })
    }

    @Test
    fun `пагинация и общий итог по всем списаниям`() {
        val adjustments = (1..5).map { index ->
            adjustment("a$index", amountMinor = -10_000L, date = index.toLong())
        }

        val first = writeOffOperationPage(adjustments, names, TxType.EXPENSE, "RUB", "₽", limit = 2, offset = 0)
        assertEquals(listOf("a5", "a4"), first.operations.map { it.id })
        assertTrue(first.hasMore)
        // Итог — по всем списаниям периода, а не только по странице.
        assertEquals(50_000L, first.totalMinor)

        val last = writeOffOperationPage(adjustments, names, TxType.EXPENSE, "RUB", "₽", limit = 2, offset = 4)
        assertEquals(listOf("a1"), last.operations.map { it.id })
        assertFalse(last.hasMore)
    }

    @Test
    fun `строка несёт имя контрагента, знак и оформление псевдо-категории`() {
        val adjustments = listOf(
            adjustment("out", amountMinor = -30_000L, date = 1L, counterpartyId = "c1", note = "Не вернёт"),
            adjustment("in", amountMinor = 40_000L, date = 2L, counterpartyId = "c2"),
        )

        val expense = writeOffOperationPage(adjustments, names, TxType.EXPENSE, "RUB", "₽", 20, 0)
            .operations.single()
        assertEquals("Аня Петрова", expense.title)
        assertEquals("Не вернёт", expense.subtitle)
        assertEquals(30_000L, expense.amountMinor)
        assertEquals(DebtWriteOffs.ICON_KEY, expense.iconKey)
        assertEquals(DebtWriteOffs.COLOR_KEY, expense.colorKey)
        assertFalse(expense.isIncome)
        assertTrue(expense.isWriteOff)

        val income = writeOffOperationPage(adjustments, names, TxType.INCOME, "RUB", "₽", 20, 0)
            .operations.single()
        assertEquals("ООО Ромашка", income.title)
        // Без заметки в подписи — код валюты.
        assertEquals("RUB", income.subtitle)
        assertTrue(income.isIncome)
        assertTrue(income.isWriteOff)
    }
}
