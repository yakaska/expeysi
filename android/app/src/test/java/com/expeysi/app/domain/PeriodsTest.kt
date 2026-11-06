package com.expeysi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PeriodsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    @Test
    fun `неделя начинается с понедельника для среды`() {
        // 15.01.2025 — среда
        val bounds = Periods.week(LocalDate.of(2025, 1, 15), zone)
        val start = Instant.ofEpochMilli(bounds.startMillis).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(bounds.endMillisExclusive).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2025, 1, 13), start)
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals(LocalDate.of(2025, 1, 20), end)
    }

    @Test
    fun `понедельник относится к своей неделе а воскресенье к предыдущей`() {
        val monday = Periods.week(LocalDate.of(2025, 1, 13), zone)
        val sunday = Periods.week(LocalDate.of(2025, 1, 19), zone)
        assertEquals(monday, sunday)
    }

    @Test
    fun `месяц это полуинтервал от первого числа`() {
        val bounds = Periods.month(LocalDate.of(2025, 1, 31), zone)
        val start = Instant.ofEpochMilli(bounds.startMillis).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(bounds.endMillisExclusive).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2025, 1, 1), start)
        assertEquals(LocalDate.of(2025, 2, 1), end)
    }

    @Test
    fun `день это полуинтервал суток`() {
        val bounds = Periods.day(LocalDate.of(2025, 1, 15), zone)
        assertEquals(24 * 60 * 60 * 1000L, bounds.endMillisExclusive - bounds.startMillis)
    }
}
