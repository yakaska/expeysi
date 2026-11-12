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
import com.expeysi.app.data.remote.PushResult
import com.expeysi.app.data.remote.RemoteUpdate
import com.expeysi.app.data.remote.SyncChange
import com.expeysi.app.data.remote.SyncEntityType
import com.expeysi.app.data.remote.SyncKey
import com.expeysi.app.data.remote.SyncRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Поведение SyncEngine на интерфейсах: фейковый SyncRemote плюс фейки DAO,
 * повторяющие SQL-контракты (условный markClean по updatedAt). Корутины
 * движка живут в backgroundScope теста: виртуальное время, без
 * Dispatchers.IO и реальных пауз. Проверки чистых функций оставлены
 * без изменений.
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

    // Тесты движка на фейках: слияние по updatedAt (инвариант 1), первичная
    // сверка (инвариант 4), рабочий цикл отправки (инвариант 5).

    @Test
    fun `снимок новее локали заменяет строку с дефолтами wire-формата`() = runTest {
        seedCatalogs(balance = balanceRow(name = "Старое имя", updatedAt = 50))
        remote.setInitialUpdates(initialUpdates())
        engine().start()
        advanceUntilIdle()
        val row = balanceDao.rows.value.single()
        assertEquals(100L, row.updatedAt)
        assertEquals("Продукты", row.name)
        assertEquals("RUB", row.currencyCode)
        assertEquals("", row.iconKey)
        assertEquals("default", row.colorKey)
        assertFalse(row.deleted)
        assertFalse(row.dirty)
        assertTrue(remote.posted.isEmpty())
    }

    @Test
    fun `локально новее снимка помечается dirty и уходит через воркера`() = runTest {
        seedCatalogs(balance = balanceRow(name = "Моё имя", updatedAt = 100))
        remote.setInitialUpdates(
            initialUpdates(
                balanceChanges = listOf(snapshotChange(SyncEntityType.BALANCE, "b1", 50, "Чужое старое имя")),
            ),
        )
        engine().start()
        advanceUntilIdle()
        val row = balanceDao.rows.value.single()
        assertEquals("Моё имя", row.name)
        assertEquals(100L, row.updatedAt)
        // Слияние и сверка не шлют напрямую: строку отправляет рабочий цикл.
        // Два прохода — после слияния и после сверки (обе считают строку
        // новее транспорта; push идемпотентен, как и у прежнего движка).
        assertEquals(2, remote.posted.size)
        assertTrue(remote.posted.all { it.updatedAt == 100L })
        assertTrue(remote.posted.all { it.fields["name"] == "Моё имя" })
        assertFalse(row.dirty)
    }

    @Test
    fun `равные updatedAt — строка не отправляется и не перезаписывается`() = runTest {
        seedCatalogs(balance = balanceRow(name = "Моё имя", updatedAt = 100))
        remote.setInitialUpdates(
            initialUpdates(
                balanceChanges = listOf(snapshotChange(SyncEntityType.BALANCE, "b1", 100, "Иное имя")),
            ),
        )
        engine().start()
        advanceUntilIdle()
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
        engine().start()
        advanceUntilIdle()
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
    fun `сверка не шлёт напрямую, только помечает dirty`() = runTest {
        seedCatalogs(balance = balanceRow(updatedAt = 300))
        remote.setInitialUpdates(
            initialUpdates(
                balanceChanges = listOf(snapshotChange(SyncEntityType.BALANCE, "b1", 100, "Продукты")),
                allEmpty = false,
            ),
        )
        engine().start()
        advanceUntilIdle()
        // Прямых отправок из слияния и сверки нет (построчного push больше
        // не существует): строку отправляет рабочий цикл. Двойная отправка —
        // слияние и сверка обе считают строку новее, как и прежний движок.
        assertEquals(2, remote.posted.size)
        assertTrue(remote.posted.all { it.key == SyncKey(SyncEntityType.BALANCE, "b1") })
        assertFalse(balanceDao.rows.value.single().dirty)
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
        engine().start()
        advanceUntilIdle()
        assertTrue(remote.posted.isNotEmpty())
        assertTrue(remote.posted.all { it.fields["updatedBy"] == UID })
        assertEquals(100L, transactionDao.rows.value.single().updatedAt)
    }

    @Test
    fun `syncDirty публикует dirty и снимает флаг той же версией`() = runTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        engine().syncDirty()
        assertEquals(SyncKey(SyncEntityType.BALANCE, "b1"), remote.posted.single().key)
        assertEquals("b1" to 100L, balanceDao.cleanCalls.single())
        assertFalse(balanceDao.rows.value.single().dirty)
    }

    @Test
    fun `syncDirty без выбранной семьи ничего не отправляет и не чистит`() = runTest {
        familyStore.clear()
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        engine().syncDirty()
        assertTrue(remote.posted.isEmpty())
        assertTrue(remote.pushFamilyIds.isEmpty())
        assertTrue(balanceDao.rows.value.single().dirty)
        assertTrue(balanceDao.cleanCalls.isEmpty())
    }

    @Test
    fun `старт сессии семьи поднимает проход отправки dirty-строк`() = runTest {
        transactionDao.rows.value = listOf(transactionRow(updatedAt = 100, updatedBy = "old-uid").copy(dirty = true))
        engine().start()
        advanceUntilIdle()
        assertEquals(SyncKey(SyncEntityType.TRANSACTION, "t1"), remote.posted.single().key)
        assertEquals(UID, remote.posted.single().fields["updatedBy"])
        assertEquals(100L, remote.posted.single().updatedAt)
        assertEquals("t1" to 100L, transactionDao.cleanCalls.single())
        assertFalse(transactionDao.rows.value.single().dirty)
    }

    @Test
    fun `trigger возвращает управление сразу, отправку делает воркер`() = runTest {
        val syncEngine = engine()
        syncEngine.start()
        advanceUntilIdle()
        assertTrue(remote.posted.isEmpty())
        transactionDao.rows.value = listOf(transactionRow(updatedAt = 100).copy(dirty = true))
        syncEngine.trigger()
        assertEquals(0, remote.posted.size)
        advanceUntilIdle()
        assertEquals(SyncKey(SyncEntityType.TRANSACTION, "t1"), remote.posted.single().key)
        assertEquals("t1" to 100L, transactionDao.cleanCalls.single())
        assertFalse(transactionDao.rows.value.single().dirty)
    }

    @Test
    fun `правка во время отправки уходит заново и снимается по своей версии`() = runTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        var editsLeft = 1
        remote.onPush = {
            // Правка той же строки, когда её push уже в полёте.
            if (editsLeft > 0) {
                editsLeft--
                balanceDao.upsert(balanceRow(name = "Моё имя", updatedAt = 150).copy(dirty = true))
            }
        }
        val syncEngine = engine()
        syncEngine.start()
        advanceUntilIdle()
        // markClean по отправленной версии бережёт правку: строка остаётся dirty.
        assertEquals(1, remote.pushCount)
        assertEquals(listOf(100L), remote.posted.map { it.updatedAt })
        assertEquals(150L, balanceDao.rows.value.single().updatedAt)
        assertTrue(balanceDao.rows.value.single().dirty)

        syncEngine.trigger()
        advanceUntilIdle()

        assertEquals(2, remote.pushCount)
        assertEquals(listOf(100L, 150L), remote.posted.map { it.updatedAt })
        assertEquals(150L, balanceDao.rows.value.single().updatedAt)
        assertFalse(balanceDao.rows.value.single().dirty)
    }

    @Test
    fun `сбой повторяется через пять секунд и цикл продолжает работу`() = runTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        remote.failTimes = 2
        val syncEngine = engine()
        syncEngine.start()
        advanceUntilIdle()
        // Два сбоя подряд: пауза 5 секунд виртуального времени и повтор после каждого.
        assertEquals(10000L, testScheduler.currentTime)
        assertEquals(3, remote.pushCount)
        assertEquals(1, remote.posted.size)
        assertFalse(balanceDao.rows.value.single().dirty)

        // Цикл жив после исключений: новый проход работает.
        balanceDao.upsert(balanceRow(updatedAt = 100).copy(dirty = true))
        syncEngine.trigger()
        advanceUntilIdle()
        assertEquals(4, remote.pushCount)
        assertEquals(2, remote.posted.size)
        assertFalse(balanceDao.rows.value.single().dirty)
    }

    @Test
    fun `частичный accepted чистит только принятые и повторяет недобор`() = runTest {
        balanceDao.rows.value = listOf(
            balanceRow(updatedAt = 100).copy(dirty = true),
            balanceRow(updatedAt = 100).copy(dirty = true).copy(id = "b2"),
        )
        remote.acceptNext(setOf(SyncKey(SyncEntityType.BALANCE, "b1")))
        engine().start()
        advanceUntilIdle()
        // b1 принят и чистится, b2 остаётся dirty и уходит вторым проходом.
        assertEquals(2, remote.pushCount)
        assertEquals(5000L, testScheduler.currentTime)
        assertEquals(listOf("b1", "b2", "b2"), remote.posted.map { it.key.id })
        assertTrue(balanceDao.rows.value.all { !it.dirty })
    }

    @Test
    fun `пятьдесят триггеров сливаются в один проход отправки`() = runTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        val syncEngine = engine()
        syncEngine.start()
        repeat(50) { syncEngine.trigger() }
        advanceUntilIdle()
        assertEquals(1, remote.pushCount)
        assertFalse(balanceDao.rows.value.single().dirty)
    }

    @Test
    fun `dirty строки без семьи уходят после выбора семьи`() = runTest {
        familyStore.clear()
        transactionDao.rows.value = listOf(transactionRow(updatedAt = 100).copy(dirty = true))
        val syncEngine = engine()
        syncEngine.start()
        advanceUntilIdle()
        assertEquals(0, remote.pushCount)
        assertTrue(transactionDao.rows.value.single().dirty)

        familyStore.select(FAMILY_ID)
        advanceUntilIdle()

        assertEquals(1, remote.pushCount)
        assertEquals(SyncKey(SyncEntityType.TRANSACTION, "t1"), remote.posted.single().key)
        assertFalse(transactionDao.rows.value.single().dirty)
    }

    @Test
    fun `полное состояние заменяет карту и строка уходит после сверки`() = runTest {
        seedCatalogs(
            balance = balanceRow(updatedAt = 50),
            account = accountRow(updatedAt = 100),
            category = categoryRow(updatedAt = 100),
        )
        remote.setInitialUpdates(
            listOf(
                fullUpdate(SyncEntityType.BALANCE, listOf(snapshotChange(SyncEntityType.BALANCE, "b1", 100, "Продукты"))),
            ),
        )
        engine().start()
        advanceUntilIdle()
        // Слияние заменило строку (dirty = false); сверка ещё не запускалась.
        assertEquals(0, remote.pushCount)
        assertEquals(100L, balanceDao.rows.value.single().updatedAt)

        remote.setInitialUpdates(
            listOf(
                fullUpdate(SyncEntityType.BALANCE, emptyList()),
                fullUpdate(SyncEntityType.ACCOUNT, listOf(snapshotChange(SyncEntityType.ACCOUNT, "a1", 100, "Наличные"))),
                fullUpdate(SyncEntityType.CATEGORY, listOf(snapshotChange(SyncEntityType.CATEGORY, "c1", 100, "Еда"))),
                fullUpdate(SyncEntityType.TRANSACTION, emptyList()),
            ),
        )
        advanceUntilIdle()
        // Если бы карта не заменилась, сверка сочла бы b1 равным и не отправила его.
        assertEquals(1, remote.pushCount)
        assertEquals(SyncKey(SyncEntityType.BALANCE, "b1"), remote.posted.single().key)
        assertFalse(balanceDao.rows.value.single().dirty)
    }

    @Test
    fun `сверка ждёт полное состояние всех четырёх типов`() = runTest {
        seedCatalogs(balance = balanceRow(updatedAt = 100), account = accountRow(updatedAt = 100), category = categoryRow(updatedAt = 100))
        transactionDao.rows.value = listOf(transactionRow(updatedAt = 300))
        remote.setInitialUpdates(
            listOf(
                fullUpdate(SyncEntityType.BALANCE, listOf(snapshotChange(SyncEntityType.BALANCE, "b1", 100, "Продукты"))),
                fullUpdate(SyncEntityType.ACCOUNT, listOf(snapshotChange(SyncEntityType.ACCOUNT, "a1", 100, "Наличные"))),
            ),
        )
        engine().start()
        advanceUntilIdle()
        // Два из четырёх: сверки нет, t1@300 не отправляется.
        assertEquals(0, remote.pushCount)

        remote.setInitialUpdates(
            listOf(
                fullUpdate(SyncEntityType.CATEGORY, listOf(snapshotChange(SyncEntityType.CATEGORY, "c1", 100, "Еда"))),
                fullUpdate(SyncEntityType.TRANSACTION, emptyList()),
            ),
        )
        advanceUntilIdle()
        // Отсутствующая в полном состоянии транзакция уходит после сверки.
        assertEquals(1, remote.pushCount)
        assertEquals(SyncKey(SyncEntityType.TRANSACTION, "t1"), remote.posted.single().key)
        assertFalse(transactionDao.rows.value.single().dirty)
    }

    @Test
    fun `дельты никогда не запускают сверку`() = runTest {
        seedCatalogs()
        transactionDao.rows.value = listOf(transactionRow(updatedAt = 300))
        remote.setInitialUpdates(
            listOf(
                deltaUpdate(SyncEntityType.BALANCE, listOf(snapshotChange(SyncEntityType.BALANCE, "b1", 100, "Продукты"))),
                deltaUpdate(SyncEntityType.ACCOUNT, listOf(snapshotChange(SyncEntityType.ACCOUNT, "a1", 100, "Наличные"))),
                deltaUpdate(SyncEntityType.CATEGORY, listOf(snapshotChange(SyncEntityType.CATEGORY, "c1", 100, "Еда"))),
                deltaUpdate(SyncEntityType.TRANSACTION, emptyList()),
            ),
        )
        engine().start()
        advanceUntilIdle()
        // Равные версии сливаются без действий; t1@300 не сверяется.
        assertEquals(0, remote.pushCount)
        assertFalse(transactionDao.rows.value.single().dirty)
    }

    @Test
    fun `невалидный документ пропускается при слиянии`() = runTest {
        seedCatalogs(
            balance = balanceRow(updatedAt = 100),
            account = accountRow(updatedAt = 100),
            category = categoryRow(updatedAt = 100),
        )
        remote.setInitialUpdates(
            listOf(
                fullUpdate(
                    SyncEntityType.BALANCE,
                    listOf(
                        snapshotChange(SyncEntityType.BALANCE, "b1", 100, "Продукты"),
                        // Нет обязательного name — документ невалиден.
                        SyncChange(
                            key = SyncKey(SyncEntityType.BALANCE, "b9"),
                            updatedAt = 50,
                            fields = mapOf("updatedAt" to 50L),
                        ),
                    ),
                ),
                fullUpdate(SyncEntityType.ACCOUNT, listOf(snapshotChange(SyncEntityType.ACCOUNT, "a1", 100, "Наличные"))),
                fullUpdate(SyncEntityType.CATEGORY, listOf(snapshotChange(SyncEntityType.CATEGORY, "c1", 100, "Еда"))),
                fullUpdate(SyncEntityType.TRANSACTION, emptyList()),
            ),
        )
        val syncEngine = engine()
        syncEngine.start()
        advanceUntilIdle()
        // Невалидный документ не создаёт строку и не отправляет ничего.
        assertEquals(0, remote.pushCount)
        assertEquals(listOf("b1"), balanceDao.rows.value.map { it.id })

        // Цикл продолжает работать после невалидного документа.
        balanceDao.markDirty("b1")
        syncEngine.trigger()
        advanceUntilIdle()
        assertEquals(1, remote.pushCount)
        assertTrue(balanceDao.rows.value.all { !it.dirty })
    }

    // Фикстуры и фейки.

    private companion object {
        const val FAMILY_ID = "fam-1"
        const val UID = "uid-1"
    }

    private val remote = FakeSyncRemote()
    private val balanceDao = FakeBalanceDao()
    private val accountDao = FakeAccountDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val familyStore = FakeFamilyStore(FAMILY_ID)

    /** Движок с подменённым scope на планировщике теста (виртуальное время). */
    private fun TestScope.engine(): SyncEngine = SyncEngine(
        remote = remote,
        authRepository = FakeAuthProvider(),
        familyStore = familyStore,
        balanceDao = balanceDao,
        accountDao = accountDao,
        categoryDao = categoryDao,
        transactionDao = transactionDao,
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
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

    /** Одиночное полное состояние типа. */
    private fun fullUpdate(type: SyncEntityType, changes: List<SyncChange>) = RemoteUpdate(type, isFullState = true, changes = changes)

    /** Одиночная дельта типа. */
    private fun deltaUpdate(type: SyncEntityType, changes: List<SyncChange>) = RemoteUpdate(type, isFullState = false, changes = changes)

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
        val pushFamilyIds = mutableListOf<String>()
        var pushCount = 0
            private set
        var failTimes = 0
        var onPush: suspend (List<SyncChange>) -> Unit = {}
        private var pendingAccepted: Set<SyncKey>? = null
        private val updates = MutableStateFlow<List<RemoteUpdate>>(emptyList())

        fun setInitialUpdates(updates: List<RemoteUpdate>) {
            this.updates.value = updates
        }

        /** Частичный accepted только для следующего push. */
        fun acceptNext(accepted: Set<SyncKey>) {
            pendingAccepted = accepted
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observe(familyId: String): Flow<RemoteUpdate> = updates.flatMapLatest { it.asFlow() }

        override suspend fun push(familyId: String, changes: List<SyncChange>): PushResult {
            pushCount++
            if (failTimes > 0) {
                failTimes--
                throw IOException("transport down")
            }
            val accepted = pendingAccepted ?: changes.map { it.key }.toSet()
            pendingAccepted = null
            posted += changes
            pushFamilyIds += familyId
            onPush(changes)
            return PushResult(accepted)
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
