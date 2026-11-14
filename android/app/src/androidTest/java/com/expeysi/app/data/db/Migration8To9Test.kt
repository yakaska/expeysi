package com.expeysi.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Миграция 8→9 против коммитнутых схем: v8 собирается из assets по `8.json`,
 * накатываются только миграции приложения, валидация сверяет результат с
 * `9.json`. Схемы лежат ассетами androidTest (`schemas/` в build.gradle.kts).
 */
@RunWith(AndroidJUnit4::class)
class Migration8To9Test {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    // Задача D8 запрещает пробелы в именах методов androidTest (в отличие от JVM-тестов).
    @Test
    fun migration8To9_oldRowsGetEmptyTransferFields_newLegInserts() {
        helper.createDatabase(DB_NAME, 8).use { db ->
            db.execSQL(
                "INSERT INTO transactions (id, type, amountMinor, accountId, categoryId, date, note, deleted, updatedAt, updatedBy) " +
                    "VALUES ('old', 'expense', 500, 'acc', 'cat', 1000, '', 0, 1, 'u')",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 9, true, MIGRATION_8_9)

        db.query("SELECT linkedId, counterpartyAccountId FROM transactions WHERE id = 'old'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            // Существующим записям миграция добавляет дефолт: обе пустые строки.
            assertEquals("", cursor.getString(0))
            assertEquals("", cursor.getString(1))
        }
        // Новая схема принимает строку с заполненными полями перевода.
        db.execSQL(
            "INSERT INTO transactions " +
                "(id, type, amountMinor, accountId, categoryId, date, note, linkedId, counterpartyAccountId, deleted, updatedAt, updatedBy) " +
                "VALUES ('new', 'transfer_out', 300, 'acc', '', 2000, '', 'L1', 'acc2', 0, 2, 'u')",
        )
        db.query("SELECT linkedId, counterpartyAccountId FROM transactions WHERE id = 'new'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("L1", cursor.getString(0))
            assertEquals("acc2", cursor.getString(1))
        }
        db.close()
    }

    private companion object {
        const val DB_NAME = "migration-8-9-test"
    }
}
