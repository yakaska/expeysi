package com.expeysi.app.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryPaletteTest {

    @Test
    fun `первым в палитре стоит orange - цвет новых категорий и счетов`() {
        // EditAccountViewModel и EditBalanceViewModel берут дефолтный цвет
        // как availableKeys.first(); перестановка изменит поведение UI.
        assertEquals("orange", CategoryPalette.availableKeys.first())
    }

    @Test
    fun `ключ default есть в палитре`() {
        assertTrue(CategoryPalette.DEFAULT_KEY in CategoryPalette.availableKeys)
    }

    @Test
    fun `неизвестный ключ резолвится цветом по умолчанию`() {
        assertEquals(
            CategoryPalette.color(CategoryPalette.DEFAULT_KEY),
            CategoryPalette.color("no_such_color"),
        )
    }
}
