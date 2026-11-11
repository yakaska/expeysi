package com.expeysi.app.ui.analysis

import com.expeysi.app.domain.DateRangeFilter
import com.expeysi.app.domain.HistoryPeriod
import com.expeysi.app.domain.Periods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class AnalysisPeriodBoundsTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: ZonedDateTime = ZonedDateTime.now(zone)

    @Test
    fun `календарный месяц даёт границы месяца`() {
        val month = YearMonth.of(2026, 4)
        val expected = Periods.month(month.atDay(1), zone)
        val (start, end) = AnalysisPeriod(month = month).bounds(now, zone)
        assertEquals(expected.startMillis, start)
        assertEquals(expected.endMillisExclusive, end)
    }

    @Test
    fun `скользящий период ограничивает только начало`() {
        val (start, end) = AnalysisPeriod(
            kind = AnalysisPeriodKind.SLIDING,
            sliding = HistoryPeriod.WEEK,
        ).bounds(now, zone)
        assertEquals(HistoryPeriod.WEEK.startMillis(now), start)
        assertNull(end)
    }

    @Test
    fun `скользящее окно в днях даёт границы как на главном экране`() {
        for (days in SLIDING_DAYS) {
            // Начало окна — начало суток дней назад, как у Windows.sums на главном экране.
            val expectedStart = now.toLocalDate()
                .minusDays((days - 1).toLong())
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            val expectedEnd = if (days == 1) {
                Periods.day(now.toLocalDate(), zone).endMillisExclusive
            } else {
                null
            }
            val (start, end) = slidingDaysPeriod(days).bounds(now, zone)
            assertEquals("окно $days дней: начало", expectedStart, start)
            assertEquals("окно $days дней: конец", expectedEnd, end)
        }
    }

    @Test
    fun `диапазон дат даёт обе границы`() {
        val range = DateRangeFilter.ofDays(
            LocalDate.of(2026, 4, 12),
            LocalDate.of(2026, 4, 18),
            zone,
        )
        val (start, end) = AnalysisPeriod(
            kind = AnalysisPeriodKind.RANGE,
            range = range,
        ).bounds(now, zone)
        assertEquals(range.startMillis, start)
        assertEquals(range.endMillisExclusive, end)
    }

    @Test
    fun `диапазон без значения не ограничивает`() {
        val (start, end) = AnalysisPeriod(kind = AnalysisPeriodKind.RANGE).bounds(now, zone)
        assertNull(start)
        assertNull(end)
    }

    @Test
    fun `календарные единицы дают границы содержащего периода`() {
        val anchor = LocalDate.of(2026, 11, 11)
        val units = listOf(
            HistoryPeriod.DAY,
            HistoryPeriod.WEEK,
            HistoryPeriod.QUARTER,
            HistoryPeriod.HALF_YEAR,
            HistoryPeriod.YEAR,
        )
        for (unit in units) {
            val expected = Periods.unit(unit, anchor, zone)
            val (start, end) = AnalysisPeriod(
                kind = AnalysisPeriodKind.CALENDAR,
                unit = unit,
                anchor = anchor,
            ).bounds(now, zone)
            assertEquals(unit.name, expected.startMillis, start)
            assertEquals(unit.name, expected.endMillisExclusive, end)
        }
    }

    @Test
    fun `листать можно только в календарных режимах`() {
        assertTrue(AnalysisPeriod(month = YearMonth.of(2026, 4)).navigable)
        assertTrue(
            AnalysisPeriod(
                kind = AnalysisPeriodKind.CALENDAR,
                unit = HistoryPeriod.WEEK,
                anchor = LocalDate.of(2026, 4, 12),
            ).navigable,
        )
        assertFalse(AnalysisPeriod(kind = AnalysisPeriodKind.SLIDING, sliding = HistoryPeriod.ALL).navigable)
        assertFalse(slidingDaysPeriod(7).navigable)
        assertFalse(AnalysisPeriod(kind = AnalysisPeriodKind.RANGE).navigable)
    }

    @Test
    fun `сдвиг квартала двигает опорную дату на квартал`() {
        val period = AnalysisPeriod(
            kind = AnalysisPeriodKind.CALENDAR,
            unit = HistoryPeriod.QUARTER,
            anchor = LocalDate.of(2026, 2, 15),
        )
        val shifted = period.shiftedBy(1)!!
        assertEquals(LocalDate.of(2026, 5, 15), shifted.anchor)
        val expected = Periods.quarter(LocalDate.of(2026, 5, 15), zone)
        assertEquals(expected.startMillis, shifted.bounds(now, zone).first)
    }

    @Test
    fun `сдвиг недели пересекает год`() {
        val period = AnalysisPeriod(
            kind = AnalysisPeriodKind.CALENDAR,
            unit = HistoryPeriod.WEEK,
            anchor = LocalDate.of(2026, 1, 1),
        )
        val shifted = period.shiftedBy(-1)!!
        val expected = Periods.week(LocalDate.of(2025, 12, 25), zone)
        assertEquals(expected.startMillis, shifted.bounds(now, zone).first)
    }

    @Test
    fun `вперёд недоступно в текущем периоде и доступно в прошлом`() {
        val today = LocalDate.from(now)
        val current = AnalysisPeriod(
            kind = AnalysisPeriodKind.CALENDAR,
            unit = HistoryPeriod.QUARTER,
            anchor = today,
        )
        assertFalse(current.hasNext(now, zone))
        val past = current.shiftedBy(-2)!!
        assertTrue(past.hasNext(now, zone))
    }

    @Test
    fun `вперёд для месяцев не выходит за текущий месяц`() {
        val currentMonth = YearMonth.from(now)
        assertFalse(AnalysisPeriod(month = currentMonth).hasNext(now, zone))
        assertTrue(AnalysisPeriod(month = currentMonth.minusMonths(1)).hasNext(now, zone))
    }

    @Test
    fun `операции раньше опорного месяца дают стрелку назад`() {
        val earliest = LocalDate.of(2026, 2, 10)
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertTrue(AnalysisPeriod(month = YearMonth.of(2026, 4)).hasPrevious(now, zone, earliest))
    }

    @Test
    fun `операции внутри опорного периода не дают стрелку назад`() {
        // 8 ноября 2026 — воскресенье; его неделя — со 2 по 8 ноября.
        val anchor = LocalDate.of(2026, 11, 8)
        val period = AnalysisPeriod(
            kind = AnalysisPeriodKind.CALENDAR,
            unit = HistoryPeriod.WEEK,
            anchor = anchor,
        )
        val insideWeek = anchor
            .minusDays(3)
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertFalse(period.hasPrevious(now, zone, insideWeek))
        val previousWeek = anchor
            .minusDays(8)
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertTrue(period.hasPrevious(now, zone, previousWeek))
    }

    @Test
    fun `без операций стрелка назад недоступна`() {
        assertFalse(AnalysisPeriod(month = YearMonth.of(2026, 4)).hasPrevious(now, zone, null))
    }
}
