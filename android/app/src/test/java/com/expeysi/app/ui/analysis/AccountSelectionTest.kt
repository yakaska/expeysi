package com.expeysi.app.ui.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSelectionTest {

    private val allIds = setOf("a", "b", "c")

    @Test
    fun `снятие одного счёта со всех даёт явный набор`() {
        val updated = AccountSelection.toggle(null, allIds, "a")
        assertEquals(setOf("b", "c"), updated)
    }

    @Test
    fun `добавление последнего счёта сводится к все счета`() {
        assertNull(AccountSelection.toggle(setOf("b", "c"), allIds, "a"))
    }

    @Test
    fun `ручной выбор всех счетов сводится к все счета`() {
        assertNull(AccountSelection.toggle(setOf("b", "c"), allIds, "a"))
    }

    @Test
    fun `выбор конкретного счёта снимает все счета`() {
        assertEquals(setOf("b", "c"), AccountSelection.toggle(null, allIds, "a"))
    }
}
