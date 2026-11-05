package com.expeysi.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.expeysi.app.data.seed.CurrencySeed

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'RUB'")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS currencies (" +
                "code TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, symbol TEXT NOT NULL)",
        )
        CurrencySeed.all.forEach { row ->
            db.execSQL(
                "INSERT OR IGNORE INTO currencies (code, name, symbol) VALUES (?, ?, ?)",
                arrayOf(row.code, row.name, row.symbol),
            )
        }
    }
}

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class, CurrencyEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun currencyDao(): CurrencyDao
}
