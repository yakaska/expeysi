package com.expeysi.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Операция: доход, расход или нога перевода. Перевод — пара записей
 * ("transfer_out" у счёта-источника, "transfer_in" у счёта-получателя),
 * связанных [linkedId]; у обычных операций оба поля пустые.
 *
 * @param linkedId общий UUID пары ног одного перевода.
 * @param counterpartyAccountId счёт на другой стороне перевода; подпись «счёт → счёт»
 *   работает, даже если нога-пара ещё не дошла по синку.
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    /** "income", "expense", "transfer_out" или "transfer_in", см. [com.expeysi.app.domain.TxType]. */
    val type: String,
    val amountMinor: Long,
    val accountId: String,
    val categoryId: String,
    /** День операции, epoch millis (локальная дата автора). */
    val date: Long,
    val note: String = "",
    val linkedId: String = "",
    val counterpartyAccountId: String = "",
    /**
     * Комиссия перевода, списанная со счёта-источника сверх отправляемой
     * суммы (только у transfer_out; у остальных записей 0). [amountMinor]
     * остаётся «всего списано» — отправлено + комиссия, как это учитывают
     * балансы и лента.
     */
    val feeMinor: Long = 0,
    /**
     * Контрагент по долгу — вторая сторона операции вместо счёта-получателя;
     * заполнен только у долговых операций (см. [com.expeysi.app.domain.TxType.isDebt]).
     */
    val counterpartyId: String = "",
    /** Срок возврата долга, epoch millis; заполнен только у DEBT_LEND и DEBT_BORROW. */
    val dueDate: Long? = null,
    val deleted: Boolean = false,
    val updatedAt: Long,
    val updatedBy: String = "",
)
