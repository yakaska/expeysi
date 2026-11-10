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
    fun `листать можно только в календарных месяцах`() {
        assertTrue(AnalysisPeriod(month = YearMonth.of(2026, 4)).monthsNavigable)
        assertFalse(AnalysisPeriod(kind = AnalysisPeriodKind.SLIDING, sliding = HistoryPeriod.ALL).monthsNavigable)
        assertFalse(AnalysisPeriod(kind = AnalysisPeriodKind.RANGE).monthsNavigable)
    }

    @Test
    fun `операции раньше опорного месяца дают стрелку назад`() {
        val earliest = LocalDate.of(2026, 2, 10)
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertTrue(hasOperationsInPreviousMonth(YearMonth.of(2026, 4), earliest, zone))
    }

    @Test
    fun `операции внутри опорного месяца не дают стрелку назад`() {
        val earliest = LocalDate.of(2026, 4, 1)
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertFalse(hasOperationsInPreviousMonth(YearMonth.of(2026, 4), earliest, zone))
    }

    @Test
    fun `без операций стрелка назад недоступна`() {
        assertFalse(hasOperationsInPreviousMonth(YearMonth.of(2026, 4), null, zone))
    }
}
