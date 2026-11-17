package com.expeysi.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Контрагент по долгам: физлицо или компания, с которыми считают долги
 * отдельно от доходов и расходов по счетам.
 */
@Entity(tableName = "counterparties")
data class CounterpartyEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** "person" или "company", см. [com.expeysi.app.domain.CounterpartyType]. */
    val type: String,
    val note: String = "",
    /** Архивный контрагент не показывается в основном списке, но остаётся в истории. */
    val archived: Boolean = false,
    val deleted: Boolean = false,
    val updatedAt: Long,
)
