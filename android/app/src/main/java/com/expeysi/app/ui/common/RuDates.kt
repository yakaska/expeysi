package com.expeysi.app.ui.common

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Русские подписи дат для отчётов. */
object RuDates {

    private val RU = Locale.forLanguageTag("ru")

    /** Заголовок дня группы: «10 апреля, пятница». */
    fun dayHeader(date: LocalDate, locale: Locale = RU): String = date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", locale))

    /** Название месяца в нижнем регистре: «апрель». */
    fun monthName(month: YearMonth, locale: Locale = RU): String = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)

    /** Подпись периода в центре диаграммы: «Апрель» или «Апрель 2025». */
    fun monthLabel(month: YearMonth, currentYear: Int, locale: Locale = RU): String {
        val name = monthName(month, locale).replaceFirstChar { it.uppercase(locale) }
        return if (month.year == currentYear) name else "$name ${month.year}"
    }
}
