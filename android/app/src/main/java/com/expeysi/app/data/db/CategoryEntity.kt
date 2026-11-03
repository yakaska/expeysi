package com.expeysi.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    /** "income" или "expense", см. [com.expeysi.app.domain.TxType]. */
    val type: String,
    val deleted: Boolean = false,
    val updatedAt: Long,
)
