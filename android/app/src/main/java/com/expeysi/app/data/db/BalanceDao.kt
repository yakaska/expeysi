package com.expeysi.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceDao {

    @Upsert
    suspend fun upsert(balance: BalanceEntity)

    @Upsert
    suspend fun upsertAll(balances: List<BalanceEntity>)

    @Query("SELECT * FROM balances WHERE deleted = 0 ORDER BY name")
    fun observeActive(): Flow<List<BalanceEntity>>

    @Query("SELECT * FROM balances")
    fun observeAll(): Flow<List<BalanceEntity>>

    @Query("SELECT * FROM balances WHERE id = :id")
    suspend fun byId(id: String): BalanceEntity?

    @Query("SELECT * FROM balances WHERE dirty = 1")
    suspend fun listDirty(): List<BalanceEntity>

    /** Сбрасывает dirty только если updatedAt строки ещё равен отправленной версии. */
    @Query("UPDATE balances SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long): Int

    @Query("UPDATE balances SET dirty = 1 WHERE id = :id")
    suspend fun markDirty(id: String)

    @Query("DELETE FROM balances")
    suspend fun clear()
}
