package com.expeysi.app.domain

enum class TxType(val wire: String) {
    INCOME("income"),
    EXPENSE("expense"),
    TRANSFER_OUT("transfer_out"),
    TRANSFER_IN("transfer_in"),

    /** Долг выдан контрагенту: деньги со счёта контрагенту, контрагент должен. */
    DEBT_LEND("debt_lend"),

    /** Долг взят от контрагента: деньги от контрагента на счёт, должен контрагенту. */
    DEBT_BORROW("debt_borrow"),

    /** Получено погашение от контрагента: деньги контрагенту на счёт. */
    DEBT_REPAY_IN("debt_repay_in"),

    /** Отправлено погашение контрагенту: деньги со счёта контрагенту. */
    DEBT_REPAY_OUT("debt_repay_out"),
    ;

    /** Перевод хранится парой ног; направление определяют поля операции. */
    val isTransfer: Boolean get() = this == TRANSFER_OUT || this == TRANSFER_IN

    /** Долговые операции: вторая сторона — контрагент ([TransactionEntity.counterpartyId]), а не счёт. */
    val isDebt: Boolean
        get() = this == DEBT_LEND || this == DEBT_BORROW || this == DEBT_REPAY_IN || this == DEBT_REPAY_OUT

    companion object {
        fun fromWire(value: String): TxType = entries.firstOrNull { it.wire == value } ?: EXPENSE
    }
}
