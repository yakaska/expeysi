package com.expeysi.app.domain

enum class TxType(val wire: String) {
    INCOME("income"),
    EXPENSE("expense"),
    ;

    companion object {
        fun fromWire(value: String): TxType = entries.firstOrNull { it.wire == value } ?: EXPENSE
    }
}
