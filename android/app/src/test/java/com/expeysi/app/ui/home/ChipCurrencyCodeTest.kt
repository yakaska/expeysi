package com.expeysi.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ChipCurrencyCodeTest {

    @Test
    fun `название без кода - код показываем`() {
        assertEquals("RUB", chipCurrencyCode("Семейный", "RUB"))
    }

    @Test
    fun `название содержит код - код не дублируем`() {
        assertEquals("", chipCurrencyCode("Вьетнам (VND)", "VND"))
    }

    @Test
    fun `регистр не важен`() {
        assertEquals("", chipCurrencyCode("usd pocket", "USD"))
        assertEquals("", chipCurrencyCode("Вьетнам (vnd)", "VND"))
    }

    @Test
    fun `пустое название - код показываем`() {
        assertEquals("EUR", chipCurrencyCode("", "EUR"))
    }

    @Test
    fun `пустой код - не показываем`() {
        assertEquals("", chipCurrencyCode("Семейный", ""))
    }
}
