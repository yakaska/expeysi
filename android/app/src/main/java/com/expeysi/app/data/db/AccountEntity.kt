package com.expeysi.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    val openingBalanceMinor: Long,
    val deleted: Boolean = false,
    val updatedAt: Long,
)
