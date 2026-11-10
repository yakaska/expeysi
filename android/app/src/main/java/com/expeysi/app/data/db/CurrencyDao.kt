package com.expeysi.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(currencies: List<CurrencyEntity>)

    @Query("SELECT * FROM currencies ORDER BY code")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies ORDER BY code")
    suspend fun listAll(): List<CurrencyEntity>
}
