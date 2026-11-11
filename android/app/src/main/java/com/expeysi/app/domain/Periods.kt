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

    /** Квартал: январь–март, апрель–июнь, июль–сентябрь, октябрь–декабрь. */
    fun quarter(date: LocalDate, zone: ZoneId): PeriodBounds {
        val firstMonth = (date.monthValue - 1) / 3 * 3 + 1
        val first = date.withMonth(firstMonth).withDayOfMonth(1)
        return bounds(from = first, toExclusive = first.plusMonths(3), zone = zone)
    }

    /** Полугодие: январь–июнь или июль–декабрь. */
    fun halfYear(date: LocalDate, zone: ZoneId): PeriodBounds {
        val first = date.withMonth(if (date.monthValue <= 6) 1 else 7).withDayOfMonth(1)
        return bounds(from = first, toExclusive = first.plusMonths(6), zone = zone)
    }

    fun year(date: LocalDate, zone: ZoneId): PeriodBounds {
        val first = date.withDayOfYear(1)
        return bounds(from = first, toExclusive = first.plusYears(1), zone = zone)
    }

    /** Границы календарной единицы, содержащей [date]. */
    fun unit(unit: HistoryPeriod, date: LocalDate, zone: ZoneId): PeriodBounds = when (unit) {
        HistoryPeriod.DAY -> day(date, zone)
        HistoryPeriod.WEEK -> week(date, zone)
        HistoryPeriod.MONTH -> month(date, zone)
        HistoryPeriod.QUARTER -> quarter(date, zone)
        HistoryPeriod.HALF_YEAR -> halfYear(date, zone)
        else -> year(date, zone)
    }

    private fun bounds(from: LocalDate, toExclusive: LocalDate, zone: ZoneId) = PeriodBounds(
        startMillis = from.atStartOfDay(zone).toInstant().toEpochMilli(),
        endMillisExclusive = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}
