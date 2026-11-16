package com.expeysi.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DeviceTimeTest {

    private val ru: Locale = Locale.forLanguageTag("ru")

    @Test
    fun `настройка 24 задана - HH mm в обеих локалях`() {
        assertEquals("09:05", formatTime(9, 5, "24", ru))
        assertEquals("21:30", formatTime(21, 30, "24", ru))
        assertEquals("09:05", formatTime(9, 5, "24", Locale.US))
        assertEquals("21:30", formatTime(21, 30, "24", Locale.US))
    }

    @Test
    fun `настройка 12 задана в англоязычной локали - маркер AM PM`() {
        assertEquals("9:05 AM", formatTime(9, 5, "12", Locale.US))
        assertEquals("9:30 PM", formatTime(21, 30, "12", Locale.US))
    }

    @Test
    fun `настройка 12 задана в ру локали - 12-часовая запись часа`() {
        // Маркер AM/PM в русской локали зависит от версии CLDR, поэтому проверяем
        // только число часа: оно обязано уйти из 24-часовой записи в 12-часовую.
        assertTrue(formatTime(9, 5, "12", ru).startsWith("9:05"))
        assertTrue(formatTime(21, 30, "12", ru).startsWith("9:30"))
    }

    @Test
    fun `настройка не задана - ру локаль 24-часовая`() {
        assertEquals("09:05", formatTime(9, 5, null, ru))
        assertEquals("21:30", formatTime(21, 30, null, ru))
    }

    @Test
    fun `настройка не задана - en локаль 12-часовая`() {
        // Какой пробел поставит локализованный шаблон перед маркером (U+0020 или
        // U+202F) зависит от версии CLDR в окружении, приводим к обычному пробелу.
        val morning = formatTime(9, 5, null, Locale.US).replace('\u202F', ' ')
        val evening = formatTime(21, 30, null, Locale.US).replace('\u202F', ' ')
        assertEquals("9:05 AM", morning)
        assertEquals("9:30 PM", evening)
    }
}
