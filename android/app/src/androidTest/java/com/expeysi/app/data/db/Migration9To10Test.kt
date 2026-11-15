package com.expeysi.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Миграция 9→10 против коммитнутых схем: v9 собирается из assets по `9.json`,
 * накатываются только миграции приложения, валидация сверяет результат с
 * `10.json`. Схемы лежат ассетами androidTest (`schemas/` в build.gradle.kts).
 */
@RunWith(AndroidJUnit4::class)
class Migration9To10Test {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    // Задача D8 запрещает пробелы в именах методов androidTest (в отличие от JVM-тестов).
    @Test
    fun migration9To10_oldRowsGetZeroFee_feeColumnReadable() {
        helper.createDatabase(DB_NAME, 9).use { db ->
            db.execSQL(
                "INSERT INTO transactions (id, type, amountMinor, accountId, categoryId, date, note, deleted, updatedAt, updatedBy) " +
                    "VALUES ('old', 'expense', 500, 'acc', 'cat', 1000, '', 0, 1, 'u')",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 10, true, MIGRATION_9_10)

        db.query("SELECT feeMinor FROM transactions WHERE id = 'old'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            // Существующим записям миграция добавляет дефолт: комиссия ноль.
            assertEquals(0L, cursor.getLong(0))
        }
        // Новая схема хранит комиссию у ноги списания рядом с суммой.
        db.execSQL(
            "INSERT INTO transactions " +
                "(id, type, amountMinor, accountId, categoryId, date, note, linkedId, counterpartyAccountId, feeMinor, deleted, updatedAt, updatedBy) " +
                "VALUES ('fee', 'transfer_out', 11100, 'acc', '', 2000, '', 'L1', 'acc2', 1100, 0, 2, 'u')",
        )
        db.query("SELECT amountMinor, feeMinor FROM transactions WHERE id = 'fee'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(11100L, cursor.getLong(0))
            assertEquals(1100L, cursor.getLong(1))
        }
        db.close()
    }

    private companion object {
        const val DB_NAME = "migration-9-10-test"
    }
}
