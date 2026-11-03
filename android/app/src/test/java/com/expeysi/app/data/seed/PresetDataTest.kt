package com.expeysi.app.data.seed

import com.expeysi.app.domain.TxType
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetDataTest {

    @Test
    fun `все предзаписанные id это валидные UUID`() {
        val ids = PresetData.accounts.map { it.id } + PresetData.categories.map { it.id }
        ids.forEach { UUID.fromString(it) }
    }

    @Test
    fun `id не повторяются`() {
        val ids = PresetData.accounts.map { it.id } + PresetData.categories.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `дежурные счета имеют зафиксированные id`() {
        // Детерминизм критичен для синка: тест защищает константы от правок.
        assertEquals("00000000-0000-4000-8000-000000000001", PresetData.ACCOUNT_CASH_ID)
        assertEquals("00000000-0000-4000-8000-000000000002", PresetData.ACCOUNT_CARD_ID)
    }

    @Test
    fun `есть категории обоих типов и у всех заданы иконки`() {
        val types = PresetData.categories.map { it.type }.toSet()
        assertEquals(setOf(TxType.INCOME, TxType.EXPENSE), types)
        PresetData.categories.forEach { assertTrue(it.iconKey.isNotBlank()) }
        PresetData.accounts.forEach { assertTrue(it.iconKey.isNotBlank()) }
    }

    @Test
    fun `посев создаёт сущности с теми же id и нулевым остатком`() {
        val accounts = PresetData.accountEntities(nowMillis = 42L)
        assertEquals(PresetData.accounts.size, accounts.size)
        assertEquals(PresetData.accounts.map { it.id }, accounts.map { it.id })
        accounts.forEach {
            assertEquals(0L, it.openingBalanceMinor)
            assertEquals(42L, it.updatedAt)
            assertTrue(!it.deleted)
        }

        val categories = PresetData.categoryEntities(nowMillis = 42L)
        assertEquals(PresetData.categories.size, categories.size)
        categories.forEach { assertTrue(!it.deleted) }
    }
}
