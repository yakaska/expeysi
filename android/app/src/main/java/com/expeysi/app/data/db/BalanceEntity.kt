package com.expeysi.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "balances")
data class BalanceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currencyCode: String,
    /** Ключ иконки из IconCatalog; пустой — круг с первой буквой названия. */
    @ColumnInfo(defaultValue = "") val iconKey: String = "",
    /** Ключ цвета из CategoryPalette. */
    @ColumnInfo(defaultValue = "default") val colorKey: String = "default",
    val deleted: Boolean = false,
    val updatedAt: Long,
)
