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

    @Query("DELETE FROM balances")
    suspend fun clear()
}
