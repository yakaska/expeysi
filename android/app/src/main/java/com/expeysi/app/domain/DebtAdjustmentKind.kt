package com.expeysi.app.domain

/**
 * Вид корректировки долга без движения денег по счёту: начальный остаток
 * отношений с контрагентом или списание остатка; хранится wire-строкой в БД.
 */
enum class DebtAdjustmentKind(val wire: String) {
    OPENING_BALANCE("opening_balance"),
    WRITE_OFF("write_off"),
    ;

    companion object {
        fun fromWire(value: String): DebtAdjustmentKind = entries.firstOrNull { it.wire == value } ?: OPENING_BALANCE
    }
}
