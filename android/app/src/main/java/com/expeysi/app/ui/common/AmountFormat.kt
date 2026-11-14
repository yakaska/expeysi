package com.expeysi.app.ui.common

import com.expeysi.app.domain.Money
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.max

/** Форматирование денег и курсов для отчётов и экранов: Money.formatAuto с неразрывными группами разрядов. */
object AmountFormat {

    private const val NBSP = "\u00A0"

    /** 178 449 ₽ без нулевых копеек; 1 234,56 — с копейками. */
    fun amount(minor: Long): String = Money.formatAuto(minor).replace(" ", NBSP)

    fun withCurrency(minor: Long, symbol: String): String = amount(minor) + NBSP + symbol

    /**
     * Курс перевода: сколько начислений приходится на единицу списания.
     * До трёх значащих цифр, крупный курс — целым числом ("1,25" → "25 000"),
     * нули дробной части срезаются.
     */
    fun rate(toMinor: Long, fromMinor: Long): String {
        require(fromMinor > 0 && toMinor > 0)
        val integerPart = toMinor / fromMinor
        val precision = MathContext(max(3, integerPart.toString().length), RoundingMode.HALF_UP)
        val ratio = BigDecimal.valueOf(toMinor, 2).divide(BigDecimal.valueOf(fromMinor, 2), precision)
        val plain = ratio.stripTrailingZeros().toPlainString()
        val dot = plain.indexOf('.')
        val integer = if (dot == -1) plain else plain.substring(0, dot)
        val fraction = if (dot == -1) "" else plain.substring(dot + 1).trimEnd('0')
        val grouped = integer.reversed().chunked(3).joinToString(NBSP).reversed()
        return if (fraction.isEmpty()) grouped else "$grouped,$fraction"
    }
}
