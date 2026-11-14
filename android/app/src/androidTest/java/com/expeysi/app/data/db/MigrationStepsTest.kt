package com.expeysi.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.expeysi.app.data.seed.CurrencySeed
import com.expeysi.app.data.seed.PresetData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Пошаговые миграции 2→8 против коммитнутых схем (8→9 — в Migration8To9Test,
 * 9→10 — в Migration9To10Test; 1→2 не тестируется: `1.json` в репозитории нет).
 * База старой версии собирается из `N.json`, сырым SQL вставляются строки,
 * `runMigrationsAndValidate` накатывает миграцию, затем тесты сверяют выжившие
 * строки, дефолты и данные, которые миграция дозаполняет. Имена методов без
 * пробелов: D8 так требует.
 */
@RunWith(AndroidJUnit4::class)
class MigrationStepsTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        DB_NAMES.forEach { context.deleteDatabase(it) }
    }

    @Test
    fun migration2To3_seedsCurrencyReference() {
        helper.createDatabase(DB_2_3, 2).use { db ->
            db.execSQL(
                "INSERT INTO accounts (id, name, iconKey, openingBalanceMinor, currencyCode, deleted, updatedAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000001', 'Наличные', 'payments', 1500, 'RUB', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO categories (id, name, iconKey, type, deleted, updatedAt) " +
                    "VALUES ('cc000000-0000-4000-8000-000000000001', 'Спортпит', 'fitness_center', 'expense', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO transactions (id, type, amountMinor, accountId, categoryId, date, note, deleted, updatedAt, updatedBy) " +
                    "VALUES ('t1', 'expense', 500, '00000000-0000-4000-8000-000000000001', " +
                    "'cc000000-0000-4000-8000-000000000001', 1000, '', 0, 1, 'u')",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_2_3, 3, true, MIGRATION_2_3)

        // Справочник валют засеян целиком: количество и содержимое — по сиду.
        db.query("SELECT code, name, symbol FROM currencies").use { cursor ->
            val rows = buildList {
                while (cursor.moveToNext()) {
                    add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                }
            }
            assertEquals(CurrencySeed.all.size, rows.size)
            CurrencySeed.all.forEach { seed ->
                val row = rows.first { it.first == seed.code }
                assertEquals(seed.name, row.second)
                assertEquals(seed.symbol, row.third)
            }
        }
        // Фоновые строки не пострадали.
        assertEquals(1, count(db, "accounts"))
        assertEquals(1, count(db, "categories"))
        assertEquals(1, count(db, "transactions"))
        db.close()
    }

    @Test
    fun migration3To4_createsBalancesAndLinksAccounts() {
        helper.createDatabase(DB_3_4, 3).use { db ->
            db.execSQL(
                "INSERT INTO accounts (id, name, iconKey, openingBalanceMinor, currencyCode, deleted, updatedAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000001', 'Наличные', 'payments', 0, 'RUB', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO accounts (id, name, iconKey, openingBalanceMinor, currencyCode, deleted, updatedAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000002', 'Карта', 'credit_card', 0, 'RUB', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO accounts (id, name, iconKey, openingBalanceMinor, currencyCode, deleted, updatedAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000099', 'Кошелек', 'savings', 0, 'USD', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO categories (id, name, iconKey, type, deleted, updatedAt) " +
                    "VALUES ('cc000000-0000-4000-8000-000000000001', 'Спортпит', 'fitness_center', 'expense', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO transactions (id, type, amountMinor, accountId, categoryId, date, note, deleted, updatedAt, updatedBy) " +
                    "VALUES ('t1', 'expense', 500, '00000000-0000-4000-8000-000000000001', " +
                    "'cc000000-0000-4000-8000-000000000001', 1000, '', 0, 1, 'u')",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_3_4, 4, true, MIGRATION_3_4)

        // На каждую валюту существующих счетов — один баланс с детерминированным id.
        val usdBalanceId = UUID.nameUUIDFromBytes("expeysi-balance-USD".toByteArray()).toString()
        assertEquals(2, count(db, "balances"))
        db.query("SELECT id, name, deleted FROM balances WHERE currencyCode = 'RUB'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(PresetData.BALANCE_MAIN_ID, cursor.getString(0))
            assertEquals("Основной", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }
        db.query("SELECT id, name, deleted FROM balances WHERE currencyCode = 'USD'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(usdBalanceId, cursor.getString(0))
            assertEquals("Баланс USD", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }
        // Счета привязаны к балансу своей валюты, оба рублевых — к одному.
        assertEquals(
            PresetData.BALANCE_MAIN_ID,
            stringOf(db, "SELECT balanceId FROM accounts WHERE id = '00000000-0000-4000-8000-000000000001'"),
        )
        assertEquals(
            PresetData.BALANCE_MAIN_ID,
            stringOf(db, "SELECT balanceId FROM accounts WHERE id = '00000000-0000-4000-8000-000000000002'"),
        )
        assertEquals(
            usdBalanceId,
            stringOf(db, "SELECT balanceId FROM accounts WHERE id = '00000000-0000-4000-8000-000000000099'"),
        )
        // Фон на месте.
        assertEquals(3, count(db, "accounts"))
        assertEquals(1, count(db, "categories"))
        assertEquals(1, count(db, "transactions"))
        db.close()
    }

    @Test
    fun migration4To5_backfillsCategoryColorsAndAddsPresets() {
        helper.createDatabase(DB_4_5, 4).use { db ->
            // Непереименованный пресет «Продукты»: миграция переименует в «Еда» и покрасит.
            db.execSQL(
                "INSERT INTO categories (id, name, iconKey, type, deleted, updatedAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000101', 'Продукты', 'cart', 'expense', 0, 1)",
            )
            // Старый пресет из легаси-карты цветов: получает свой цвет.
            db.execSQL(
                "INSERT INTO categories (id, name, iconKey, type, deleted, updatedAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000104', 'Кафе', 'local_cafe', 'expense', 0, 1)",
            )
            // Своя категория пользователя: имя и иконка остаются, цвет — дефолт.
            db.execSQL(
                "INSERT INTO categories (id, name, iconKey, type, deleted, updatedAt) " +
                    "VALUES ('cc000000-0000-4000-8000-000000000001', 'Спортпит', 'fitness_center', 'expense', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO accounts (id, name, iconKey, openingBalanceMinor, currencyCode, balanceId, deleted, updatedAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000001', 'Наличные', 'payments', 0, 'RUB', '', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO transactions (id, type, amountMinor, accountId, categoryId, date, note, deleted, updatedAt, updatedBy) " +
                    "VALUES ('t1', 'expense', 500, '00000000-0000-4000-8000-000000000001', " +
                    "'cc000000-0000-4000-8000-000000000001', 1000, '', 0, 1, 'u')",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_4_5, 5, true, MIGRATION_4_5)

        db.query(
            "SELECT name, iconKey, colorKey FROM categories " +
                "WHERE id = '00000000-0000-4000-8000-000000000101'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Еда", cursor.getString(0))
            assertEquals("restaurant", cursor.getString(1))
            assertEquals("orange", cursor.getString(2))
        }
        assertEquals(
            "orange",
            stringOf(db, "SELECT colorKey FROM categories WHERE id = '00000000-0000-4000-8000-000000000104'"),
        )
        db.query("SELECT name, iconKey, colorKey FROM categories WHERE id = 'cc000000-0000-4000-8000-000000000001'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Спортпит", cursor.getString(0))
                assertEquals("fitness_center", cursor.getString(1))
                assertEquals("default", cursor.getString(2))
            }
        // Недостающий пресет приехал со своим цветом (INSERT OR IGNORE не тронул чужие).
        assertEquals(
            "purple",
            stringOf(db, "SELECT colorKey FROM categories WHERE id = '00000000-0000-4000-8000-000000000108'"),
        )
        // Три своих строки плюс недостающие пресеты; «Продукты» уже были — игнор.
        assertEquals(PresetData.categories.size + 2, count(db, "categories"))
        assertEquals(1, count(db, "accounts"))
        assertEquals(1, count(db, "transactions"))
        db.close()
    }

    @Test
    fun migration5To6_backfillsAccountColors() {
        helper.createDatabase(DB_5_6, 5).use { db ->
            db.execSQL(
                "INSERT INTO balances (id, name, currencyCode, deleted, updatedAt) " +
                    "VALUES ('${PresetData.BALANCE_MAIN_ID}', 'Основной', 'RUB', 0, 1)",
            )
            // Счета без colorKey: пресетные красятся по сиду, свои — дефолтом.
            db.execSQL(
                "INSERT INTO accounts (id, name, iconKey, openingBalanceMinor, currencyCode, balanceId, deleted, updatedAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000001', 'Наличные', 'payments', 0, 'RUB', " +
                    "'${PresetData.BALANCE_MAIN_ID}', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO accounts (id, name, iconKey, openingBalanceMinor, currencyCode, balanceId, deleted, updatedAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000002', 'Карта', 'credit_card', 0, 'RUB', " +
                    "'${PresetData.BALANCE_MAIN_ID}', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO accounts (id, name, iconKey, openingBalanceMinor, currencyCode, balanceId, deleted, updatedAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000099', 'Копилка', 'savings', 0, 'RUB', '', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO categories (id, name, iconKey, type, colorKey, deleted, updatedAt) " +
                    "VALUES ('cc000000-0000-4000-8000-000000000001', 'Спортпит', 'fitness_center', 'expense', 'default', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO transactions (id, type, amountMinor, accountId, categoryId, date, note, deleted, updatedAt, updatedBy) " +
                    "VALUES ('t1', 'expense', 500, '00000000-0000-4000-8000-000000000001', " +
                    "'cc000000-0000-4000-8000-000000000001', 1000, '', 0, 1, 'u')",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_5_6, 6, true, MIGRATION_5_6)

        assertEquals(
            "green",
            stringOf(db, "SELECT colorKey FROM accounts WHERE id = '${PresetData.ACCOUNT_CASH_ID}'"),
        )
        assertEquals(
            "blue",
            stringOf(db, "SELECT colorKey FROM accounts WHERE id = '${PresetData.ACCOUNT_CARD_ID}'"),
        )
        assertEquals(
            "default",
            stringOf(db, "SELECT colorKey FROM accounts WHERE id = '00000000-0000-4000-8000-000000000099'"),
        )
        // Фон не пострадал.
        assertEquals(1, count(db, "balances"))
        assertEquals(3, count(db, "accounts"))
        assertEquals(1, count(db, "categories"))
        assertEquals(1, count(db, "transactions"))
        db.close()
    }

    @Test
    fun migration6To7_backfillsBalanceIconAndColor() {
        helper.createDatabase(DB_6_7, 6).use { db ->
            // Балансы без iconKey и colorKey.
            db.execSQL(
                "INSERT INTO balances (id, name, currencyCode, deleted, updatedAt) " +
                    "VALUES ('${PresetData.BALANCE_MAIN_ID}', 'Основной', 'RUB', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO balances (id, name, currencyCode, deleted, updatedAt) " +
                    "VALUES ('balance-usd', 'Копилка', 'USD', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO accounts (id, name, iconKey, colorKey, openingBalanceMinor, currencyCode, balanceId, deleted, updatedAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000001', 'Наличные', 'payments', 'green', 0, 'RUB', " +
                    "'${PresetData.BALANCE_MAIN_ID}', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO categories (id, name, iconKey, type, colorKey, deleted, updatedAt) " +
                    "VALUES ('cc000000-0000-4000-8000-000000000001', 'Спортпит', 'fitness_center', 'expense', 'default', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO transactions (id, type, amountMinor, accountId, categoryId, date, note, deleted, updatedAt, updatedBy) " +
                    "VALUES ('t1', 'expense', 500, '00000000-0000-4000-8000-000000000001', " +
                    "'cc000000-0000-4000-8000-000000000001', 1000, '', 0, 1, 'u')",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_6_7, 7, true, MIGRATION_6_7)

        db.query("SELECT iconKey, colorKey FROM balances WHERE id = '${PresetData.BALANCE_MAIN_ID}'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("payments", cursor.getString(0))
            assertEquals("blue", cursor.getString(1))
        }
        db.query("SELECT iconKey, colorKey FROM balances WHERE id = 'balance-usd'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            // Свой баланс получает дефолты из ALTER TABLE.
            assertEquals("", cursor.getString(0))
            assertEquals("default", cursor.getString(1))
        }
        // Фон не пострадал.
        assertEquals(2, count(db, "balances"))
        assertEquals(1, count(db, "accounts"))
        assertEquals(1, count(db, "transactions"))
        db.close()
    }

    @Test
    fun migration7To8_alignsCurrencySymbolsWithSeed() {
        helper.createDatabase(DB_7_8, 7).use { db ->
            db.execSQL(
                "INSERT INTO accounts (id, name, iconKey, colorKey, openingBalanceMinor, currencyCode, balanceId, deleted, updatedAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000001', 'Наличные', 'payments', 'default', 0, 'RUB', '', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO categories (id, name, iconKey, type, colorKey, deleted, updatedAt) " +
                    "VALUES ('cc000000-0000-4000-8000-000000000001', 'Спортпит', 'fitness_center', 'expense', 'default', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO transactions (id, type, amountMinor, accountId, categoryId, date, note, deleted, updatedAt, updatedBy) " +
                    "VALUES ('t1', 'expense', 500, '00000000-0000-4000-8000-000000000001', " +
                    "'cc000000-0000-4000-8000-000000000001', 1000, '', 0, 1, 'u')",
            )
            // Символы от старого сида: getSymbol(Locale.ROOT) вернул ISO-коды вместо глифов.
            db.execSQL("INSERT INTO currencies (code, name, symbol) VALUES ('RUB', 'Russian Ruble', 'RUB')")
            db.execSQL("INSERT INTO currencies (code, name, symbol) VALUES ('USD', 'US Dollar', 'USD')")
            db.execSQL("INSERT INTO currencies (code, name, symbol) VALUES ('EUR', 'Euro', 'EUR')")
            // Кода нет в сиде: миграция его не трогает.
            db.execSQL("INSERT INTO currencies (code, name, symbol) VALUES ('ZZZ', 'Fake Currency', 'X')")
        }

        val db = helper.runMigrationsAndValidate(DB_7_8, 8, true, MIGRATION_7_8)

        assertEquals("RUB".symbolBySeed(), stringOf(db, "SELECT symbol FROM currencies WHERE code = 'RUB'"))
        assertEquals("USD".symbolBySeed(), stringOf(db, "SELECT symbol FROM currencies WHERE code = 'USD'"))
        assertEquals("EUR".symbolBySeed(), stringOf(db, "SELECT symbol FROM currencies WHERE code = 'EUR'"))
        assertEquals("X", stringOf(db, "SELECT symbol FROM currencies WHERE code = 'ZZZ'"))
        // Фон не пострадал.
        assertEquals(4, count(db, "currencies"))
        assertEquals(1, count(db, "accounts"))
        assertEquals(1, count(db, "transactions"))
        db.close()
    }

    private fun String.symbolBySeed(): String = CurrencySeed.all.first { it.code == this }.symbol

    private fun count(db: SupportSQLiteDatabase, table: String): Int = db.query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    private fun stringOf(db: SupportSQLiteDatabase, sql: String): String? = db.query(sql).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private companion object {
        const val DB_2_3 = "migration-2-3-test"
        const val DB_3_4 = "migration-3-4-test"
        const val DB_4_5 = "migration-4-5-test"
        const val DB_5_6 = "migration-5-6-test"
        const val DB_6_7 = "migration-6-7-test"
        const val DB_7_8 = "migration-7-8-test"
        val DB_NAMES = listOf(DB_2_3, DB_3_4, DB_4_5, DB_5_6, DB_6_7, DB_7_8)
    }
}
