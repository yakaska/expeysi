package com.expeysi.app.domain

import kotlin.math.abs

object Money {

    /** "123,45" или "123.45" → 12345. Мусор → null. */
    fun parseToMinor(text: String): Long? {
        val cleaned = text.trim().replace(" ", "").replace(',', '.')
        if (!Regex("""^-?\d+(\.\d{1,2})?$""").matches(cleaned)) return null
        val negative = cleaned.startsWith("-")
        val absText = cleaned.removePrefix("-")
        val dot = absText.indexOf('.')
        val rubles = if (dot == -1) absText else absText.substring(0, dot)
        val kopecks = if (dot == -1) "0" else absText.substring(dot + 1)
        val minor = rubles.toLong() * 100 + kopecks.padEnd(2, '0').toLong()
        return if (negative) -minor else minor
    }

    /** 123456 → "1 234,56". */
    fun format(minor: Long): String {
        val negative = minor < 0
        val absolute = abs(minor)
        val rubles = absolute / 100
        val kopecks = absolute % 100
        val grouped = rubles.toString().reversed().chunked(3).joinToString(" ").reversed()
        val sign = if (negative) "-" else ""
        return "$sign$grouped,${kopecks.toString().padStart(2, '0')}"
    }
}
