package com.expeysi.app.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Скользящие периоды истории: граница считается от текущего момента назад. */
enum class HistoryPeriod {
    DAY, WEEK, MONTH, QUARTER, HALF_YEAR, YEAR, ALL;

    /** Нижняя граница включительно; null — без ограничения (всё время). */
    fun startMillis(now: ZonedDateTime): Long? = when (this) {
        DAY -> now.minusDays(1)
        WEEK -> now.minusWeeks(1)
        MONTH -> now.minusMonths(1)
        QUARTER -> now.minusMonths(3)
        HALF_YEAR -> now.minusMonths(6)
        YEAR -> now.minusYears(1)
        ALL -> null
    }?.toInstant()?.toEpochMilli()
}

/** Произвольный диапазон дат: от начала первого дня до конца последнего. */
data class DateRangeFilter(val startMillis: Long, val endMillisExclusive: Long) {
    companion object {
        fun ofDays(start: LocalDate, endInclusive: LocalDate, zone: ZoneId): DateRangeFilter =
            DateRangeFilter(
                startMillis = Periods.day(start, zone).startMillis,
                endMillisExclusive = Periods.day(endInclusive, zone).endMillisExclusive,
            )
    }
}
