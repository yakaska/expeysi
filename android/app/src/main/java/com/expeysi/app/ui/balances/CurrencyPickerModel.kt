package com.expeysi.app.ui.balances

import com.expeysi.app.data.db.CurrencyEntity
import com.expeysi.app.data.seed.CurrencySeed
import com.expeysi.app.data.seed.CurrencyTier
import com.expeysi.app.ui.common.CurrencyLabels
import java.util.Currency
import java.util.Locale

/** Секция пикера валют; заголовок секции рисуется по [SectionKind]. */
data class PickerSection(val kind: SectionKind, val items: List<CurrencyEntity>)

/**
 * Разделы списка пикера. SEARCH — без заголовка (плоский результат поиска);
 * CURRENT — строка выбранной, но скрытой валюты (устаревшей или вне справочника).
 */
enum class SectionKind { CURRENT, SEARCH, RECENT, USED, POPULAR, REST }

/**
 * Секции пикера при пустом запросе: Недавние → Используемые → Популярные,
 * «Остальные» добавляется при [showAll]. HIDDEN нигде не показывается; если
 * выбранный код не попадает ни в одну секцию, спереди выводится «Текущая».
 */
fun buildPickerSections(
    all: List<CurrencyEntity>,
    selectedCode: String,
    usedCodes: List<String>,
    recentCodes: List<String>,
    userLocaleCode: String,
    showAll: Boolean,
    locale: Locale = Locale.getDefault(),
): List<PickerSection> {
    val byCode = all.associateBy { it.code }
    val visible = byCode.values.filter { CurrencySeed.tierOf(it.code) != CurrencyTier.HIDDEN }
    val visibleCodes = visible.map { it.code }.toSet()

    val sections = mutableListOf<PickerSection>()

    val recent = recentCodes.distinct().take(PICKER_RECENT_LIMIT)
        .filter { it in visibleCodes }
        .mapNotNull(byCode::get)
    if (recent.isNotEmpty()) {
        sections += PickerSection(SectionKind.RECENT, recent)
    }

    val taken = recent.map { it.code }.toMutableSet()
    val used = usedCodes.distinct()
        .filter { it in visibleCodes && it !in taken }
        .mapNotNull(byCode::get)
    if (used.isNotEmpty()) {
        sections += PickerSection(SectionKind.USED, used)
    }
    taken += used.map { it.code }

    val localeCurrency = runCatching {
        Currency.getInstance(Locale("", userLocaleCode)).currencyCode
    }.getOrNull()
    val coreCodes = CurrencySeed.tiers.filterValues { it == CurrencyTier.CORE }.keys.toList()
    val commonCodes = CurrencySeed.tiers.filterValues { it == CurrencyTier.COMMON }.keys
        .sortedBy { CurrencyLabels.displayName(it, locale) }
    val popular = (coreCodes + listOfNotNull(localeCurrency) + commonCodes)
        .filter { it in visibleCodes && it !in taken }
        .distinct()
        .mapNotNull(byCode::get)
    if (popular.isNotEmpty()) {
        sections += PickerSection(SectionKind.POPULAR, popular)
    }
    taken += popular.map { it.code }

    if (showAll) {
        val rest = visible
            .filter { it.code !in taken }
            .sortedBy { CurrencyLabels.displayName(it.code, locale) }
        if (rest.isNotEmpty()) {
            sections += PickerSection(SectionKind.REST, rest)
        }
    }

    return withCurrentSection(all, selectedCode, sections)
}

/**
 * Поиск по непустому запросу: один плоский список без HIDDEN, без секций.
 * Матч по коду, английскому названию, локализованному названию и символу.
 */
fun searchCurrencies(
    all: List<CurrencyEntity>,
    selectedCode: String,
    usedCodes: List<String>,
    query: String,
    locale: Locale = Locale.getDefault(),
): List<PickerSection> {
    val byCode = all.associateBy { it.code }
    val visible = byCode.values.filter { CurrencySeed.tierOf(it.code) != CurrencyTier.HIDDEN }

    val q = query.trim()
    val results = if (q.isBlank()) {
        emptyList()
    } else {
        val used = usedCodes.toSet()
        visible
            .mapNotNull { currency -> matchRank(currency, q, locale)?.let { currency to it } }
            .sortedWith(
                compareBy(
                    { (_, rank) -> rank },
                    { (currency, _) -> currency.code !in used },
                    { (currency, _) -> CurrencySeed.tierOf(currency.code) == CurrencyTier.EXOTIC },
                    { (currency, _) -> CurrencyLabels.displayName(currency.code, locale) },
                ),
            )
            .map { (currency, _) -> currency }
    }

    val sections = mutableListOf<PickerSection>()
    if (results.isNotEmpty()) {
        sections += PickerSection(SectionKind.SEARCH, results)
    }
    return withCurrentSection(all, selectedCode, sections)
}

/** Выбранная, но не попадающая ни в одну секцию валюта — первой строкой «Текущая». */
private fun withCurrentSection(
    all: List<CurrencyEntity>,
    selectedCode: String,
    sections: List<PickerSection>,
): List<PickerSection> {
    if (selectedCode.isBlank()) return sections
    val shown = sections.flatMapTo(mutableSetOf<String>()) { it.items.map { item -> item.code } }
    val byCode = all.associateBy { it.code }
    val current = byCode[selectedCode] ?: CurrencyEntity(selectedCode, selectedCode, selectedCode)
    return if (current.code in shown) {
        sections
    } else {
        listOf(PickerSection(SectionKind.CURRENT, listOf(current))) + sections
    }
}

/**
 * Ранг совпадения строки поиска с валютой: точный код (0) › код начинается
 * с запроса (1) › название начинается с запроса (2) › вхождение в название
 * или символ (3); null — не совпало.
 */
private fun matchRank(currency: CurrencyEntity, query: String, locale: Locale): Int? {
    val localized = CurrencyLabels.displayName(currency.code, locale)
    return when {
        currency.code.equals(query, ignoreCase = true) -> 0
        currency.code.startsWith(query, ignoreCase = true) -> 1
        localized.startsWith(query, ignoreCase = true) ||
            currency.name.startsWith(query, ignoreCase = true) -> 2
        localized.contains(query, ignoreCase = true) ||
            currency.name.contains(query, ignoreCase = true) ||
            currency.symbol.contains(query, ignoreCase = true) -> 3
        else -> null
    }
}

/** Валют в секции «Недавние» пикера. */
private const val PICKER_RECENT_LIMIT = 3
