package com.expeysi.app.data.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencySeedTest {

    @Test
    fun `список валют большой и без повторов кодов`() {
        assertTrue(CurrencySeed.all.size > 150)
        assertEquals(CurrencySeed.all.size, CurrencySeed.all.map { it.code }.toSet().size)
    }

    @Test
    fun `коды трёхбуквенные, названия и символы не пустые`() {
        CurrencySeed.all.forEach { row ->
            assertTrue(row.code.matches(Regex("[A-Z]{3}")))
            assertTrue(row.name.isNotBlank())
            assertTrue(row.symbol.isNotBlank())
        }
    }

    @Test
    fun `коды из карты тиров есть в справочнике`() {
        CurrencySeed.tiers.forEach { (code, _) ->
            assertTrue("нет в справочнике: $code", CurrencySeed.all.any { it.code == code })
        }
    }

    @Test
    fun `тиры ядро и распространённые не устаревшие`() {
        CurrencySeed.all.filter { row ->
            val tier = CurrencySeed.tiers[row.code]
            tier == CurrencyTier.CORE || tier == CurrencyTier.COMMON
        }.forEach { row ->
            assertTrue("CORE/COMMON устаревшая: ${row.code}", !CurrencySeed.isDeprecated(row))
        }
    }

    @Test
    fun `ядро популярных в фиксированном порядке`() {
        val core = CurrencySeed.tiers.filterValues { it == CurrencyTier.CORE }.keys.toList()
        assertEquals(listOf("RUB", "USD", "EUR", "VND", "CNY", "KZT"), core)
    }

    @Test
    fun `тиры скрытых валют`() {
        assertEquals(CurrencyTier.HIDDEN, CurrencySeed.tierOf("ADP")) // отменённая
        assertEquals(CurrencyTier.HIDDEN, CurrencySeed.tierOf("ZWL")) // годы в названии
        assertEquals(CurrencyTier.HIDDEN, CurrencySeed.tierOf("XAU")) // металл
        assertEquals(CurrencyTier.HIDDEN, CurrencySeed.tierOf("XTS")) // тестовый код
        assertEquals(CurrencyTier.CORE, CurrencySeed.tierOf("RUB"))
        assertEquals(CurrencyTier.COMMON, CurrencySeed.tierOf("GBP"))
        assertEquals(CurrencyTier.EXOTIC, CurrencySeed.tierOf("AFN"))
        assertEquals(CurrencyTier.EXOTIC, CurrencySeed.tierOf("XOF")) // реальная CFA-валюта
    }

    @Test
    fun `основные валюты присутствуют`() {
        val codes = CurrencySeed.all.map { it.code }.toSet()
        assertTrue(codes.containsAll(listOf("RUB", "USD", "EUR", "KZT", "GEL", "AMD")))
    }

    @Test
    fun `валюты со своими знаками в unicode показывают глиф, а не код`() {
        val byCode = CurrencySeed.all.associateBy { it.code }
        val glyphs = mapOf(
            "RUB" to "₽",
            "TRY" to "₺",
            "UAH" to "₴",
            "KZT" to "₸",
            "GEL" to "₾",
            "AZN" to "₼",
            "THB" to "฿",
            "NGN" to "₦",
            "BDT" to "৳",
            "AFN" to "؋",
            "MNT" to "₮",
            "LAK" to "₭",
            "GHS" to "₵",
            "CRC" to "₡",
            "NPR" to "₨",
            "LKR" to "₨",
            "PKR" to "₨",
        )
        glyphs.forEach { (code, glyph) ->
            assertEquals(glyph, byCode.getValue(code).symbol)
        }
    }
}
