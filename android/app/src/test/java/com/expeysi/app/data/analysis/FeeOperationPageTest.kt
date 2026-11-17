package com.expeysi.app.data.analysis

import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.TransferFees
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Список псевдо-категории комиссий переводов для «Анализа финансов». */
class FeeOperationPageTest {

    private fun leg(
        id: String,
        feeMinor: Long,
        date: Long,
        from: String = "acc1",
        to: String = "acc2",
        note: String = "",
    ) = TransactionEntity(
        id = id,
        type = TxType.TRANSFER_OUT.wire,
        amountMinor = 10_000L + feeMinor,
        accountId = from,
        categoryId = "",
        date = date,
        note = note,
        linkedId = "pair-$id",
        counterpartyAccountId = to,
        feeMinor = feeMinor,
        updatedAt = 0L,
    )

    private val names = mapOf("acc1" to "Карта", "acc2" to "Наличные")

    @Test
    fun `берутся только ноги с комиссией, по убыванию даты`() {
        val legs = listOf(
            leg("old", feeMinor = 100L, date = 100L),
            leg("no-fee", feeMinor = 0L, date = 200L),
            leg("new", feeMinor = 300L, date = 300L),
        )

        val page = feeOperationPage(legs, names, "₽", 20, 0)

        assertEquals(listOf("new", "old"), page.operations.map { it.id })
        assertEquals(400L, page.totalMinor)
    }

    @Test
    fun `пагинация и общий итог по всем комиссиям периода`() {
        val legs = (1..5).map { index -> leg("l$index", feeMinor = 100L, date = index.toLong()) }

        val first = feeOperationPage(legs, names, "₽", limit = 2, offset = 0)
        assertEquals(listOf("l5", "l4"), first.operations.map { it.id })
        assertTrue(first.hasMore)
        assertEquals(500L, first.totalMinor)

        val last = feeOperationPage(legs, names, "₽", limit = 2, offset = 4)
        assertEquals(listOf("l1"), last.operations.map { it.id })
        assertFalse(last.hasMore)
    }

    @Test
    fun `строка открывает перевод и несёт сумму комиссии`() {
        val legs = listOf(
            leg("with-note", feeMinor = 150L, date = 1L, note = "На отпуск"),
            leg("no-note", feeMinor = 250L, date = 2L),
        )

        val withNote = feeOperationPage(legs, names, "₽", 20, 0).operations
            .single { it.id == "with-note" }
        assertEquals("На отпуск", withNote.title)
        assertEquals("Карта → Наличные", withNote.subtitle)
        assertEquals(150L, withNote.amountMinor)
        assertEquals(TransferFees.ICON_KEY, withNote.iconKey)
        assertEquals(TransferFees.COLOR_KEY, withNote.colorKey)
        assertFalse(withNote.isIncome)
        // id — нога transfer_out: редактор откроет сам перевод по linkedId.
        assertEquals("with-note", withNote.id)

        val noNote = feeOperationPage(legs, names, "₽", 20, 0).operations
            .single { it.id == "no-note" }
        // Без заметки заголовок — счёт-получатель.
        assertEquals("Наличные", noNote.title)
    }
}
