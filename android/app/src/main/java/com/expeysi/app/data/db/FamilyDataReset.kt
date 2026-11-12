package com.expeysi.app.data.db

import android.util.Log
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Точечный шов для очистки локальных семейных данных по сигналу о смене
 * или выходе из семьи. Интерфейс существует ради тестируемости потребителей
 * (FamilyRepository) на JVM без Room; реализация — единственная.
 */
interface FamilyDataReset {

    /** Полная очистка семейных таблиц Room одной транзакцией. */
    suspend fun clearFamilyData()
}

@Singleton
class RoomFamilyDataReset @Inject constructor(
    private val database: AppDatabase,
) : FamilyDataReset {

    override suspend fun clearFamilyData() {
        database.withTransaction {
            Log.d(TAG, "room clear begin")
            database.transactionDao().clear()
            database.accountDao().clear()
            database.categoryDao().clear()
            database.balanceDao().clear()
            Log.d(TAG, "room clear end")
        }
    }

    private companion object {
        const val TAG = "ExpeysiFamily"
    }
}
