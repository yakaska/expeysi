package com.expeysi.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.expeysi.app.data.seed.CurrencySeed
import com.expeysi.app.data.seed.PresetData
import java.util.UUID

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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS balances (" +
                "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, currencyCode TEXT NOT NULL, " +
                "deleted INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL)",
        )
        db.execSQL("ALTER TABLE accounts ADD COLUMN balanceId TEXT NOT NULL DEFAULT ''")

        // По балансу на каждую валюту существующих счетов. id детерминированный
        // от кода валюты — одинаковый на всех устройствах, что важно для синка.
        val codes = mutableListOf<String>()
        db.query("SELECT DISTINCT currencyCode FROM accounts").use { cursor ->
            while (cursor.moveToNext()) codes.add(cursor.getString(0))
        }
        val now = System.currentTimeMillis()
        codes.forEach { code ->
            val id = UUID.nameUUIDFromBytes("expeysi-balance-$code".toByteArray()).toString()
            val name = if (code == "RUB") "Основной" else "Баланс $code"
            db.execSQL(
                "INSERT OR IGNORE INTO balances (id, name, currencyCode, deleted, updatedAt) " +
                    "VALUES (?, ?, ?, 0, ?)",
                arrayOf(id, name, code, now),
            )
            db.execSQL("UPDATE accounts SET balanceId = ? WHERE currencyCode = ?", arrayOf(id, code))
        }
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN colorKey TEXT NOT NULL DEFAULT 'default'")
        val now = System.currentTimeMillis()
        PresetData.categories.forEach { c ->
            // Новые предзаписанные категории доезжают на существующие установки;
            // уже существующие (в т.ч. переименованные) получают только цвет.
            db.execSQL(
                "INSERT OR IGNORE INTO categories (id, name, iconKey, type, colorKey, deleted, updatedAt) " +
                    "VALUES (?, ?, ?, ?, ?, 0, ?)",
                arrayOf(c.id, c.name, c.iconKey, c.type.wire, c.colorKey, now),
            )
            db.execSQL("UPDATE categories SET colorKey = ? WHERE id = ?", arrayOf(c.colorKey, c.id))
        }
        // «Продукты» → «Еда», только если пользователь не переименовывал категорию.
        db.execSQL(
            "UPDATE categories SET name = 'Еда', iconKey = 'restaurant' " +
                "WHERE id = '00000000-0000-4000-8000-000000000101' AND name = 'Продукты'",
        )
        // Цвета для старых предзаписанных категорий, которых нет в новом наборе.
        mapOf(
            "00000000-0000-4000-8000-000000000104" to "orange", // Кафе
            "00000000-0000-4000-8000-000000000107" to "default", // Прочее (расход)
            "00000000-0000-4000-8000-000000000202" to "green", // Подработка
            "00000000-0000-4000-8000-000000000203" to "default", // Прочее (доход)
        ).forEach { (id, colorKey) ->
            db.execSQL("UPDATE categories SET colorKey = ? WHERE id = ?", arrayOf(colorKey, id))
        }
    }
}

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        CurrencyEntity::class,
        BalanceEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun balanceDao(): BalanceDao
}
