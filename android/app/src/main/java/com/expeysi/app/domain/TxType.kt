package com.expeysi.app.domain

enum class TxType(val wire: String) {
    INCOME("income"),
    EXPENSE("expense"),
    TRANSFER_OUT("transfer_out"),
    TRANSFER_IN("transfer_in"),
    ;

    /** Перевод хранится парой ног; направление определяют поля операции. */
    val isTransfer: Boolean get() = this == TRANSFER_OUT || this == TRANSFER_IN

    companion object {
        fun fromWire(value: String): TxType = entries.firstOrNull { it.wire == value } ?: EXPENSE
    }
}
