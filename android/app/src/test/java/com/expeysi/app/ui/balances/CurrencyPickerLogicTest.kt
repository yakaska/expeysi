package com.expeysi.app.ui.balances

import com.expeysi.app.data.db.CurrencyEntity
import com.expeysi.app.data.seed.CurrencySeed
import com.expeysi.app.data.seed.CurrencyTier
import com.expeysi.app.ui.common.CurrencyLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CurrencyPickerLogicTest {

    private val all = CurrencySeed.all.map { CurrencyEntity(it.code, it.name, it.symbol) }

    /** Локаль для детерминированных названий и сортировки в тестах. */
    private val ruLocale = Locale.forLanguageTag("ru")

    private fun build(
        selectedCode: String = "",
        usedCodes: List<String> = emptyList(),
        recentCodes: List<String> = emptyList(),
        userLocaleCode: String = "",
        showAll: Boolean = false,
    ): List<PickerSection> = buildPickerSections(
        all = all,
        selectedCode = selectedCode,
        usedCodes = usedCodes,
        recentCodes = recentCodes,
        userLocaleCode = userLocaleCode,
        showAll = showAll,
        locale = ruLocale,
    )

    private fun search(
        query: String,
        selectedCode: String = "",
        usedCodes: List<String> = emptyList(),
    ): List<CurrencyEntity> = searchCurrencies(
        all = all,
        selectedCode = selectedCode,
        usedCodes = usedCodes,
        query = query,
        locale = ruLocale,
    ).flatMap { it.items }

    private fun codes(sections: List<PickerSection>, kind: SectionKind): List<String>? = sections.firstOrNull { it.kind == kind }?.items?.map { it.code }

    @Test
    fun `секции без дубликатов и без скрытых валют`() {
        val sections = build(
            selectedCode = "USD",
            usedCodes = listOf("KZT", "USD", "ZWL", "XAU"),
            recentCodes = listOf("EUR", "CNY", "ZWL"),
            showAll = true,
        )
        val flat = sections.flatMap { it.items }.map { it.code }
        assertEquals(flat.size, flat.toSet().size)
        assertFalse(flat.any { it in setOf("ZWL", "XAU", "XXX", "XTS") })
    }

    @Test
    fun `недавние не длиннее трёх, используемые без пересечения с недавними`() {
        val sections = build(
            usedCodes = listOf("CNY", "GBP"),
            recentCodes = listOf("EUR", "CNY", "RUB", "VND"),
        )
        assertEquals(listOf("EUR", "CNY", "RUB"), codes(sections, SectionKind.RECENT))
        assertEquals(listOf("GBP"), codes(sections, SectionKind.USED))
    }

    @Test
    fun `ядро открывает секцию популярных`() {
        val sections = build()
        assertEquals(
            listOf("RUB", "USD", "EUR", "VND", "CNY", "KZT"),
            codes(sections, SectionKind.POPULAR)!!.take(6),
        )
    }

    @Test
    fun `валюта локали попадает в популярные без дублей`() {
        val sections = build(userLocaleCode = "BA", showAll = true)
        val popular = codes(sections, SectionKind.POPULAR)!!
        assertTrue(popular.contains("BAM"))
        assertFalse(codes(sections, SectionKind.REST)!!.contains("BAM"))
    }

    @Test
    fun `без флага все валюты секции остальные нет, с флагом есть и по алфавиту`() {
        assertNull(codes(build(), SectionKind.REST))
        val rest = codes(build(showAll = true), SectionKind.REST)!!
        assertTrue(rest.contains("AFN"))
        assertEquals(rest.sortedBy { CurrencyLabels.displayName(it, ruLocale) }, rest)
    }

    @Test
    fun `скрытая выбранная валюта первой строкой текущая`() {
        val sections = build(selectedCode = "ZWL")
        assertEquals(SectionKind.CURRENT, sections.first().kind)
        assertEquals("ZWL", sections.first().items.single().code)
    }

    @Test
    fun `неизвестный выбранный код первой строкой текущая`() {
        val sections = build(selectedCode = "ABC")
        assertEquals(SectionKind.CURRENT, sections.first().kind)
        assertEquals("ABC", sections.first().items.single().code)
    }

    @Test
    fun `видимая выбранная валюта не дублируется строкой текущая`() {
        val sections = build(selectedCode = "USD", usedCodes = listOf("USD"))
        assertNull(codes(sections, SectionKind.CURRENT))
        assertTrue(codes(sections, SectionKind.USED)!!.contains("USD"))
    }

    @Test
    fun `поиск по коду, названию и символу приводит к VND`() {
        listOf("vnd", "донг", "₫").forEach { query ->
            val items = search(query)
            assertEquals("query=$query", "VND", items.first().code)
        }
    }

    @Test
    fun `поиск us ставит USD выше экзотических`() {
        val items = search("us")
        assertEquals("USD", items.first().code)
    }

    @Test
    fun `поиск не находит скрытые валюты`() {
        val byName = search("Gold")
        assertFalse(byName.any { it.code == "XAU" })
        val byCode = search("XTS")
        assertFalse(byCode.any { it.code == "XTS" })
        assertTrue(byCode.all { CurrencySeed.tierOf(it.code) != CurrencyTier.HIDDEN })
    }
}
