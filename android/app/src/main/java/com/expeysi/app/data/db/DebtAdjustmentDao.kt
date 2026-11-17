package com.expeysi.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtAdjustmentDao {

    @Upsert
    suspend fun upsert(adjustment: DebtAdjustmentEntity)

    @Upsert
    suspend fun upsertAll(adjustments: List<DebtAdjustmentEntity>)

    @Query("SELECT * FROM debt_adjustments WHERE deleted = 0 AND counterpartyId = :counterpartyId ORDER BY date DESC")
    fun observeActiveForCounterparty(counterpartyId: String): Flow<List<DebtAdjustmentEntity>>

    @Query("SELECT * FROM debt_adjustments WHERE deleted = 0 AND counterpartyId = :counterpartyId")
    suspend fun listActiveForCounterparty(counterpartyId: String): List<DebtAdjustmentEntity>

    @Query("SELECT * FROM debt_adjustments")
    fun observeAll(): Flow<List<DebtAdjustmentEntity>>

    /**
     * Списания «в отчётах» за отчётный период в одной валюте: разрезки и
     * «Анализ финансов» берут их одним списком, знак — минус/плюс.
     */
    @Query(
        "SELECT * FROM debt_adjustments " +
            "WHERE deleted = 0 AND countInStats = 1 AND kind = 'write_off' AND currencyCode = :currencyCode " +
            "AND (:startMillis IS NULL OR date >= :startMillis) " +
            "AND (:endMillis IS NULL OR date < :endMillis)",
    )
    suspend fun listStatWriteOffs(
        currencyCode: String,
        startMillis: Long?,
        endMillis: Long?,
    ): List<DebtAdjustmentEntity>

    @Query("SELECT * FROM debt_adjustments WHERE id = :id")
    suspend fun byId(id: String): DebtAdjustmentEntity?

    /** Есть ли живые корректировки у контрагента: запрет удаления при истории. */
    @Query("SELECT EXISTS(SELECT 1 FROM debt_adjustments WHERE counterpartyId = :counterpartyId AND deleted = 0)")
    suspend fun hasActiveForCounterparty(counterpartyId: String): Boolean

    @Query("DELETE FROM debt_adjustments")
    suspend fun clear()
}
