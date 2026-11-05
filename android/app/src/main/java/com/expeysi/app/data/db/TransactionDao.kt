package com.expeysi.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Upsert
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY date DESC, updatedAt DESC")
    fun observeActive(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND accountId = :accountId ORDER BY date DESC")
    fun observeActiveForAccount(accountId: String): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE deleted = 0 AND type = :type " +
            "AND date >= :startMillis AND date < :endMillis",
    )
    fun observeInPeriod(type: String, startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("DELETE FROM transactions")
    suspend fun clear()
}
