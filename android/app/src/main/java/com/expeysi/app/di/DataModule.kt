package com.expeysi.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.expeysi.app.data.analysis.AnalysisRepository
import com.expeysi.app.data.analysis.RoomAnalysisRepository
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AppDatabase
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.CurrencyEntity
import com.expeysi.app.data.db.MIGRATION_1_2
import com.expeysi.app.data.db.MIGRATION_2_3
import com.expeysi.app.data.db.MIGRATION_3_4
import com.expeysi.app.data.db.MIGRATION_4_5
import com.expeysi.app.data.db.MIGRATION_5_6
import com.expeysi.app.data.db.MIGRATION_6_7
import com.expeysi.app.data.db.MIGRATION_7_8
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.seed.CurrencySeed
import com.expeysi.app.data.seed.PresetData
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

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
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Посев предзаписанных данных — только при создании БД,
                    // чтобы не воскрешать удалённые пользователем записи.
                    seedScope.launch {
                        val now = System.currentTimeMillis()
                        val database = dbProvider.get()
                        database.balanceDao().upsertAll(PresetData.balanceEntities(now))
                        database.accountDao().upsertAll(PresetData.accountEntities(now))
                        database.categoryDao().upsertAll(PresetData.categoryEntities(now))
                        database.currencyDao().insertAll(
                            CurrencySeed.all.map { CurrencyEntity(it.code, it.name, it.symbol) },
                        )
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

    @Provides
    fun provideCurrencyDao(db: AppDatabase): CurrencyDao = db.currencyDao()

    @Provides
    fun provideBalanceDao(db: AppDatabase): BalanceDao = db.balanceDao()

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences = context.getSharedPreferences("expeysi_prefs", Context.MODE_PRIVATE)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalysisRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAnalysisRepository(impl: RoomAnalysisRepository): AnalysisRepository
}
