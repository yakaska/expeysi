package com.expeysi.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Миграция 10→11 против коммитнутых схем: v10 собирается из assets по `10.json`,
 * накатываются только миграции приложения, валидация сверяет результат с
 * `11.json`. Схемы лежат ассетами androidTest (`schemas/` в build.gradle.kts).
 */
@RunWith(AndroidJUnit4::class)
class Migration10To11Test {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    // Задача D8 запрещает пробелы в именах методов androidTest (в отличие от JVM-тестов).
    @Test
    fun migration10To11_oldRowsGetEmptyCounterpartyAndNullDueDate_newTablesReadable() {
        helper.createDatabase(DB_NAME, 10).use { db ->
            db.execSQL(
                "INSERT INTO transactions " +
                    "(id, type, amountMinor, accountId, categoryId, date, note, linkedId, counterpartyAccountId, feeMinor, deleted, updatedAt, updatedBy) " +
                    "VALUES ('old', 'expense', 500, 'acc', 'cat', 1000, '', '', '', 0, 0, 1, 'u')",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 11, true, MIGRATION_10_11)

        db.query("SELECT counterpartyId, dueDate FROM transactions WHERE id = 'old'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            // Существующим записям миграция ставит дефолты: контрагента нет, срок неизвестен.
            assertEquals("", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        // Новые таблицы читаются и принимают записи обеих сторон.
        db.execSQL(
            "INSERT INTO counterparties (id, name, type, note, archived, deleted, updatedAt) " +
                "VALUES ('c1', 'Иван', 'person', '', 0, 0, 1)",
        )
        db.execSQL(
            "INSERT INTO debt_adjustments " +
                "(id, counterpartyId, kind, amountMinor, currencyCode, date, note, countInStats, deleted, updatedAt) " +
                "VALUES ('a1', 'c1', 'opening_balance', 150000, 'RUB', 2, '', 0, 0, 2)",
        )
        db.query("SELECT name, type FROM counterparties WHERE id = 'c1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Иван", cursor.getString(0))
            assertEquals("person", cursor.getString(1))
        }
        db.query("SELECT amountMinor, countInStats FROM debt_adjustments WHERE id = 'a1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(150000L, cursor.getLong(0))
            assertEquals(0, cursor.getInt(1))
        }
        db.close()
    }

    private companion object {
        const val DB_NAME = "migration-10-11-test"
    }
}
