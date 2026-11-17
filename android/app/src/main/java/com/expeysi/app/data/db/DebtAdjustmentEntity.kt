package com.expeysi.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Корректировка долга без движения денег по счёту: начальный остаток
 * отношений с контрагентом или списание долга. Счетов не касается никогда.
 *
 * @param amountMinor знаковый вклад в остаток: плюс — контрагент должен,
 *   минус — должен он; списание хранит дельту, уменьшающую модуль остатка.
 *
 * Слияние версий в синхронизации — last-write-wins по [updatedAt] без
 * `updatedBy`: как и у контрагентов, поле автора правки для этой коллекции
 * не хранится ни в Room, ни в Firestore (см. SyncEngine).
 */
@Entity(tableName = "debt_adjustments")
data class DebtAdjustmentEntity(
    @PrimaryKey val id: String,
    val counterpartyId: String,
    /** "opening_balance" или "write_off", см. [com.expeysi.app.domain.DebtAdjustmentKind]. */
    val kind: String,
    val amountMinor: Long,
    val currencyCode: String,
    /** Дата корректировки, epoch millis (локальная дата автора). */
    val date: Long,
    val note: String = "",
    /** Показывать списание в отчётах как доход или расход; у начального остатка всегда false. */
    val countInStats: Boolean = false,
    val deleted: Boolean = false,
    val updatedAt: Long,
)
