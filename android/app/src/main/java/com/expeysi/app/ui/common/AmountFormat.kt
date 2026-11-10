package com.expeysi.app.ui.common

/** Форматирование денег для отчётов: группировка неразрывным пробелом. */
object AmountFormat {

    private const val NBSP = "\u00A0"

    /** 178 449 ₽ без нулевых копеек; 1 234,56 — с копейками. */
    fun amount(minor: Long): String {
        val absolute = kotlin.math.abs(minor)
        val rubles = grouped(absolute / 100)
        val kopecks = absolute % 100
        val fractional = if (kopecks == 0L) "" else "," + kopecks.toString().padStart(2, '0')
        val sign = if (minor < 0) "-" else ""
        return sign + rubles + fractional
    }

    fun withCurrency(minor: Long, symbol: String): String = amount(minor) + NBSP + symbol

    private fun grouped(value: Long): String = value
        .toString()
        .reversed()
        .chunked(3)
        .joinToString(NBSP)
        .reversed()
}
