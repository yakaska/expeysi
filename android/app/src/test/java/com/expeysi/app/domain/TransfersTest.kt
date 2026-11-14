package com.expeysi.app.domain

import com.expeysi.app.data.db.TransactionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun leg(
    id: String,
    type: TxType,
    linkedId: String = "",
    accountId: String = "a1",
    counterAccountId: String = "a2",
    date: Long = 0L,
    updatedAt: Long = 0L,
) =
    TransactionEntity(
        id = id,
        type = type.wire,
        amountMinor = 100_00L,
        accountId = accountId,
        categoryId = "",
        date = date,
        linkedId = linkedId,
        counterpartyAccountId = counterAccountId,
        updatedAt = updatedAt,
    )

class TransfersTest {

    @Test
    fun `обе ноги в скоупе сливаются в одну строку за списанием`() {
        val pair = listOf(
            leg("out", TxType.TRANSFER_OUT, linkedId = "L1"),
            leg("in", TxType.TRANSFER_IN, linkedId = "L1", accountId = "a2", counterAccountId = "a1"),
        )
        val collapsed = Transfers.collapse(pair)
        assertEquals(listOf("out"), collapsed.map { it.id })
    }

    @Test
    fun `одинокая нога зачисления остаётся видимой`() {
        val legs = listOf(
            leg("in", TxType.TRANSFER_IN, linkedId = "L1", accountId = "a2", counterAccountId = "a1"),
        )
        assertEquals(listOf("in"), Transfers.collapse(legs).map { it.id })
    }

    @Test
    fun `ноги разных переводов не мешают друг другу`() {
        val legs = listOf(
            leg("out1", TxType.TRANSFER_OUT, linkedId = "L1"),
            leg("in1", TxType.TRANSFER_IN, linkedId = "L1", accountId = "a2"),
            leg("in2", TxType.TRANSFER_IN, linkedId = "L2", accountId = "a2"),
        )
        assertEquals(listOf("out1", "in2"), Transfers.collapse(legs).map { it.id })
    }

    @Test
    fun `ноги без связки не сливаются`() {
        val legs = listOf(
            leg("out", TxType.TRANSFER_OUT),
            leg("in", TxType.TRANSFER_IN, accountId = "a2"),
        )
        assertEquals(listOf("out", "in"), Transfers.collapse(legs).map { it.id })
    }

    @Test
    fun `обычные операции сливаются с собой`() {
        val operations = listOf(
            leg("income", TxType.INCOME, accountId = "a1", counterAccountId = ""),
            leg("expense", TxType.EXPENSE),
        )
        assertEquals(operations, Transfers.collapse(operations))
    }

    @Test
    fun `подпись направления у списания и зачисления зеркальная`() {
        val out = leg("out", TxType.TRANSFER_OUT, accountId = "cash", counterAccountId = "card")
        val inLeg = leg("in", TxType.TRANSFER_IN, accountId = "card", counterAccountId = "cash")
        val names = mapOf("cash" to "Наличные", "card" to "Карта")
        assertEquals(
            "Наличные → Карта",
            Transfers.directionLabel(out, names.getValue(out.accountId), names.getValue(out.counterpartyAccountId)),
        )
        assertEquals(
            "Наличные → Карта",
            Transfers.directionLabel(inLeg, names.getValue(inLeg.accountId), names.getValue(inLeg.counterpartyAccountId)),
        )
    }

    @Test
    fun `подпись без контрагента показывает только известную сторону`() {
        val out = leg("out", TxType.TRANSFER_OUT, accountId = "cash", counterAccountId = "unknown")
        assertEquals("Наличные", Transfers.directionLabel(out, "Наличные", ""))
    }

    @Test
    fun `полная пара помечается свёрнутой по ноге списания`() {
        val pair = listOf(
            leg("out", TxType.TRANSFER_OUT, linkedId = "L1"),
            leg("in", TxType.TRANSFER_IN, linkedId = "L1", accountId = "a2", counterAccountId = "a1"),
        )
        assertTrue(Transfers.isCollapsedPair(pair.first(), pair))
        assertFalse(Transfers.isCollapsedPair(pair.last(), pair))
    }

    @Test
    fun `одинокие ноги свёрнутой парой не считаются`() {
        val legs = listOf(
            leg("out", TxType.TRANSFER_OUT, linkedId = "L1"),
            leg("in", TxType.TRANSFER_IN, linkedId = "L1", accountId = "a2"),
            leg("another", TxType.TRANSFER_OUT, linkedId = "L2"),
        )
        assertFalse(Transfers.isCollapsedPair(legs[0], listOf(legs[0])))
        assertFalse(Transfers.isCollapsedPair(legs[1], legs))
        assertFalse(Transfers.isCollapsedPair(legs[2], legs))
        // Партнёр другой связки не делает пару полной.
        assertFalse(Transfers.isCollapsedPair(legs[0], listOf(legs[0], legs[1].copy(linkedId = "L3"))))
    }

    @Test
    fun `операции без связки нейтральны к свёртке`() {
        val operations = listOf(
            leg("income", TxType.INCOME, accountId = "a1", counterAccountId = ""),
            leg("expense", TxType.EXPENSE),
        )
        operations.forEach { assertFalse(Transfers.isCollapsedPair(it, operations)) }
    }

    @Test
    fun `граница страницы добирает партнёра и пара сворачивается`() = runTest {
        // Нога списания — последний элемент страницы; нога зачисления — первый
        // элемент за её границей, приходит запросом по связке.
        val out = leg("out", TxType.TRANSFER_OUT, linkedId = "L1", date = 20, updatedAt = 2)
        val inLeg = leg("in", TxType.TRANSFER_IN, linkedId = "L1", accountId = "a2", counterAccountId = "a1", date = 10, updatedAt = 1)

        val page = Transfers.supplementSplitLegs(
            page = listOf(out),
            scopeIds = setOf("a1", "a2"),
            linkedLegs = { linkedId -> if (linkedId == "L1") listOf(out, inLeg) else emptyList() },
        )

        assertEquals(listOf("out", "in"), page.map { it.id })
        assertEquals(listOf("out"), Transfers.collapse(page).map { it.id })
        assertTrue(Transfers.isCollapsedPair(out, page))
    }

    @Test
    fun `добранный партнёр занимает своё место в порядке страницы`() = runTest {
        val fresh = leg("fresh", TxType.EXPENSE, date = 100)
        val out = leg("out", TxType.TRANSFER_OUT, linkedId = "L1", date = 20)
        val old = leg("old", TxType.EXPENSE, date = 5)
        val inLeg = leg("in", TxType.TRANSFER_IN, linkedId = "L1", accountId = "a2", counterAccountId = "a1", date = 10)

        val page = Transfers.supplementSplitLegs(
            page = listOf(fresh, out, old),
            scopeIds = setOf("a1", "a2"),
            linkedLegs = { listOf(inLeg) },
        )

        assertEquals(listOf("fresh", "out", "in", "old"), page.map { it.id })
    }

    @Test
    fun `полная страница запросов по связкам не делает`() = runTest {
        val pair = listOf(
            leg("out", TxType.TRANSFER_OUT, linkedId = "L1", date = 20),
            leg("in", TxType.TRANSFER_IN, linkedId = "L1", accountId = "a2", counterAccountId = "a1", date = 10),
        )

        assertEquals(pair, Transfers.supplementSplitLegs(pair, setOf("a1", "a2"), { emptyList() }))
    }

    @Test
    fun `партнёр вне скоупа страницы не добирается`() = runTest {
        val out = leg("out", TxType.TRANSFER_OUT, linkedId = "L1", date = 20)
        val inOtherBalance = leg("in", TxType.TRANSFER_IN, linkedId = "L1", accountId = "a3", counterAccountId = "a1", date = 10)

        val page = Transfers.supplementSplitLegs(
            page = listOf(out),
            scopeIds = setOf("a1"),
            linkedLegs = { listOf(inOtherBalance) },
        )

        assertEquals(listOf("out"), page.map { it.id })
    }
}
