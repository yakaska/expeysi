package com.expeysi.app.ui.analysis

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.expeysi.app.R
import com.expeysi.app.domain.DateRangeFilter
import com.expeysi.app.domain.HistoryPeriod
import com.expeysi.app.domain.Periods
import com.expeysi.app.ui.common.RuDates
import com.expeysi.app.ui.history.labelRes
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class AnalysisPeriodKind { MONTHS, CALENDAR, SLIDING, RANGE }

private val shortRangeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

/**
 * Период анализа. MONTHS — календарные месяцы, CALENDAR — календарные дни,
 * недели, кварталы, полугодия и годы; оба листаются по стрелкам. SLIDING и
 * RANGE выбираются пикером и не листаются.
 */
data class AnalysisPeriod(
    val kind: AnalysisPeriodKind = AnalysisPeriodKind.MONTHS,
    /** Опорный календарный месяц для режима MONTHS. */
    val month: YearMonth = YearMonth.now(),
    /** Единица календарного режима CALENDAR (кроме месяцев и «всего времени»). */
    val unit: HistoryPeriod = HistoryPeriod.DAY,
    /** Опорный день режима CALENDAR: дата внутри листаемого периода. */
    val anchor: LocalDate = LocalDate.now(),
    /** Скользящий период из пикера для режима SLIDING. */
    val sliding: HistoryPeriod = HistoryPeriod.MONTH,
    /** Произвольный диапазон для режима RANGE. */
    val range: DateRangeFilter? = null,
) {
    /** Границы выборки; (null, null) — без ограничений. */
    fun bounds(now: ZonedDateTime, zone: ZoneId): Pair<Long?, Long?> = when (kind) {
        AnalysisPeriodKind.MONTHS -> {
            val bounds = Periods.month(month.atDay(1), zone)
            bounds.startMillis to bounds.endMillisExclusive
        }
        AnalysisPeriodKind.CALENDAR -> {
            val bounds = Periods.unit(unit, anchor, zone)
            bounds.startMillis to bounds.endMillisExclusive
        }
        AnalysisPeriodKind.SLIDING -> sliding.startMillis(now) to null
        AnalysisPeriodKind.RANGE -> {
            if (range == null) {
                null to null
            } else {
                range.startMillis to range.endMillisExclusive
            }
        }
    }

    /** Календарные периоды (месяцы и прочие единицы) листаются по стрелкам. */
    val navigable: Boolean
        get() = kind == AnalysisPeriodKind.MONTHS || kind == AnalysisPeriodKind.CALENDAR

    /** Период, смещённый на [delta] листаемых единиц; null — режим не листается. */
    fun shiftedBy(delta: Int): AnalysisPeriod? = when (kind) {
        AnalysisPeriodKind.MONTHS -> copy(month = month.plusMonths(delta.toLong()))
        AnalysisPeriodKind.CALENDAR -> copy(anchor = unit.shiftAnchor(anchor, delta))
        else -> null
    }

    /** Стрелка «вперёд»: следующий период не должен уходить в будущее. */
    fun hasNext(now: ZonedDateTime, zone: ZoneId): Boolean {
        val next = shiftedBy(1) ?: return false
        val nextStart = next.bounds(now, zone).first ?: return false
        return nextStart <= todayPeriodStart(now, zone)
    }

    /** Стрелка «назад»: до текущего периода были операции. */
    fun hasPrevious(now: ZonedDateTime, zone: ZoneId, earliestMillis: Long?): Boolean {
        if (shiftedBy(-1) == null || earliestMillis == null) return false
        val currentStart = bounds(now, zone).first ?: return false
        return earliestMillis < currentStart
    }

    /** Начало календарного периода, содержащего сегодня: граница листания вперёд. */
    private fun todayPeriodStart(now: ZonedDateTime, zone: ZoneId): Long = when (kind) {
        AnalysisPeriodKind.MONTHS -> now.toLocalDate().withDayOfMonth(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        AnalysisPeriodKind.CALENDAR -> Periods.unit(unit, now.toLocalDate(), zone).startMillis
        else -> 0L
    }
}

/** Опорная точка внутри календарной единицы; сдвиг на [delta] единиц. */
private fun HistoryPeriod.shiftAnchor(anchor: LocalDate, delta: Int): LocalDate = when (this) {
    HistoryPeriod.DAY -> anchor.plusDays(delta.toLong())
    HistoryPeriod.WEEK -> anchor.plusWeeks(delta.toLong())
    HistoryPeriod.QUARTER -> anchor.plusMonths(3L * delta)
    HistoryPeriod.HALF_YEAR -> anchor.plusMonths(6L * delta)
    HistoryPeriod.YEAR -> anchor.plusYears(delta.toLong())
    else -> anchor
}

/** Метка периода в центре диаграммы: «Апрель 2026», «11 ноября», «1-й квартал 2026»… */
@Composable
internal fun AnalysisPeriod.centerLabel(zone: ZoneId): String = when (kind) {
    AnalysisPeriodKind.MONTHS -> RuDates.monthLabel(month, currentYear = YearMonth.now().year)
    AnalysisPeriodKind.CALENDAR -> calendarLabel(zone)
    AnalysisPeriodKind.SLIDING -> stringResource(sliding.labelRes())
    AnalysisPeriodKind.RANGE -> range?.let { RuDates.shortRangeLabel(it, zone) }
        ?: stringResource(HistoryPeriod.ALL.labelRes())
}

/** Метка календарной единицы: «11 ноября», «9.11–15.11», «1-й квартал 2026»… */
@Composable
private fun AnalysisPeriod.calendarLabel(zone: ZoneId): String = when (unit) {
    HistoryPeriod.DAY -> RuDates.dayLabel(anchor)
    HistoryPeriod.YEAR -> anchor.year.toString()
    HistoryPeriod.QUARTER -> stringResource(
        R.string.analysis_quarter_label,
        (anchor.monthValue - 1) / 3 + 1,
        anchor.year,
    )
    HistoryPeriod.HALF_YEAR -> stringResource(
        R.string.analysis_half_year_label,
        if (anchor.monthValue <= 6) 1 else 2,
        anchor.year,
    )
    else -> calendarRangeLabel(zone)
}

/** Границы единицы диапазоном: для недели «9.11–15.11». */
private fun AnalysisPeriod.calendarRangeLabel(zone: ZoneId): String {
    val (startMillis, endMillisExclusive) = Periods.unit(unit, anchor, zone)
    return RuDates.shortRangeLabel(DateRangeFilter(startMillis, endMillisExclusive), zone)
}

/** Метка периода для параметра навигации на соседний экран. */
@Composable
internal fun AnalysisPeriod.linkLabel(zone: ZoneId): String = when (kind) {
    AnalysisPeriodKind.MONTHS -> RuDates.monthLabel(month, currentYear = YearMonth.now().year)
    AnalysisPeriodKind.CALENDAR -> calendarLabel(zone)
    AnalysisPeriodKind.SLIDING -> stringResource(sliding.labelRes())
    AnalysisPeriodKind.RANGE -> range?.let { RuDates.shortRangeLabel(it, zone) }
        ?: stringResource(HistoryPeriod.ALL.labelRes())
}

/** Часть описания доступности: «апрель», «за неделю», «за всё время»… */
@Composable
internal fun AnalysisPeriod.sinceLabel(): String = when (kind) {
    AnalysisPeriodKind.MONTHS -> RuDates.monthName(month)
    AnalysisPeriodKind.SLIDING -> stringResource(sliding.sinceRes())
    AnalysisPeriodKind.CALENDAR -> stringResource(unit.sinceRes())
    AnalysisPeriodKind.RANGE -> stringResource(R.string.analysis_range_custom)
}

private fun HistoryPeriod.sinceRes(): Int = when (this) {
    HistoryPeriod.DAY -> R.string.analysis_range_day
    HistoryPeriod.WEEK -> R.string.analysis_range_week
    HistoryPeriod.MONTH -> R.string.analysis_range_month
    HistoryPeriod.QUARTER -> R.string.analysis_range_quarter
    HistoryPeriod.HALF_YEAR -> R.string.analysis_range_half
    HistoryPeriod.YEAR -> R.string.analysis_range_year
    HistoryPeriod.ALL -> R.string.analysis_range_all
}
