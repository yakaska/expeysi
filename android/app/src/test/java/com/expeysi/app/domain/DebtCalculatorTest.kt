package com.expeysi.app.domain

import com.expeysi.app.data.db.DebtAdjustmentEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.DebtAdjustmentKind.OPENING_BALANCE
import com.expeysi.app.domain.DebtAdjustmentKind.WRITE_OFF
import com.expeysi.app.domain.DebtCalculator.Balance
import com.expeysi.app.domain.DebtCalculator.Delta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtCalculatorTest {

    private fun tx(
        type: TxType,
        counterpartyId: String = "c1",
        accountId: String = "acc",
        amountMinor: Long = 10_000L,
        date: Long = 0L,
    ) = TransactionEntity(
        id = "$type-$counterpartyId-$accountId-$amountMinor-$date",
        type = type.wire,
        amountMinor = amountMinor,
        accountId = accountId,
        categoryId = "",
        date = date,
        counterpartyId = counterpartyId,
        updatedAt = 0L,
    )

    private fun adjustment(
        counterpartyId: String = "c1",
        amountMinor: Long,
        currencyCode: String = "RUB",
        kind: DebtAdjustmentKind = OPENING_BALANCE,
    ) = DebtAdjustmentEntity(
        id = "$kind-$counterpartyId-$amountMinor-$currencyCode",
        counterpartyId = counterpartyId,
        kind = kind.wire,
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        date = 0L,
        updatedAt = 0L,
    )

    /** Вклады из истории контрагента: валюта резолвится по счёту операции. */
    private fun deltas(
        transactions: List<TransactionEntity> = emptyList(),
        adjustments: List<DebtAdjustmentEntity> = emptyList(),
        currencies: Map<String, String> = mapOf("acc" to "RUB", "usd-acc" to "USD"),
    ): List<Delta> = DebtCalculator.transactionDeltas(transactions, currencies::getValue) +
        DebtCalculator.adjustmentDeltas(adjustments)

    @Test
    fun `выданный долг увеличивает остаток контрагента`() {
        val rows = deltas(transactions = listOf(tx(TxType.DEBT_LEND, amountMinor = 250_000L)))

        assertEquals(250_000L, DebtCalculator.balanceOf(rows, "c1", "RUB"))
    }

    @Test
    fun `взятый долг уменьшает остаток контрагента`() {
        val rows = deltas(transactions = listOf(tx(TxType.DEBT_BORROW, amountMinor = 150_000L)))

        assertEquals(-150_000L, DebtCalculator.balanceOf(rows, "c1", "RUB"))
    }

    @Test
    fun `частичное погашение уменьшает модуль остатка`() {
        // Контрагент должен 250 000, вернул 100 000 — должен 150 000.
        val rows = deltas(
            transactions = listOf(
                tx(TxType.DEBT_LEND, amountMinor = 250_000L),
                tx(TxType.DEBT_REPAY_IN, amountMinor = 100_000L),
            ),
        )

        assertEquals(150_000L, DebtCalculator.balanceOf(rows, "c1", "RUB"))
    }

    @Test
    fun `перепогашение меняет знак в минус`() {
        // Должен был 100 000, вернул 130 000 — теперь вы должны ему 30 000.
        val rows = deltas(
            transactions = listOf(
                tx(TxType.DEBT_LEND, amountMinor = 100_000L),
                tx(TxType.DEBT_REPAY_IN, amountMinor = 130_000L),
            ),
        )

        assertEquals(-30_000L, DebtCalculator.balanceOf(rows, "c1", "RUB"))
    }

    @Test
    fun `погашение своего долга возвращает остаток к нулю`() {
        // Должны 200 000, отдали 200 000 — закрыт.
        val rows = deltas(
            transactions = listOf(
                tx(TxType.DEBT_BORROW, amountMinor = 200_000L),
                tx(TxType.DEBT_REPAY_OUT, amountMinor = 200_000L),
            ),
        )

        val balance = DebtCalculator.balances(rows).single()
        assertEquals(0L, balance.balanceMinor)
        assertTrue(balance.isSettled)
    }

    @Test
    fun `полное списание через корректировку закрывает остаток`() {
        // Должен 300 000, списали 300 000.
        val rows = deltas(
            transactions = listOf(tx(TxType.DEBT_LEND, amountMinor = 300_000L)),
            adjustments = listOf(adjustment(amountMinor = DebtCalculator.writeOffDelta(300_000L, 300_000L), kind = WRITE_OFF)),
        )

        assertEquals(0L, DebtCalculator.balanceOf(rows, "c1", "RUB"))
    }

    @Test
    fun `частичное списание уменьшает модуль остатка`() {
        // Должны 100 000, списали 30 000 — осталиcь должны 70 000.
        val rows = deltas(
            transactions = listOf(tx(TxType.DEBT_BORROW, amountMinor = 100_000L)),
            adjustments = listOf(adjustment(amountMinor = DebtCalculator.writeOffDelta(-100_000L, 30_000L), kind = WRITE_OFF)),
        )

        assertEquals(-70_000L, DebtCalculator.balanceOf(rows, "c1", "RUB"))
    }

    @Test
    fun `долг, закрытый списанием, помечается в ленте`() {
        val writeOff = adjustment(amountMinor = -300_000L, kind = WRITE_OFF)
        val rows = deltas(
            transactions = listOf(tx(TxType.DEBT_LEND, amountMinor = 300_000L)),
            adjustments = listOf(writeOff),
        )

        assertEquals(setOf("c1" to "RUB"), DebtCalculator.pairsClosedByWriteOff(rows, listOf(writeOff)))
    }

    @Test
    fun `долг, закрытый деньгами с участием списания, тоже помечается`() {
        // Вернули 60 000 деньгами, остальные 40 000 списали — в закрытии
        // участвовала корректировка, помечаем.
        val writeOff = adjustment(amountMinor = -40_000L, kind = WRITE_OFF)
        val rows = deltas(
            transactions = listOf(
                tx(TxType.DEBT_LEND, amountMinor = 100_000L),
                tx(TxType.DEBT_REPAY_IN, amountMinor = 60_000L),
            ),
            adjustments = listOf(writeOff),
        )

        assertEquals(setOf("c1" to "RUB"), DebtCalculator.pairsClosedByWriteOff(rows, listOf(writeOff)))
    }

    @Test
    fun `пара, закрытая деньгами без списаний, пометки не получает`() {
        val rows = deltas(
            transactions = listOf(
                tx(TxType.DEBT_LEND, amountMinor = 200_000L),
                tx(TxType.DEBT_REPAY_IN, amountMinor = 200_000L),
            ),
        )

        assertEquals(emptySet<Pair<String, String>>(), DebtCalculator.pairsClosedByWriteOff(rows, emptyList()))
    }

    @Test
    fun `переоткрытый после списания долг пометки не получает`() {
        // Списание закрыло 100 000, но затем выдали ещё 50 000 — долг открыт.
        val writeOff = adjustment(amountMinor = -100_000L, kind = WRITE_OFF)
        val rows = deltas(
            transactions = listOf(
                tx(TxType.DEBT_LEND, amountMinor = 100_000L),
                tx(TxType.DEBT_LEND, amountMinor = 50_000L, date = 1L),
            ),
            adjustments = listOf(writeOff),
        )

        assertEquals(emptySet<Pair<String, String>>(), DebtCalculator.pairsClosedByWriteOff(rows, listOf(writeOff)))
    }

    @Test
    fun `удалённое списание пометки не даёт`() {
        val adjustments = listOf(adjustment(amountMinor = -100_000L, kind = WRITE_OFF).copy(deleted = true))
        val rows = deltas(
            transactions = listOf(tx(TxType.DEBT_LEND, amountMinor = 100_000L)),
            adjustments = adjustments,
        )

        assertEquals(emptySet<Pair<String, String>>(), DebtCalculator.pairsClosedByWriteOff(rows, adjustments))
    }

    @Test
    fun `знак дельты списания противоположен остатку`() {
        assertEquals(-30_000L, DebtCalculator.writeOffDelta(30_000L, 30_000L))
        assertEquals(50_000L, DebtCalculator.writeOffDelta(-50_000L, 50_000L))
    }

    @Test
    fun `списание при нулевом остатке ничего не меняет`() {
        // Ноль без списания «перевернулся» бы в долг обратной стороны.
        assertEquals(0L, DebtCalculator.writeOffDelta(0L, 30_000L))
    }

    @Test
    fun `правка без смены направления хранит исходный kind`() {
        // Частично погашенный долг не «переименовывается» в выданный долг
        // из-за изменившегося знака остатка: направление денег не менялось.
        assertEquals(
            TxType.DEBT_BORROW,
            DebtCalculator.editKind(
                originalType = TxType.DEBT_BORROW,
                directionChanged = false,
                outgoing = true,
                balanceMinor = -10_000L,
            ),
        )
        assertEquals(
            TxType.DEBT_REPAY_IN,
            DebtCalculator.editKind(
                originalType = TxType.DEBT_REPAY_IN,
                directionChanged = false,
                outgoing = false,
                balanceMinor = 0L,
            ),
        )
    }

    @Test
    fun `правка со сменой направления выводит kind заново`() {
        assertEquals(
            TxType.DEBT_REPAY_OUT,
            DebtCalculator.editKind(
                originalType = TxType.DEBT_LEND,
                directionChanged = true,
                outgoing = true,
                balanceMinor = -100_00L,
            ),
        )
        assertEquals(
            TxType.DEBT_BORROW,
            DebtCalculator.editKind(
                originalType = TxType.DEBT_REPAY_IN,
                directionChanged = true,
                outgoing = false,
                balanceMinor = -100_00L,
            ),
        )
    }

    @Test
    fun `удалённая транзакция в остатки не попадает`() {
        val rows = deltas(
            transactions = listOf(tx(TxType.DEBT_LEND, amountMinor = 100_000L).copy(deleted = true)),
        )

        assertTrue(DebtCalculator.balances(rows).isEmpty())
    }

    @Test
    fun `начальный остаток задаёт базу до операций`() {
        val rows = deltas(
            adjustments = listOf(adjustment(amountMinor = 500_000L)),
            transactions = listOf(tx(TxType.DEBT_LEND, amountMinor = 100_000L)),
        )

        assertEquals(600_000L, DebtCalculator.balanceOf(rows, "c1", "RUB"))
    }

    @Test
    fun `отрицательный начальный остаток означает мой долг`() {
        val rows = deltas(adjustments = listOf(adjustment(amountMinor = -200_000L)))

        assertEquals(-200_000L, DebtCalculator.balanceOf(rows, "c1", "RUB"))
        assertFalse(DebtCalculator.balances(rows).single().theyOwe)
    }

    @Test
    fun `валюты одного контрагента считаются раздельно`() {
        val rows = deltas(
            transactions = listOf(
                tx(TxType.DEBT_LEND, amountMinor = 100_000L),
                tx(TxType.DEBT_BORROW, accountId = "usd-acc", amountMinor = 5_000L),
            ),
        )

        val balances = DebtCalculator.balances(rows)
        assertEquals(2, balances.size)
        assertEquals(100_000L, DebtCalculator.balanceOf(rows, "c1", "RUB"))
        assertEquals(-5_000L, DebtCalculator.balanceOf(rows, "c1", "USD"))
    }

    @Test
    fun `kind выводится из направления и знака остатка`() {
        // Деньги из счёта: положительный остаток — новый долг, минус — погашение.
        assertEquals(TxType.DEBT_LEND, DebtCalculator.kindFor(outgoing = true, balanceMinor = 100_00L))
        assertEquals(TxType.DEBT_REPAY_OUT, DebtCalculator.kindFor(outgoing = true, balanceMinor = -100_00L))
        // Деньги на счёт: остаток в вашу пользу — полученное погашение, иначе — новый долг.
        assertEquals(TxType.DEBT_REPAY_IN, DebtCalculator.kindFor(outgoing = false, balanceMinor = 100_00L))
        assertEquals(TxType.DEBT_BORROW, DebtCalculator.kindFor(outgoing = false, balanceMinor = 0L))
        assertEquals(TxType.DEBT_BORROW, DebtCalculator.kindFor(outgoing = false, balanceMinor = -100_00L))
    }

    @Test
    fun `направление денег совпадает с kind`() {
        assertTrue(DebtCalculator.isOutgoing(TxType.DEBT_LEND))
        assertTrue(DebtCalculator.isOutgoing(TxType.DEBT_REPAY_OUT))
        assertFalse(DebtCalculator.isOutgoing(TxType.DEBT_BORROW))
        assertFalse(DebtCalculator.isOutgoing(TxType.DEBT_REPAY_IN))
    }

    @Test
    fun `префил погашения — модуль остатка`() {
        assertEquals(120_000L, DebtCalculator.repaymentAmount(120_000L))
        assertEquals(80_000L, DebtCalculator.repaymentAmount(-80_000L))
        assertEquals(0L, DebtCalculator.repaymentAmount(0L))
    }

    @Test
    fun `смена знака видна только в перепогашении`() {
        // Гасил долг: минус к нулю — закрытие, а не смена знака.
        assertFalse(DebtCalculator.flipsSign(before = -100_00L, after = 0L))
        assertTrue(DebtCalculator.flipsSign(before = -100_00L, after = 30_00L))
        // Гасили его долг: плюс через ноль в минус.
        assertTrue(DebtCalculator.flipsSign(before = 100_00L, after = -30_00L))
        assertFalse(DebtCalculator.flipsSign(before = 0L, after = -30_00L))
    }

    @Test
    fun `срок просрочен только после вчерашнего дня`() {
        val todayStart = 1_000_000L
        assertTrue(DebtCalculator.isOverdue(dueDate = 999_999L, todayStartMillis = todayStart))
        // День, назначенный на сегодня, ещё не просрочен.
        assertFalse(DebtCalculator.isOverdue(dueDate = 1_000_000L, todayStartMillis = todayStart))
    }

    @Test
    fun `не-долговые операции в остатки долга не попадают`() {
        val rows = deltas(
            transactions = listOf(
                tx(TxType.EXPENSE, amountMinor = 500L),
                tx(TxType.INCOME, amountMinor = 700L),
                tx(TxType.TRANSFER_OUT, amountMinor = 900L),
            ),
        )

        assertTrue(DebtCalculator.balances(rows).isEmpty())
    }

    @Test
    fun `транзакция без контрагента пропускается`() {
        val rows = deltas(transactions = listOf(tx(TxType.DEBT_LEND, counterpartyId = "")))

        assertTrue(DebtCalculator.balances(rows).isEmpty())
    }

    @Test
    fun `удалённая корректировка в остатки не попадает`() {
        val deleted = adjustment(amountMinor = 100_000L).copy(deleted = true)

        assertTrue(DebtCalculator.adjustmentDeltas(listOf(deleted)).isEmpty())
    }

    @Test
    fun `остатки группируются по контрагентам`() {
        val rows = deltas(
            transactions = listOf(
                tx(TxType.DEBT_LEND, counterpartyId = "c1", amountMinor = 100_000L),
                tx(TxType.DEBT_BORROW, counterpartyId = "c2", amountMinor = 40_000L),
            ),
        )

        val balances = DebtCalculator.balances(rows)
        assertEquals(
            listOf(
                Balance("c1", "RUB", 100_000L),
                Balance("c2", "RUB", -40_000L),
            ),
            balances,
        )
    }
}
