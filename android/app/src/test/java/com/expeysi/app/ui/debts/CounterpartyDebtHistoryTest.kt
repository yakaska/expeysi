package com.expeysi.app.ui.debts

import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtDueStatus
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtHistorySign
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtHistoryTone
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtOp
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtOpKind
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.closedDaysDelta
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.debtRowSemantics
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.dueStatus
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.fold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CounterpartyDebtHistoryTest {

    private val zone = ZoneId.of("Europe/Moscow")

    /** «Сегодня» свёртки: 2026-11-17. */
    private val today: LocalDate = LocalDate.of(2026, 11, 17)

    /** Дата в millis: «день N от 2026-11-17 (минус — раньше) в час:минуту». */
    private fun at(day: Long, hour: Int = 12, minute: Int = 0): Long =
        today.plusDays(day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** Локальная дата операции из millis — так статус срока получает дату из записи. */
    private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun op(
        id: String,
        date: Long,
        kind: DebtOpKind,
        deltaMinor: Long,
        currency: String = "RUB",
    ) = DebtOp(
        id = id,
        isAdjustment = kind == DebtOpKind.WRITE_OFF || kind == DebtOpKind.OPENING_BALANCE,
        date = date,
        updatedAt = date,
        currencyCode = currency,
        kind = kind,
        deltaMinor = deltaMinor,
        note = "",
    )

    /* ------------------------------- Тон и знак ------------------------------- */

    @Test
    fun `выданный долг - деньги ушли - минус и красный`() {
        val semantics = debtRowSemantics(DebtOpKind.LEND, 50_000)
        assertEquals(DebtHistoryTone.NEGATIVE, semantics.tone)
        assertEquals(DebtHistorySign.MINUS, semantics.sign)
    }

    @Test
    fun `отправленное погашение - деньги ушли - минус и красный`() {
        val semantics = debtRowSemantics(DebtOpKind.REPAY_OUT, 30_000)
        assertEquals(DebtHistoryTone.NEGATIVE, semantics.tone)
        assertEquals(DebtHistorySign.MINUS, semantics.sign)
    }

    @Test
    fun `взятый долг - деньги пришли - плюс и зелёный`() {
        val semantics = debtRowSemantics(DebtOpKind.BORROW, -50_000)
        assertEquals(DebtHistoryTone.POSITIVE, semantics.tone)
        assertEquals(DebtHistorySign.PLUS, semantics.sign)
    }

    @Test
    fun `полученное погашение - деньги пришли - плюс и зелёный`() {
        val semantics = debtRowSemantics(DebtOpKind.REPAY_IN, -30_000)
        assertEquals(DebtHistoryTone.POSITIVE, semantics.tone)
        assertEquals(DebtHistorySign.PLUS, semantics.sign)
    }

    @Test
    fun `списание - наличных нет - нейтрально и без знака`() {
        val semantics = debtRowSemantics(DebtOpKind.WRITE_OFF, 50_000)
        assertEquals(DebtHistoryTone.NEUTRAL, semantics.tone)
        assertNull(semantics.sign)
    }

    @Test
    fun `начальный остаток - наличных нет - нейтрально, знак по вкладу`() {
        assertEquals(
            DebtHistorySign.PLUS,
            debtRowSemantics(DebtOpKind.OPENING_BALANCE, 50_000).sign,
        )
        assertEquals(
            DebtHistorySign.MINUS,
            debtRowSemantics(DebtOpKind.OPENING_BALANCE, -50_000).sign,
        )
        assertEquals(
            DebtHistoryTone.NEUTRAL,
            debtRowSemantics(DebtOpKind.OPENING_BALANCE, 50_000).tone,
        )
    }

    /* ----------------------------- Бегущий остаток ---------------------------- */

    @Test
    fun `бегущий остаток складывается по хронологии и показывается от нового к старому`() {
        // 100 000 -> 50 000 -> 0 в хронологии, в списке наоборот.
        val foldResult = fold(
            listOf(
                op("repay-2", at(0, 18), DebtOpKind.REPAY_IN, -50_000),
                op("repay-1", at(0, 10), DebtOpKind.REPAY_IN, -50_000),
                op("lend", at(-1), DebtOpKind.LEND, 100_000),
            ),
            today,
            zone,
        )

        assertEquals(listOf("repay-2", "repay-1", "lend"), foldResult.rows.map { it.op.id })
        assertEquals(listOf(0L, 50_000L, 100_000L), foldResult.rows.map { it.runningBalanceMinor })
    }

    @Test
    fun `бегущий остаток ведётся по каждой валюте отдельно`() {
        val foldResult = fold(
            listOf(
                op("lend-rub", at(-2), DebtOpKind.LEND, 100_000, currency = "RUB"),
                op("lend-usd", at(-1), DebtOpKind.LEND, 5_000, currency = "USD"),
                op("repay-usd", at(0), DebtOpKind.REPAY_IN, -3_000, currency = "USD"),
            ),
            today,
            zone,
        )

        assertEquals(
            mapOf(
                "repay-usd" to 2_000L,
                "lend-usd" to 5_000L,
                "lend-rub" to 100_000L,
            ),
            foldResult.rows.associate { it.op.id to it.runningBalanceMinor },
        )
    }

    @Test
    fun `начальный остаток задаёт остаток, списание уменьшает`() {
        val foldResult = fold(
            listOf(
                op("write-off", at(0), DebtOpKind.WRITE_OFF, 70_000),
                op("borrow", at(-1), DebtOpKind.BORROW, -20_000),
                op("opening", at(-3), DebtOpKind.OPENING_BALANCE, -50_000),
            ),
            today,
            zone,
        )

        // Хронология: -50 000 -> -70 000 -> 0 (долг мой, списание его закрывает).
        assertEquals(
            listOf(0L, -70_000L, -50_000L),
            foldResult.rows.map { it.runningBalanceMinor },
        )
    }

    /* ------------------------------ Дата закрытия ----------------------------- */

    @Test
    fun `полное погашение задаёт дату закрытия`() {
        val foldResult = fold(
            listOf(
                op("repay", at(2), DebtOpKind.REPAY_IN, -100_000),
                op("lend", at(0), DebtOpKind.LEND, 100_000),
            ),
            today,
            zone,
        )

        val summary = foldResult.summaries.single()
        assertEquals(0L, summary.balanceMinor)
        assertEquals(at(2), summary.closingDate)
        assertEquals("repay", summary.closingOp?.id)
        assertEquals(at(2), foldResult.closingDate)
        assertEquals(at(0), foldResult.firstDate)
        assertEquals(100_000L, summary.lentMinor)
        assertEquals(100_000L, summary.repaidInMinor)
        assertEquals(0L, summary.borrowedMinor)
        assertEquals(0L, summary.repaidOutMinor)
    }

    @Test
    fun `частичное погашение не задаёт дату закрытия`() {
        val foldResult = fold(
            listOf(
                op("repay", at(2), DebtOpKind.REPAY_IN, -50_000),
                op("lend", at(0), DebtOpKind.LEND, 100_000),
            ),
            today,
            zone,
        )

        val summary = foldResult.summaries.single()
        assertEquals(50_000L, summary.balanceMinor)
        assertNull(summary.closingOp)
        assertNull(summary.closingDate)
        assertNull(foldResult.closingDate)
    }

    @Test
    fun `повторное закрытие берёт последнюю обнулившую операцию`() {
        val foldResult = fold(
            listOf(
                // Я должен: погашение отправлено — деньги ушли, вклад в остаток плюс.
                op("settle-2", at(4), DebtOpKind.REPAY_OUT, 10_000),
                op("borrow", at(3), DebtOpKind.BORROW, -10_000),
                // Мне должны: полученное погашение — деньги пришли, вклад минус.
                op("settle-1", at(2), DebtOpKind.REPAY_IN, -50_000),
                op("lend", at(0), DebtOpKind.LEND, 50_000),
            ),
            today,
            zone,
        )

        assertEquals(0L, foldResult.summaries.single().balanceMinor)
        assertEquals("settle-2", foldResult.summaries.single().closingOp?.id)
        assertEquals(at(4), foldResult.summaries.single().closingDate)
    }

    @Test
    fun `начальный остаток и списание тоже бывают закрывающей парой`() {
        val foldResult = fold(
            listOf(
                op("write-off", at(1), DebtOpKind.WRITE_OFF, 70_000),
                op("opening", at(0, 9), DebtOpKind.OPENING_BALANCE, -70_000),
            ),
            today,
            zone,
        )

        val summary = foldResult.summaries.single()
        assertEquals(0L, summary.balanceMinor)
        assertEquals("write-off", summary.closingOp?.id)
        assertEquals(at(1), summary.closingDate)
    }

    @Test
    fun `сводки по валютам сортируются по модулю остатка`() {
        val foldResult = fold(
            listOf(
                op("lend-usd", at(-1), DebtOpKind.LEND, 5_000, currency = "USD"),
                op("lend-rub", at(0), DebtOpKind.LEND, 100_000, currency = "RUB"),
            ),
            today,
            zone,
        )

        assertEquals(listOf("RUB", "USD"), foldResult.summaries.map { it.currencyCode })
        assertEquals(listOf(100_000L, 5_000L), foldResult.summaries.map { it.balanceMinor })
    }

    /* ---------------------------- Раньше или позже ---------------------------- */

    @Test
    fun `закрытие в день срока - ноль дней`() {
        val due = LocalDate.of(2026, 11, 10)
        assertEquals(0, closedDaysDelta(closingDate = LocalDate.of(2026, 11, 10), dueDate = due))
    }

    @Test
    fun `закрытие после срока - дни опоздания`() {
        val due = LocalDate.of(2026, 11, 10)
        assertEquals(3, closedDaysDelta(closingDate = LocalDate.of(2026, 11, 13), dueDate = due))
    }

    @Test
    fun `закрытие раньше срока - дни с минусом`() {
        val due = LocalDate.of(2026, 11, 10)
        assertEquals(-5, closedDaysDelta(closingDate = LocalDate.of(2026, 11, 5), dueDate = due))
    }

    @Test
    fun `статус срока закрытого долга берёт дату закрытия`() {
        val status = dueStatus(
            dueDate = LocalDate.of(2026, 11, 10),
            closingDate = LocalDate.of(2026, 11, 13),
            today = today,
        )
        assertEquals(DebtDueStatus.Closed(3), status)
    }

    @Test
    fun `статус срока открытого долга с просрочкой`() {
        val status = dueStatus(
            dueDate = dateOf(at(-4, 0, 0)),
            closingDate = null,
            today = today,
        )
        assertEquals(DebtDueStatus.Open(overdueDays = 4), status)
    }

    @Test
    fun `статус срока открытого долга без просрочки`() {
        val status = dueStatus(
            dueDate = dateOf(at(4, 0, 0)),
            closingDate = null,
            today = today,
        )
        assertEquals(DebtDueStatus.Open(overdueDays = 0), status)
    }

    @Test
    fun `полное закрытие нескольких валют берёт последнее обнуление`() {
        val foldResult = fold(
            listOf(
                op("lend-usd", at(-1), DebtOpKind.LEND, 5_000, currency = "USD"),
                op("repay-usd", at(1), DebtOpKind.REPAY_IN, -5_000, currency = "USD"),
                op("lend-rub", at(0), DebtOpKind.LEND, 100_000, currency = "RUB"),
                op("repay-rub", at(2), DebtOpKind.REPAY_IN, -100_000, currency = "RUB"),
            ),
            today,
            zone,
        )

        assertEquals(at(2), foldResult.closingDate)
        assertEquals(at(-1), foldResult.firstDate)
    }
}
