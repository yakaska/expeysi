package com.expeysi.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AppDatabase
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.seed.PresetData
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        dbProvider: Provider<AppDatabase>,
    ): AppDatabase {
        val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return Room.databaseBuilder(context, AppDatabase::class.java, "expeysi.db")
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Посев предзаписанных данных — только при создании БД,
                    // чтобы не воскрешать удалённые пользователем записи.
                    seedScope.launch {
                        val now = System.currentTimeMillis()
                        val database = dbProvider.get()
                        database.accountDao().upsertAll(PresetData.accountEntities(now))
                        database.categoryDao().upsertAll(PresetData.categoryEntities(now))
                    }
                }
            })
            .build()
    }

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
}
