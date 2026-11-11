package com.expeysi.app.ui.common

import com.expeysi.app.domain.Money

/** Форматирование денег для отчётов: Money.formatAuto с неразрывными группами разрядов. */
object AmountFormat {

    private const val NBSP = "\u00A0"

    /** 178 449 ₽ без нулевых копеек; 1 234,56 — с копейками. */
    fun amount(minor: Long): String = Money.formatAuto(minor).replace(" ", NBSP)

    fun withCurrency(minor: Long, symbol: String): String = amount(minor) + NBSP + symbol
}
