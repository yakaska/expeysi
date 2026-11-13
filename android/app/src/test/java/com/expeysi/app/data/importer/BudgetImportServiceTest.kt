package com.expeysi.app.data.importer

import android.content.SharedPreferences
import com.expeysi.app.data.SelectedBalanceStore
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.data.sync.SyncTrigger
import com.expeysi.app.domain.TxType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Перекраска существующих категорий при импорте: файл экспорта Expeysi обязан
 * возвращать оформление, файлы внешнего «Бюджета» его не трогают.
 * Имена копий балансов: префикс «импорт » и нумерация повторов.
 * Сквозной импорт: все строки уходят в Room dirty, отправку двигает триггер.
 */
class BudgetImportServiceTest {

    private val existing = CategoryEntity(
        id = "c1",
        name = "Еда",
        iconKey = "category",
        colorKey = "default",
        type = TxType.EXPENSE.wire,
        updatedAt = 1,
    )

    @Test
    fun `явные иконка и цвет перекрашивают существующую категорию`() {
        val updated = restyledCategory(
            existing,
            tx(categoryIconKey = "restaurant", categoryColorKey = "orange"),
            nowMillis = 77,
        )!!

        assertEquals("c1", updated.id)
        assertEquals("restaurant", updated.iconKey)
        assertEquals("orange", updated.colorKey)
        assertEquals(77, updated.updatedAt)
    }

    @Test
    fun `пустое оформление чужого файла категорию не меняет`() {
        assertNull(restyledCategory(existing, tx(categoryIconKey = null, categoryColorKey = null), nowMillis = 77))
        assertUnchanged(existing)
    }

    @Test
    fun `совпадающее оформление возвращает null`() {
        assertNull(
            restyledCategory(
                existing,
                tx(categoryIconKey = "category", categoryColorKey = "default"),
                nowMillis = 77,
            ),
        )
        assertUnchanged(existing)
    }

    @Test
    fun `частичное оформление обновляет только заданный атрибут`() {
        val onlyIcon = restyledCategory(existing, tx(categoryIconKey = "restaurant", categoryColorKey = null), nowMillis = 77)!!
        assertEquals("restaurant", onlyIcon.iconKey)
        assertEquals("default", onlyIcon.colorKey)
        assertEquals(77, onlyIcon.updatedAt)

        val onlyColor = restyledCategory(existing, tx(categoryIconKey = null, categoryColorKey = "orange"), nowMillis = 77)!!
        assertEquals("category", onlyColor.iconKey)
        assertEquals("orange", onlyColor.colorKey)
    }

    private fun assertUnchanged(category: CategoryEntity) {
        assertEquals(1, category.updatedAt)
    }

    @Test
    fun `номер копии идёт за максимальным занятым`() {
        assertEquals(
            "импорт Dong (3)",
            importedBalanceName("Dong", setOf("Dong", "импорт Dong", "импорт Dong (2)")),
        )
    }

    @Test
    fun `свободное имя берется без номера`() {
        // Свой баланс «Dong» не мешает: счёт только по копиям с префиксом;
        // мусор в скобках и имена с похожими префиксами нумерацию не сбивают.
        assertEquals("импорт Dong", importedBalanceName("Dong", setOf("Dong", "импорт Dong (x)", "импорт Dong2")))
        assertEquals("импорт Dong", importedBalanceName("Dong", setOf("Dong", "импорт Dong (2)")))
    }

    @Test
    fun `имя со скобками в основе номеруется корректно`() {
        assertEquals(
            "импорт Бюджет (RUB) (2)",
            importedBalanceName("Бюджет (RUB)", setOf("импорт Бюджет (RUB)")),
        )
    }

    @Test
    fun `импорт пишет все строки dirty и один раз запускает рабочий цикл`() = runTest {
        val writer = FakeImportWriter()
        val trigger = FakeSyncTrigger()
        val store = SelectedBalanceStore(FakeSharedPreferences())
        val service = BudgetImportService(
            balanceDao = FakeBalanceDao(),
            categoryDao = FakeCategoryDao(),
            parser = BudgetCsvParser(),
            importWriter = writer,
            syncTrigger = trigger,
            selectedBalanceStore = store,
        )

        val result = service.import(csv.byteInputStream())

        // Все импортированные строки уходят dirty: отправку делает рабочий
        // цикл SyncEngine, а не импортёр.
        assertTrue(writer.balances.all { it.dirty })
        assertTrue(writer.accounts.all { it.dirty })
        assertTrue(writer.categories.all { it.dirty })
        assertTrue(writer.transactions.all { it.dirty })
        assertEquals(1, writer.balances.size)
        assertEquals(1, writer.accounts.size)
        assertEquals(2, writer.categories.size)
        assertEquals(2, writer.transactions.size)
        assertEquals(2, result.transactionCount)
        assertEquals("импорт Бюджет (RUB)", writer.balances.single().name)
        assertEquals(writer.balances.single().id, result.balanceId)
        assertEquals(result.balanceId, store.selectedId.value)
        assertEquals(1, trigger.count)
    }

    private fun tx(categoryIconKey: String?, categoryColorKey: String?) = BudgetTransaction(
        accountName = "Карта",
        categoryName = "Еда",
        type = TxType.EXPENSE,
        amountMinor = 100L,
        currencyCode = "RUB",
        date = 0,
        note = "",
        categoryIconKey = categoryIconKey,
        categoryColorKey = categoryColorKey,
    )

    // Фикстуры и фейки сквозного импорта.

    /** Минимальный файл «Бюджета»: расходы и доходы по одному счету. */
    private val csv = """
        Дата;Время;Название счета;Категория;Сумма;Валюта;Комментарий
        2024-01-01;12:00;Карта;Еда;-100.50;RUB;покупка
        2024-01-02;09:30;Карта;Зарплата;200.75;RUB;аванс
    """.trimIndent()

    private class FakeImportWriter : ImportWriter {
        val balances = mutableListOf<BalanceEntity>()
        val accounts = mutableListOf<AccountEntity>()
        val categories = mutableListOf<CategoryEntity>()
        val transactions = mutableListOf<TransactionEntity>()

        override suspend fun write(
            balances: List<BalanceEntity>,
            accounts: List<AccountEntity>,
            categories: List<CategoryEntity>,
            transactions: List<TransactionEntity>,
        ) {
            this.balances += balances
            this.accounts += accounts
            this.categories += categories
            this.transactions += transactions
        }
    }

    private class FakeSyncTrigger : SyncTrigger {
        var count = 0
        override fun trigger() {
            count++
        }
    }

    private class FakeBalanceDao : BalanceDao {
        private val rows = mutableMapOf<String, BalanceEntity>()

        override suspend fun upsert(balance: BalanceEntity) {
            rows[balance.id] = balance
        }

        override suspend fun upsertAll(balances: List<BalanceEntity>) = balances.forEach { upsert(it) }

        override fun observeActive(): Flow<List<BalanceEntity>> = flowOf(rows.values.filter { !it.deleted })

        override fun observeAll(): Flow<List<BalanceEntity>> = flowOf(rows.values.toList())

        override suspend fun byId(id: String): BalanceEntity? = rows[id]

        override suspend fun listDirty(): List<BalanceEntity> = rows.values.filter { it.dirty }

        override suspend fun markClean(id: String, updatedAt: Long): Int {
            val current = rows[id] ?: return 0
            if (current.updatedAt != updatedAt) return 0
            rows[id] = current.copy(dirty = false)
            return 1
        }

        override suspend fun markDirty(id: String) {
            rows[id]?.let { rows[id] = it.copy(dirty = true) }
        }

        override suspend fun clear() {
            rows.clear()
        }
    }

    private class FakeCategoryDao : CategoryDao {
        private val rows = mutableMapOf<String, CategoryEntity>()

        override suspend fun upsertAll(categories: List<CategoryEntity>) = categories.forEach { upsert(it) }

        override suspend fun upsert(category: CategoryEntity) {
            rows[category.id] = category
        }

        override fun observeActive(type: String): Flow<List<CategoryEntity>> = flowOf(rows.values.filter { !it.deleted && it.type == type })

        override suspend fun listActive(type: String): List<CategoryEntity> = rows.values.filter { !it.deleted && it.type == type }

        override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(rows.values.toList())

        override suspend fun byId(id: String): CategoryEntity? = rows[id]

        override suspend fun listDirty(): List<CategoryEntity> = rows.values.filter { it.dirty }

        override suspend fun markClean(id: String, updatedAt: Long): Int {
            val current = rows[id] ?: return 0
            if (current.updatedAt != updatedAt) return 0
            rows[id] = current.copy(dirty = false)
            return 1
        }

        override suspend fun markDirty(id: String) {
            rows[id]?.let { rows[id] = it.copy(dirty = true) }
        }

        override suspend fun clear() {
            rows.clear()
        }
    }

    /** Полная реализация предпочтений без Android-рантайма. */
    private class FakeSharedPreferences : SharedPreferences {
        val values = mutableMapOf<String, String>()
        private val pending = mutableMapOf<String, String?>()

        override fun getAll(): Map<String, *> = values
        override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = values[key]?.split(",")?.toSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class FakeEditor : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = apply { pending[key] = values?.joinToString(",") }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value.toString() }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value.toString() }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value.toString() }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value.toString() }
            override fun remove(key: String): SharedPreferences.Editor = apply { pending[key] = null }
            override fun clear(): SharedPreferences.Editor = apply { pending.clear() }
            override fun commit(): Boolean {
                applyChanges()
                return true
            }

            override fun apply() {
                applyChanges()
            }

            private fun applyChanges() {
                pending.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                pending.clear()
            }
        }
    }
}
