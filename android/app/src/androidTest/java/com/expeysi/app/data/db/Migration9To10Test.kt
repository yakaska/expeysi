package com.expeysi.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Миграция 9→10 против коммитнутых схем: v9 собирается из assets по `9.json`,
 * накатывается только миграция приложения, валидация сверяет результат с
 * `10.json`. Миграция пересчитывает масштаб сумм (сотые доли → десятитысячные,
 * ×100) и не трогает метки времени: документы журнала Firestore без метки
 * масштаба пересчитываются при чтении (SyncEngine.scaledMinor), перепись
 * журнала не нужна.
 */
@RunWith(AndroidJUnit4::class)
class Migration9To10Test {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    // Задача D8 запрещает пробелы в именах методов androidTest (в отличие от JVM-тестов).
    @Test
    fun migration9To10_rescalesAmountsBy100_keepsUpdatedAt() {
        helper.createDatabase(DB_NAME, 9).use { db ->
            db.execSQL(
                "INSERT INTO accounts (id, name, iconKey, colorKey, openingBalanceMinor, currencyCode, balanceId, deleted, updatedAt) " +
                    "VALUES ('acc', 'Наличные', 'payments', 'green', 12345, 'RUB', 'bal', 0, 1000)",
            )
            db.execSQL(
                "INSERT INTO transactions (id, type, amountMinor, accountId, categoryId, date, note, linkedId, counterpartyAccountId, deleted, updatedAt, updatedBy) " +
                    "VALUES ('t1', 'expense', 123456, 'acc', 'cat', 2000, '', '', '', 0, 2000, 'u')",
            )
            // Нулевые суммы пересчёту не меняются.
            db.execSQL(
                "INSERT INTO transactions (id, type, amountMinor, accountId, categoryId, date, note, linkedId, counterpartyAccountId, deleted, updatedAt, updatedBy) " +
                    "VALUES ('t2', 'income', 0, 'acc', 'cat', 3000, '', '', '', 0, 3000, 'u')",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 10, true, MIGRATION_9_10)

        db.query("SELECT openingBalanceMinor, updatedAt FROM accounts WHERE id = 'acc'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1_234_500L, cursor.getLong(0))
            assertEquals(1000L, cursor.getLong(1))
        }
        db.query("SELECT amountMinor, updatedAt FROM transactions WHERE id = 't1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(12_345_600L, cursor.getLong(0))
            assertEquals(2000L, cursor.getLong(1))
        }
        db.query("SELECT amountMinor, updatedAt FROM transactions WHERE id = 't2'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
            assertEquals(3000L, cursor.getLong(1))
        }
        db.close()
    }

    private companion object {
        const val DB_NAME = "migration-9-10-test"
    }
}
