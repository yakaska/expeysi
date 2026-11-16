package com.expeysi.app.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconCatalogTest {

    @Test
    fun `ключи по умолчанию и переводов есть в каталоге`() {
        val keys = IconCatalog.availableKeys
        assertTrue(IconCatalog.DEFAULT_KEY in keys)
        assertTrue(IconCatalog.TRANSFER_KEY in keys)
    }

    @Test
    fun `каждый известный ключ резолвится не дефолтной иконкой`() {
        // Опечатка в маппинге не даёт Compile-time ошибки: ключ молча
        // рисуется Category. Тест ловит такие промахи.
        IconCatalog.availableKeys
            .filterNot { it == IconCatalog.DEFAULT_KEY }
            .forEach { key ->
                assertTrue(
                    "ключ \"$key\" не привязан к иконке и рисуется дефолтом",
                    IconCatalog.icon(key) != Icons.Filled.Category,
                )
            }
    }

    @Test
    fun `неизвестный ключ резолвится иконкой по умолчанию`() {
        assertEquals(Icons.Filled.Category, IconCatalog.icon("no_such_key"))
        assertEquals(Icons.Filled.Category, IconCatalog.icon(IconCatalog.DEFAULT_KEY))
    }
}
