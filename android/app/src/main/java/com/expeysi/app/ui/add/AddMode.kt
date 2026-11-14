package com.expeysi.app.ui.add

import com.expeysi.app.domain.TxType

/**
 * Режим экрана добавления: доход, расход или перевод. Перевод хранится парой ног
 * [TxType.TRANSFER_OUT]/[TxType.TRANSFER_IN], поэтому у режима нет собственного
 * значения TxType: направление определяют поля «со счёта» и «на счёт».
 */
enum class AddMode(val wire: String) {
    EXPENSE("expense"),
    INCOME("income"),
    TRANSFER("transfer"),
    ;

    /** Доход и расход однозначно выражаются в TxType для хранилища и выбора категории. */
    val txType: TxType?
        get() = when (this) {
            EXPENSE -> TxType.EXPENSE
            INCOME -> TxType.INCOME
            TRANSFER -> null
        }

    companion object {
        /** Значения ног перевода маршрутизируются в режим TRANSFER, неизвестное — в расход. */
        fun fromWire(value: String?): AddMode = when {
            TxType.fromWire(value ?: "").isTransfer -> TRANSFER
            else -> entries.firstOrNull { it.wire == value } ?: EXPENSE
        }
    }
}
