package com.expeysi.app.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryPeriodTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 5, 12, 30, 0, 0, zone)

    private fun millis(dateTime: ZonedDateTime): Long = dateTime.toInstant().toEpochMilli()

    @Test
    fun `день — последние 24 часа`() {
        assertEquals(millis(now.minusDays(1)), HistoryPeriod.DAY.startMillis(now))
    }

    @Test
    fun `неделя — последние 7 дней`() {
        assertEquals(millis(now.minusWeeks(1)), HistoryPeriod.WEEK.startMillis(now))
    }

    @Test
    fun `месяц, квартал, полгода, год`() {
        assertEquals(millis(now.minusMonths(1)), HistoryPeriod.MONTH.startMillis(now))
        assertEquals(millis(now.minusMonths(3)), HistoryPeriod.QUARTER.startMillis(now))
        assertEquals(millis(now.minusMonths(6)), HistoryPeriod.HALF_YEAR.startMillis(now))
        assertEquals(millis(now.minusYears(1)), HistoryPeriod.YEAR.startMillis(now))
    }

    @Test
    fun `всё время — без нижней границы`() {
        assertNull(HistoryPeriod.ALL.startMillis(now))
    }

    @Test
    fun `диапазон дат включает последний день целиком`() {
        val range = DateRangeFilter.ofDays(
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31),
            zone,
        )
        assertEquals(
            millis(LocalDate.of(2026, 10, 1).atStartOfDay(zone)),
            range.startMillis,
        )
        assertEquals(
            millis(LocalDate.of(2026, 11, 1).atStartOfDay(zone)),
            range.endMillisExclusive,
        )
    }

    @Test
    fun `диапазон из одного дня`() {
        val day = LocalDate.of(2026, 11, 5)
        val range = DateRangeFilter.ofDays(day, day, zone)
        assertEquals(millis(day.atStartOfDay(zone)), range.startMillis)
        assertEquals(millis(day.plusDays(1).atStartOfDay(zone)), range.endMillisExclusive)
    }
}
