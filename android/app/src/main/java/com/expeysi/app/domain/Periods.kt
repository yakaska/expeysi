package com.expeysi.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class PeriodBounds(val startMillis: Long, val endMillisExclusive: Long)

object Periods {

    fun day(date: LocalDate, zone: ZoneId): PeriodBounds = bounds(
        from = date,
        toExclusive = date.plusDays(1),
        zone = zone,
    )

    /** Неделя начинается с понедельника. */
    fun week(date: LocalDate, zone: ZoneId): PeriodBounds {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return bounds(from = monday, toExclusive = monday.plusWeeks(1), zone = zone)
    }

    fun month(date: LocalDate, zone: ZoneId): PeriodBounds {
        val first = date.withDayOfMonth(1)
        return bounds(from = first, toExclusive = first.plusMonths(1), zone = zone)
    }

    private fun bounds(from: LocalDate, toExclusive: LocalDate, zone: ZoneId) = PeriodBounds(
        startMillis = from.atStartOfDay(zone).toInstant().toEpochMilli(),
        endMillisExclusive = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}
