package com.expeysi.app.ui.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSelectionTest {

    private val allIds = setOf("a", "b", "c")

    @Test
    fun `выбор конкретного счёта со всех счетов даёт только его`() {
        assertEquals(setOf("a"), AccountSelection.toggle(null, allIds, "a"))
    }

    @Test
    fun `снятие последнего выбранного счёта даёт пустой выбор`() {
        assertEquals(emptySet<String>(), AccountSelection.toggle(setOf("a"), allIds, "a"))
    }

    @Test
    fun `добавление последнего счёта сводится к все счета`() {
        assertNull(AccountSelection.toggle(setOf("b", "c"), allIds, "a"))
    }

    @Test
    fun `снятие одного счёта из подмножества оставляет остальные`() {
        assertEquals(setOf("c"), AccountSelection.toggle(setOf("b", "c"), allIds, "b"))
    }
}
