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
        "SELECT * FROM transactions WHERE deleted = 0 AND accountId IN (:accountIds) " +
            "ORDER BY date DESC, updatedAt DESC LIMIT :limit",
    )
    fun observeActiveForAccounts(accountIds: List<String>, limit: Int): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE deleted = 0 AND type = :type " +
            "AND date >= :startMillis AND date < :endMillis",
    )
    fun observeInPeriod(type: String, startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE accountId IN (:accountIds)")
    suspend fun byAccountIds(accountIds: List<String>): List<TransactionEntity>

    @Query(
        "SELECT * FROM transactions WHERE deleted = 0 AND type = :type " +
            "AND date >= :startMillis AND date < :endMillis AND accountId IN (:accountIds) " +
            "ORDER BY date DESC, updatedAt DESC",
    )
    suspend fun byTypeAndPeriod(
        type: String,
        startMillis: Long,
        endMillis: Long,
        accountIds: List<String>,
    ): List<TransactionEntity>

    @Query(
        "SELECT * FROM transactions WHERE deleted = 0 AND type = :type AND categoryId = :categoryId " +
            "AND date >= :startMillis AND date < :endMillis AND accountId IN (:accountIds) " +
            "ORDER BY date DESC, updatedAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun byCategoryAndPeriod(
        type: String,
        categoryId: String,
        startMillis: Long,
        endMillis: Long,
        accountIds: List<String>,
        limit: Int,
        offset: Int,
    ): List<TransactionEntity>

    @Query(
        "SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE deleted = 0 AND type = :type " +
            "AND categoryId = :categoryId AND date >= :startMillis AND date < :endMillis " +
            "AND accountId IN (:accountIds)",
    )
    suspend fun sumByCategoryAndPeriod(
        type: String,
        categoryId: String,
        startMillis: Long,
        endMillis: Long,
        accountIds: List<String>,
    ): Long

    @Query("SELECT * FROM transactions")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("DELETE FROM transactions")
    suspend fun clear()
}
