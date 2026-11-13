package com.expeysi.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    /** "income" или "expense", см. [com.expeysi.app.domain.TxType]. */
    val type: String,
    /** Ключ цвета обода иконки из фиксированной палитры CategoryPalette. */
    val colorKey: String = "default",
    val deleted: Boolean = false,
    val updatedAt: Long,
    /** Локальное изменение ещё не передано транспорту синхронизации. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
)
