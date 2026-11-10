package com.expeysi.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class RuDatesTest {

    private val ru = Locale.forLanguageTag("ru")

    @Test
    fun `месяц текущего года без года`() {
        assertEquals("Апрель", RuDates.monthLabel(YearMonth.of(2026, 4), currentYear = 2026, ru))
    }

    @Test
    fun `месяц другого года с годом`() {
        assertEquals("Апрель 2025", RuDates.monthLabel(YearMonth.of(2025, 4), currentYear = 2026, ru))
    }

    @Test
    fun `название месяца в нижнем регистре`() {
        assertEquals("апрель", RuDates.monthName(YearMonth.of(2026, 4), ru))
        assertEquals("май", RuDates.monthName(YearMonth.of(2026, 5), ru))
    }

    @Test
    fun `заголовок дня содержит число месяц и день недели`() {
        val date = LocalDate.of(2026, 4, 3)
        assertEquals("3 апреля, пятница", RuDates.dayHeader(date, ru))
    }
}
