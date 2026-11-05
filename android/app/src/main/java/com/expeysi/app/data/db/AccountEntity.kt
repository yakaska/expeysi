package com.expeysi.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    val openingBalanceMinor: Long,
    /** Код валюты ISO 4217, см. [com.expeysi.app.domain.Currency]. */
    @ColumnInfo(defaultValue = "RUB") val currencyCode: String = "RUB",
    val deleted: Boolean = false,
    val updatedAt: Long,
)
