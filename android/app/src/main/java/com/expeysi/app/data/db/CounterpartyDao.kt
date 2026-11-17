package com.expeysi.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterpartyDao {

    @Upsert
    suspend fun upsert(counterparty: CounterpartyEntity)

    @Upsert
    suspend fun upsertAll(counterparties: List<CounterpartyEntity>)

    @Query("SELECT * FROM counterparties WHERE deleted = 0 AND archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<CounterpartyEntity>>

    @Query("SELECT * FROM counterparties WHERE deleted = 0 AND archived = 1 ORDER BY name COLLATE NOCASE")
    fun observeArchived(): Flow<List<CounterpartyEntity>>

    @Query("SELECT * FROM counterparties WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    suspend fun listActive(): List<CounterpartyEntity>

    @Query("SELECT * FROM counterparties WHERE id = :id")
    suspend fun byId(id: String): CounterpartyEntity?

    @Query("SELECT * FROM counterparties")
    fun observeAll(): Flow<List<CounterpartyEntity>>

    @Query("DELETE FROM counterparties")
    suspend fun clear()
}
