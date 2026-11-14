package com.expeysi.app.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.expeysi.app.data.seed.CurrencySeed
import com.expeysi.app.data.seed.PresetData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Полный путь 2→10: база собирается из самой старой коммитнутой схемы (2.json)
 * и открывается через databaseBuilder со всем списком ALL_MIGRATIONS — тем же,
 * что регистрирует DataModule. Путь 1→2 не входит: `1.json` не коммитился.
 */
@RunWith(AndroidJUnit4::class)
class MigrationChainTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun fullPath2ToLatest_allMigrationsOpen_keepsRows_daosWork() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL(
                "INSERT INTO accounts (id, name, iconKey, openingBalanceMinor, currencyCode, deleted, updatedAt) " +
                    "VALUES ('${PresetData.ACCOUNT_CASH_ID}', 'Наличные', 'payments', 1500, 'RUB', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO categories (id, name, iconKey, type, deleted, updatedAt) " +
                    "VALUES ('$CC_CATEGORY_ID', 'Спортпит', 'fitness_center', 'expense', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO transactions (id, type, amountMinor, accountId, categoryId, date, note, deleted, updatedAt, updatedBy) " +
                    "VALUES ('t1', 'expense', 500, '${PresetData.ACCOUNT_CASH_ID}', '$CC_CATEGORY_ID', 1000, '', 0, 1, 'u')",
            )
        }

        // Тот же список миграций, что у DataModule, — один общий ALL_MIGRATIONS.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

        runBlocking {
            // Первый DAO-запрос открывает базу и прогоняет все миграции 2→10.
            val account = db.accountDao().byId(PresetData.ACCOUNT_CASH_ID)
            assertNotNull(account)
            // 9→10 пересчитал масштаб сумм (сотые доли → десятитысячные, ×100),
            // метки времени не тронул: журнал пересчитывается при чтении.
            assertEquals(150_000L, account!!.openingBalanceMinor)
            assertEquals(1L, account.updatedAt)
            assertEquals("RUB", account.currencyCode)
            // 3→4 привязал счёт к созданному под его валюту балансу.
            assertEquals(PresetData.BALANCE_MAIN_ID, account.balanceId)

            val transaction = db.transactionDao().byId("t1")
            assertNotNull(transaction)
            assertEquals(50_000L, transaction!!.amountMinor)
            assertEquals(1L, transaction.updatedAt)
            assertEquals(PresetData.ACCOUNT_CASH_ID, transaction.accountId)
            // 8→9 добавил полям перевода пустые дефолты у обычных операций.
            assertEquals("", transaction.linkedId)
            assertEquals("", transaction.counterpartyAccountId)

            val category = db.categoryDao().byId(CC_CATEGORY_ID)
            assertNotNull(category)
            assertEquals("Спортпит", category!!.name)

            // Баланс создан миграцией 3→4, раскрашен 6→7.
            val balance = db.balanceDao().byId(PresetData.BALANCE_MAIN_ID)
            assertNotNull(balance)
            assertEquals("payments", balance!!.iconKey)
            assertEquals("blue", balance.colorKey)

            // Справочник валют засеян миграцией 2→3.
            assertEquals(CurrencySeed.all.size, db.currencyDao().listAll().size)

            // Свои строки на месте; миграции добавили только пресеты и сиды.
            assertEquals(1, count(db, "accounts"))
            assertEquals(1, count(db, "transactions"))
            assertEquals(1, count(db, "balances"))
            assertEquals(PresetData.categories.size + 1, count(db, "categories"))
        }
        db.close()
    }

    private fun count(db: AppDatabase, table: String): Int = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    private companion object {
        const val DB_NAME = "migration-chain-test"
        const val CC_CATEGORY_ID = "cc000000-0000-4000-8000-000000000001"
    }
}
