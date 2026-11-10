package com.expeysi.app.ui.analysis

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.expeysi.app.R
import com.expeysi.app.domain.DateRangeFilter
import com.expeysi.app.domain.HistoryPeriod
import com.expeysi.app.domain.Periods
import com.expeysi.app.ui.common.RuDates
import com.expeysi.app.ui.history.labelRes
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class AnalysisPeriodKind { MONTHS, SLIDING, RANGE }

private val shortRangeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

/**
 * Период анализа. MONTHS — календарные месяцы с навигацией по стрелкам;
 * SLIDING и RANGE выбираются пикером с тем же набором, что и в отчётах.
 */
data class AnalysisPeriod(
    val kind: AnalysisPeriodKind = AnalysisPeriodKind.MONTHS,
    /** Опорный календарный месяц для режима MONTHS. */
    val month: YearMonth = YearMonth.now(),
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
        AnalysisPeriodKind.SLIDING -> sliding.startMillis(now) to null
        AnalysisPeriodKind.RANGE -> {
            if (range == null) {
                null to null
            } else {
                range.startMillis to range.endMillisExclusive
            }
        }
    }

    /** Режим календарных месяцев позволяет листать по месяцам. */
    val monthsNavigable: Boolean
        get() = kind == AnalysisPeriodKind.MONTHS
}

/** Есть ли операции у месяца перед [month]: по самой ранней операции баланса. */
internal fun hasOperationsInPreviousMonth(
    month: YearMonth,
    earliestMillis: Long?,
    zone: ZoneId,
): Boolean {
    if (earliestMillis == null) return false
    val previousEnd = month.minusMonths(1).atDay(1)
        .atStartOfDay(zone)
        .plusMonths(1)
        .toInstant()
        .toEpochMilli()
    return earliestMillis < previousEnd
}

/** Метка периода в центре диаграммы: «Апрель 2026», «Неделя», «12.04–18.04»… */
@Composable
internal fun AnalysisPeriod.centerLabel(zone: ZoneId): String = when (kind) {
    AnalysisPeriodKind.MONTHS -> RuDates.monthLabel(month, currentYear = YearMonth.now().year)
    AnalysisPeriodKind.SLIDING -> stringResource(sliding.labelRes())
    AnalysisPeriodKind.RANGE -> range?.let { RuDates.shortRangeLabel(it, zone) }
        ?: stringResource(HistoryPeriod.ALL.labelRes())
}

/** Метка периода вне композиции: для параметров навигации. */
internal fun AnalysisPeriod.linkLabel(zone: ZoneId, getString: (Int) -> String): String = when (kind) {
    AnalysisPeriodKind.MONTHS -> RuDates.monthLabel(month, currentYear = YearMonth.now().year)
    AnalysisPeriodKind.SLIDING -> getString(sliding.labelRes())
    AnalysisPeriodKind.RANGE -> range?.let { RuDates.shortRangeLabel(it, zone) }
        ?: getString(HistoryPeriod.ALL.labelRes())
}

/** Часть описания доступности: «апрель», «за неделю», «за всё время»… */
@Composable
internal fun AnalysisPeriod.sinceLabel(): String = when (kind) {
    AnalysisPeriodKind.MONTHS -> RuDates.monthName(month)
    AnalysisPeriodKind.SLIDING -> stringResource(sliding.sinceRes())
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
