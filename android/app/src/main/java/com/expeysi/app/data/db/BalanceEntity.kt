package com.expeysi.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "balances")
data class BalanceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currencyCode: String,
    val deleted: Boolean = false,
    val updatedAt: Long,
)
