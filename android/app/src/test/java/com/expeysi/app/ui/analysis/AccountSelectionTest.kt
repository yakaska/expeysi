package com.expeysi.app.ui.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountSelectionTest {

    @Test
    fun `выбор конкретного счёта со всех счетов даёт только его`() {
        assertEquals(setOf("a"), AccountSelection.toggle(null, "a"))
    }

    @Test
    fun `дальнейшие тапы меняют набор как есть`() {
        assertEquals(setOf("a", "b"), AccountSelection.toggle(setOf("a"), "b"))
        assertEquals(setOf("b"), AccountSelection.toggle(setOf("a", "b"), "a"))
    }

    @Test
    fun `ручной выбор всех счетов остаётся явным набором`() {
        assertEquals(setOf("a", "b", "c"), AccountSelection.toggle(setOf("a", "b"), "c"))
    }

    @Test
    fun `снятие последнего выбранного счёта даёт пустой выбор`() {
        assertEquals(emptySet<String>(), AccountSelection.toggle(setOf("a"), "a"))
    }
}
