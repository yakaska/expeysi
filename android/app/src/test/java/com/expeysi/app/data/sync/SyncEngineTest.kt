package com.expeysi.app.data.sync

import com.expeysi.app.data.FamilyStore
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.data.remote.AuthProvider
import com.expeysi.app.data.remote.RemoteUpdate
import com.expeysi.app.data.remote.SyncChange
import com.expeysi.app.data.remote.SyncEntityType
import com.expeysi.app.data.remote.SyncKey
import com.expeysi.app.data.remote.SyncMapper
import com.expeysi.app.data.remote.SyncRemote
import com.expeysi.app.data.remote.firebase.firestoreBatches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Поведение SyncEngine на интерфейсах: фейковый SyncRemote плюс фейки DAO,
 * повторяющие SQL-контракты (условный markClean по updatedAt). Существующие
 * проверки чистых функций оставлены без изменений.
 */
class SyncEngineTest {

    @Test
    fun `отсутствие версии удалённой строки требует отправки`() {
        assertTrue(needsUpload(localUpdatedAt = 100, remoteUpdatedAt = null))
    }

    @Test
    fun `локаль новее удалённой версии требует отправки`() {
        assertTrue(needsUpload(localUpdatedAt = 101, remoteUpdatedAt = 100))
    }

    @Test
    fun `равные версии не требуют отправки`() {
        assertFalse(needsUpload(localUpdatedAt = 100, remoteUpdatedAt = 100))
    }

    @Test
    fun `удалённая версия новее не требует отправки`() {
        assertFalse(needsUpload(localUpdatedAt = 100, remoteUpdatedAt = 101))
    }

    @Test
    fun `батчи ровно по 500 документов Firestore`() {
        assertEquals(listOf(500, 1), firestoreBatches(List(501) { it }).map { it.size })
        assertEquals(listOf(500), firestoreBatches(List(500) { it }).map { it.size })
        assertEquals(emptyList<List<Int>>(), firestoreBatches(emptyList<Int>()))
    }

    // Тесты движка на фейках: слияние по updatedAt (инвариант 1), первичная
    // сверка (инвариант 4), перенос dirty-строк и загрузка импорта (инвариант 5).

    @Test
    fun `снимок новее локали заменяет строку с дефолтами wire-формата`() = runTest {
        seedCatalogs(balance = balanceRow(name = "Старое имя", updatedAt = 50))
        remote.setInitialUpdates(initialUpdates())
        engine().runFamilySync(FAMILY_ID)
        val row = balanceDao.rows.value.single()
        assertEquals(100L, row.updatedAt)
        assertEquals("Продукты", row.name)
        assertEquals("RUB", row.currencyCode)
        assertEquals("", row.iconKey)
        assertEquals("default", row.colorKey)
        assertFalse(row.deleted)
        assertTrue(remote.posted.isEmpty())
    }

    @Test
    fun `локаль новее снимка отправляется и не перезаписывается`() = runTest {
        seedCatalogs(balance = balanceRow(name = "Моё имя", updatedAt = 100))
        remote.setInitialUpdates(
            initialUpdates(
                balanceChanges = listOf(snapshotChange(SyncEntityType.BALANCE, "b1", 50, "Чужое старое имя")),
            ),
        )
        engine().runFamilySync(FAMILY_ID)
        val row = balanceDao.rows.value.single()
        assertEquals("Моё имя", row.name)
        assertEquals(100L, row.updatedAt)
        // И слияние, и первичная сверка считают строку новее транспорта —
        // обе отправляют, как и прежний движок (set по id идемпотентен).
        assertEquals(2, remote.posted.size)
        assertTrue(remote.posted.all { it.updatedAt == 100L })
        assertTrue(remote.posted.all { it.fields["name"] == "Моё имя" })
    }

    @Test
    fun `равные updatedAt — строка не отправляется и не перезаписывается`() = runTest {
        seedCatalogs(balance = balanceRow(name = "Моё имя", updatedAt = 100))
        remote.setInitialUpdates(
            initialUpdates(
                balanceChanges = listOf(snapshotChange(SyncEntityType.BALANCE, "b1", 100, "Иное имя")),
            ),
        )
        engine().runFamilySync(FAMILY_ID)
        assertTrue(remote.posted.isEmpty())
        assertEquals("Моё имя", balanceDao.rows.value.single().name)
    }

    @Test
    fun `первичная сверка отправляет строки, отсутствующие в снимках`() = runTest {
        seedCatalogs(
            balance = balanceRow(updatedAt = 300),
            account = accountRow(updatedAt = 300),
            category = categoryRow(updatedAt = 300),
        )
        transactionDao.rows.value = listOf(transactionRow(updatedAt = 300))
        remote.setInitialUpdates(initialUpdates(allEmpty = true))
        engine().runFamilySync(FAMILY_ID)
        assertEquals(
            listOf(
                SyncKey(SyncEntityType.BALANCE, "b1"),
                SyncKey(SyncEntityType.ACCOUNT, "a1"),
                SyncKey(SyncEntityType.CATEGORY, "c1"),
                SyncKey(SyncEntityType.TRANSACTION, "t1"),
            ),
            remote.posted.map { it.key },
        )
        assertEquals(300L, remote.posted.first().updatedAt)
        assertEquals(UID, remote.posted.last().fields["updatedBy"])
    }

    @Test
    fun `слияние отдаёт локальную транзакцию с updatedBy текущего пользователя`() = runTest {
        seedCatalogs()
        transactionDao.rows.value = listOf(transactionRow(updatedAt = 100, updatedBy = "old-uid"))
        remote.setInitialUpdates(
            initialUpdates(
                transactionChanges = listOf(staleTransactionChange()),
            ),
        )
        engine().runFamilySync(FAMILY_ID)
        assertTrue(remote.posted.isNotEmpty())
        assertTrue(remote.posted.all { it.fields["updatedBy"] == UID })
        assertEquals(100L, transactionDao.rows.value.single().updatedAt)
    }

    @Test
    fun `requestSync публикует dirty и снимает флаг той же версией`() = runTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        engine().requestSync()
        assertEquals(SyncKey(SyncEntityType.BALANCE, "b1"), remote.posted.single().key)
        assertEquals("b1" to 100L, balanceDao.cleanCalls.single())
        assertFalse(balanceDao.rows.value.single().dirty)
    }

    @Test
    fun `requestSync без выбранной семьи ничего не отправляет и не чистит`() = runTest {
        familyStore.clear()
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        engine().requestSync()
        assertTrue(remote.posted.isEmpty())
        assertTrue(remote.bulkCalls.isEmpty())
        assertTrue(balanceDao.rows.value.single().dirty)
        assertTrue(balanceDao.cleanCalls.isEmpty())
    }

    @Test
    fun `trigger переносит dirty-строки в собственном scope движка`() = runTest {
        transactionDao.rows.value = listOf(transactionRow(updatedAt = 100).copy(dirty = true))
        engine().trigger()
        // trigger() возвращается сразу: перенос идёт в собственном IO-scope движка
        // (сеть вне контекста вызывающего). Ждём фактического завершения ряда:
        // dirty снимется только после отправки и markClean.
        withContext(Dispatchers.IO) {
            withTimeout(2_000) {
                while (transactionDao.rows.value.single().dirty) delay(10)
            }
        }
        assertEquals(SyncKey(SyncEntityType.TRANSACTION, "t1"), remote.posted.single().key)
        assertEquals(UID, remote.posted.single().fields["updatedBy"])
        assertEquals(100L, remote.posted.single().updatedAt)
        assertEquals("t1" to 100L, transactionDao.cleanCalls.single())
        assertFalse(transactionDao.rows.value.single().dirty)
    }

    @Test
    fun `uploadImportedData без семьи не отправляет`() = runTest {
        familyStore.clear()
        engine().uploadImportedData(listOf(balanceRow()), emptyList(), emptyList(), emptyList())
        assertTrue(remote.bulkCalls.isEmpty())
        assertTrue(remote.bulkTimeouts.isEmpty())
    }

    @Test
    fun `uploadImportedData передаёт wire-изменения и таймаут батча`() = runTest {
        val balance = balanceRow(updatedAt = 300)
        val account = accountRow(updatedAt = 300)
        val category = categoryRow(updatedAt = 300)
        val transaction = transactionRow(updatedAt = 300)
        engine().uploadImportedData(listOf(balance), listOf(account), listOf(category), listOf(transaction))
        assertEquals("fam-1", remote.bulkFamilyIds.single())
        assertEquals(listOf(IMPORT_TIMEOUT_MILLIS), remote.bulkTimeouts)
        assertEquals(
            listOf(
                SyncMapper.toChange(balance),
                SyncMapper.toChange(account),
                SyncMapper.toChange(category),
                SyncMapper.toChange(transaction, UID),
            ),
            remote.bulkCalls.single(),
        )
    }

    // Фикстуры и фейки.

    private companion object {
        const val FAMILY_ID = "fam-1"
        const val UID = "uid-1"
        const val IMPORT_TIMEOUT_MILLIS = 20_000L
    }

    private val remote = FakeSyncRemote()
    private val balanceDao = FakeBalanceDao()
    private val accountDao = FakeAccountDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val familyStore = FakeFamilyStore(FAMILY_ID)

    private fun engine() = SyncEngine(
        remote = remote,
        authRepository = FakeAuthProvider(),
        familyStore = familyStore,
        balanceDao = balanceDao,
        accountDao = accountDao,
        categoryDao = categoryDao,
        transactionDao = transactionDao,
    )

    private fun balanceRow(
        name: String = "Продукты",
        updatedAt: Long = 100,
        dirty: Boolean = false,
    ) = BalanceEntity(
        id = "b1",
        name = name,
        currencyCode = "RUB",
        iconKey = "",
        colorKey = "default",
        deleted = false,
        updatedAt = updatedAt,
        dirty = dirty,
    )

    private fun accountRow(updatedAt: Long = 100) = AccountEntity(
        id = "a1",
        name = "Наличные",
        iconKey = "",
        openingBalanceMinor = 0,
        balanceId = "b1",
        deleted = false,
        updatedAt = updatedAt,
    )

    private fun categoryRow(updatedAt: Long = 100) = CategoryEntity(
        id = "c1",
        name = "Еда",
        iconKey = "",
        type = "expense",
        deleted = false,
        updatedAt = updatedAt,
    )

    private fun transactionRow(updatedAt: Long = 100, updatedBy: String = "old-uid") = TransactionEntity(
        id = "t1",
        type = "expense",
        amountMinor = 500,
        accountId = "a1",
        categoryId = "c1",
        date = 0,
        deleted = false,
        updatedAt = updatedAt,
        updatedBy = updatedBy,
    )

    /** Снимок из удалённого журнала: id и поля wire-формата. */
    private fun snapshotChange(type: SyncEntityType, id: String, updatedAt: Long, name: String) = SyncChange(
        key = SyncKey(type, id),
        updatedAt = updatedAt,
        fields = mapOf("name" to name, "updatedAt" to updatedAt),
    )

    /**
     * Первичные снимки всех четырёх типов: справочники согласованы с сидами по
     * умолчанию (версии 100). Изменения транзакций подставляет вызывающий тест.
     */
    private fun initialUpdates(
        balanceChanges: List<SyncChange> = listOf(snapshotChange(SyncEntityType.BALANCE, "b1", 100, "Продукты")),
        accountChanges: List<SyncChange> = listOf(snapshotChange(SyncEntityType.ACCOUNT, "a1", 100, "Наличные")),
        categoryChanges: List<SyncChange> = listOf(snapshotChange(SyncEntityType.CATEGORY, "c1", 100, "Еда")),
        transactionChanges: List<SyncChange> = emptyList(),
        allEmpty: Boolean = false,
    ) = listOf(
        RemoteUpdate(SyncEntityType.BALANCE, isFullState = true, changes = if (allEmpty) emptyList() else balanceChanges),
        RemoteUpdate(SyncEntityType.ACCOUNT, isFullState = true, changes = if (allEmpty) emptyList() else accountChanges),
        RemoteUpdate(SyncEntityType.CATEGORY, isFullState = true, changes = if (allEmpty) emptyList() else categoryChanges),
        RemoteUpdate(SyncEntityType.TRANSACTION, isFullState = true, changes = transactionChanges),
    )

    private fun staleTransactionChange() = SyncChange(
        key = SyncKey(SyncEntityType.TRANSACTION, "t1"),
        updatedAt = 50,
        fields = mapOf(
            "type" to "expense",
            "amountMinor" to 500L,
            "accountId" to "a1",
            "categoryId" to "c1",
            "date" to 0L,
            "updatedAt" to 50L,
        ),
    )

    private fun seedCatalogs(
        balance: BalanceEntity = balanceRow(),
        account: AccountEntity = accountRow(),
        category: CategoryEntity = categoryRow(),
    ) {
        balanceDao.rows.value = listOf(balance)
        accountDao.rows.value = listOf(account)
        categoryDao.rows.value = listOf(category)
    }

    private class FakeAuthProvider : AuthProvider {
        override val isSignedIn = MutableStateFlow(true)
        override val userId: String? = UID
        override val displayName: String = "Тест Нейм"
        override suspend fun signInWithGoogle(idToken: String) = Unit
    }

    private class FakeFamilyStore(familyId: String?) : FamilyStore {
        private val _familyId = MutableStateFlow(familyId)
        override val familyId: StateFlow<String?> = _familyId.asStateFlow()
        override fun select(id: String) {
            _familyId.value = id
        }
        override fun clear() {
            _familyId.value = null
        }
    }

    private class FakeSyncRemote : SyncRemote {
        val posted = mutableListOf<SyncChange>()
        val postedFamilyIds = mutableListOf<String>()
        val bulkCalls = mutableListOf<List<SyncChange>>()
        val bulkFamilyIds = mutableListOf<String>()
        val bulkTimeouts = mutableListOf<Long>()
        private val initial = mutableMapOf<SyncEntityType, List<RemoteUpdate>>()

        fun setInitialUpdates(updates: List<RemoteUpdate>) {
            updates.forEach { initial[it.type] = listOf(it) }
        }

        override fun observeChanges(familyId: String, type: SyncEntityType): Flow<RemoteUpdate> {
            val prepared = initial[type].orEmpty()
            return flowOf(*prepared.toTypedArray())
        }

        override suspend fun postChange(familyId: String, change: SyncChange) {
            postedFamilyIds += familyId
            posted += change
        }

        override suspend fun uploadBulk(familyId: String, changes: List<SyncChange>, timeoutMillis: Long) {
            bulkFamilyIds += familyId
            bulkCalls += changes
            bulkTimeouts += timeoutMillis
        }
    }

    private class FakeBalanceDao : BalanceDao {
        val rows = MutableStateFlow<List<BalanceEntity>>(emptyList())
        val cleanCalls = mutableListOf<Pair<String, Long>>()

        override suspend fun upsert(balance: BalanceEntity) {
            rows.value = rows.value.filterNot { it.id == balance.id } + balance
        }

        override suspend fun upsertAll(balances: List<BalanceEntity>) = balances.forEach { upsert(it) }

        override fun observeActive(): Flow<List<BalanceEntity>> = flowOf(rows.value.filter { !it.deleted })

        override fun observeAll(): Flow<List<BalanceEntity>> = rows

        override suspend fun byId(id: String): BalanceEntity? = rows.value.firstOrNull { it.id == id }

        override suspend fun listDirty(): List<BalanceEntity> = rows.value.filter { it.dirty }

        override suspend fun markClean(id: String, updatedAt: Long): Int {
            cleanCalls += id to updatedAt
            val current = rows.value.firstOrNull { it.id == id } ?: return 0
            if (current.updatedAt != updatedAt) return 0
            rows.value = rows.value.filterNot { it.id == id } + current.copy(dirty = false)
            return 1
        }

        override suspend fun markDirty(id: String) {
            replace(id) { it.copy(dirty = true) }
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }

        private fun replace(id: String, change: (BalanceEntity) -> BalanceEntity) {
            val current = rows.value.firstOrNull { it.id == id } ?: return
            rows.value = rows.value.filterNot { it.id == id } + change(current)
        }
    }

    private class FakeAccountDao : AccountDao {
        val rows = MutableStateFlow<List<AccountEntity>>(emptyList())
        val cleanCalls = mutableListOf<Pair<String, Long>>()

        override suspend fun upsert(account: AccountEntity) {
            rows.value = rows.value.filterNot { it.id == account.id } + account
        }

        override suspend fun upsertAll(accounts: List<AccountEntity>) = accounts.forEach { upsert(it) }

        override fun observeActive(): Flow<List<AccountEntity>> = flowOf(rows.value.filter { !it.deleted })

        override fun observeAll(): Flow<List<AccountEntity>> = rows

        override suspend fun byId(id: String): AccountEntity? = rows.value.firstOrNull { it.id == id }

        override suspend fun listByBalance(balanceId: String): List<AccountEntity> = rows.value.filter { !it.deleted && it.balanceId == balanceId }

        override suspend fun listActive(): List<AccountEntity> = rows.value.filter { !it.deleted }

        override suspend fun byBalanceId(balanceId: String): List<AccountEntity> = rows.value.filter { it.balanceId == balanceId }

        override suspend fun listDirty(): List<AccountEntity> = rows.value.filter { it.dirty }

        override suspend fun markClean(id: String, updatedAt: Long): Int {
            cleanCalls += id to updatedAt
            val current = rows.value.firstOrNull { it.id == id } ?: return 0
            if (current.updatedAt != updatedAt) return 0
            rows.value = rows.value.filterNot { it.id == id } + current.copy(dirty = false)
            return 1
        }

        override suspend fun markDirty(id: String) {
            replace(id) { it.copy(dirty = true) }
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }

        private fun replace(id: String, change: (AccountEntity) -> AccountEntity) {
            val current = rows.value.firstOrNull { it.id == id } ?: return
            rows.value = rows.value.filterNot { it.id == id } + change(current)
        }
    }

    private class FakeCategoryDao : CategoryDao {
        val rows = MutableStateFlow<List<CategoryEntity>>(emptyList())
        val cleanCalls = mutableListOf<Pair<String, Long>>()

        override suspend fun upsert(category: CategoryEntity) {
            rows.value = rows.value.filterNot { it.id == category.id } + category
        }

        override suspend fun upsertAll(categories: List<CategoryEntity>) = categories.forEach { upsert(it) }

        override fun observeActive(type: String): Flow<List<CategoryEntity>> = flowOf(rows.value.filter { !it.deleted && it.type == type })

        override fun observeAll(): Flow<List<CategoryEntity>> = rows

        override suspend fun byId(id: String): CategoryEntity? = rows.value.firstOrNull { it.id == id }

        override suspend fun listActive(type: String): List<CategoryEntity> = rows.value.filter { !it.deleted && it.type == type }

        override suspend fun listDirty(): List<CategoryEntity> = rows.value.filter { it.dirty }

        override suspend fun markClean(id: String, updatedAt: Long): Int {
            cleanCalls += id to updatedAt
            val current = rows.value.firstOrNull { it.id == id } ?: return 0
            if (current.updatedAt != updatedAt) return 0
            rows.value = rows.value.filterNot { it.id == id } + current.copy(dirty = false)
            return 1
        }

        override suspend fun markDirty(id: String) {
            replace(id) { it.copy(dirty = true) }
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }

        private fun replace(id: String, change: (CategoryEntity) -> CategoryEntity) {
            val current = rows.value.firstOrNull { it.id == id } ?: return
            rows.value = rows.value.filterNot { it.id == id } + change(current)
        }
    }

    private class FakeTransactionDao : TransactionDao {
        val rows = MutableStateFlow<List<TransactionEntity>>(emptyList())
        val cleanCalls = mutableListOf<Pair<String, Long>>()

        override suspend fun upsert(transaction: TransactionEntity) {
            rows.value = rows.value.filterNot { it.id == transaction.id } + transaction
        }

        override suspend fun upsertAll(transactions: List<TransactionEntity>) = transactions.forEach { upsert(it) }

        override fun observeActive(): Flow<List<TransactionEntity>> = flowOf(rows.value.filter { !it.deleted })

        override fun observeActiveForAccount(accountId: String): Flow<List<TransactionEntity>> = flowOf(rows.value.filter { !it.deleted && it.accountId == accountId })

        override fun observeActiveForAccounts(accountIds: List<String>, limit: Int): Flow<List<TransactionEntity>> = flowOf(rows.value.filter { !it.deleted && it.accountId in accountIds }.take(limit))

        override fun observeInPeriod(type: String, startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>> = flowOf(rows.value.filter { !it.deleted && it.type == type && it.date in startMillis until endMillis })

        override fun observeAll(): Flow<List<TransactionEntity>> = rows

        override suspend fun byId(id: String): TransactionEntity? = rows.value.firstOrNull { it.id == id }

        override suspend fun byAccountIds(accountIds: List<String>): List<TransactionEntity> = rows.value.filter { it.accountId in accountIds }

        override suspend fun byTypeAndPeriod(
            type: String,
            startMillis: Long?,
            endMillisExclusive: Long?,
            accountIds: List<String>,
        ): List<TransactionEntity> = rows.value.filter {
            !it.deleted &&
                it.type == type &&
                it.accountId in accountIds &&
                (startMillis == null || it.date >= startMillis) &&
                (endMillisExclusive == null || it.date < endMillisExclusive)
        }

        override suspend fun byCategoryAndPeriod(
            type: String,
            categoryId: String,
            startMillis: Long?,
            endMillisExclusive: Long?,
            accountIds: List<String>,
            limit: Int,
            offset: Int,
        ): List<TransactionEntity> = rows.value.filter {
            !it.deleted &&
                it.type == type &&
                it.categoryId == categoryId &&
                it.accountId in accountIds &&
                (startMillis == null || it.date >= startMillis) &&
                (endMillisExclusive == null || it.date < endMillisExclusive)
        }.drop(offset).take(limit)

        override suspend fun sumByCategoryAndPeriod(
            type: String,
            categoryId: String,
            startMillis: Long?,
            endMillisExclusive: Long?,
            accountIds: List<String>,
        ): Long = rows.value.filter {
            !it.deleted &&
                it.type == type &&
                it.categoryId == categoryId &&
                it.accountId in accountIds &&
                (startMillis == null || it.date >= startMillis) &&
                (endMillisExclusive == null || it.date < endMillisExclusive)
        }.sumOf { it.amountMinor }

        override suspend fun earliestMillis(accountIds: List<String>): Long? = rows.value.filter { !it.deleted && it.accountId in accountIds }.minOfOrNull { it.date }

        override suspend fun listDirty(): List<TransactionEntity> = rows.value.filter { it.dirty }

        override suspend fun markClean(id: String, updatedAt: Long): Int {
            cleanCalls += id to updatedAt
            val current = rows.value.firstOrNull { it.id == id } ?: return 0
            if (current.updatedAt != updatedAt) return 0
            rows.value = rows.value.filterNot { it.id == id } + current.copy(dirty = false)
            return 1
        }

        override suspend fun markDirty(id: String) {
            replace(id) { it.copy(dirty = true) }
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }

        private fun replace(id: String, change: (TransactionEntity) -> TransactionEntity) {
            val current = rows.value.firstOrNull { it.id == id } ?: return
            rows.value = rows.value.filterNot { it.id == id } + change(current)
        }
    }
}
