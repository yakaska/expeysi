package com.expeysi.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY name")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE balanceId = :balanceId")
    suspend fun byBalanceId(balanceId: String): List<AccountEntity>

    @Query("DELETE FROM accounts")
    suspend fun clear()
}
