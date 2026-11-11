package com.expeysi.app.ui.common

import java.util.Currency
import java.util.Locale

/**
 * Название валюты по локали: java.util.Currency берёт названия из ICU,
 * поэтому «RUB» → «российский рубль» на русском и «US Dollar» на английском.
 * Неизвестный код и отсутствие локализованного названия — код как есть.
 */
object CurrencyLabels {

    private val cache = mutableMapOf<Pair<String, Locale>, String>()

    /** Локализованное название в нижнем регистре, как даёт ICU. */
    fun displayName(code: String, locale: Locale = Locale.getDefault()): String = cache.getOrPut(code to locale) {
        runCatching { Currency.getInstance(code).getDisplayName(locale) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
            ?: code
    }

    /** Локализованное название с прописной первой буквы: «Российский рубль». */
    fun title(code: String, locale: Locale = Locale.getDefault()): String {
        val name = displayName(code, locale)
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}
