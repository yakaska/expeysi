package com.expeysi.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    /** "income" или "expense", см. [com.expeysi.app.domain.TxType]. */
    val type: String,
    val amountMinor: Long,
    val accountId: String,
    val categoryId: String,
    /** День операции, epoch millis (локальная дата автора). */
    val date: Long,
    val note: String = "",
    val deleted: Boolean = false,
    val updatedAt: Long,
    val updatedBy: String = "",
)
