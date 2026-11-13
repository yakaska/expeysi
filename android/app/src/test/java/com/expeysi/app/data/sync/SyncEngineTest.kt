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
import com.expeysi.app.data.remote.RejectionBuffer
import com.expeysi.app.data.remote.RemoteUpdate
import com.expeysi.app.data.remote.SyncChange
import com.expeysi.app.data.remote.SyncEntityType
import com.expeysi.app.data.remote.SyncKey
import com.expeysi.app.data.remote.SyncRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
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
 * движка живут в отдельном скоупе с общим планировщиком теста: виртуальное
 * время, без Dispatchers.IO и реальных пауз. Скоуп не связан с TestScope,
 * поэтому тесты с бесконечным отказом сервера (rejectAlways) запускаются
 * через engineTest(): вечный цикл «отказ — выпуск» останавливается в finally
 * даже при падении ассерта, иначе runTest не дождётся тишины планировщика
 * (OOM executor'а). Проверки чистых функций оставлены без изменений.
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
        // markClean по отправленной версии бережёт правку: строка осталась dirty
        // и следующим проходом ушла заново уже версией 150.
        assertEquals(2, remote.pushCount)
        assertEquals(listOf(100L, 150L), remote.posted.map { it.updatedAt })
        assertEquals(150L, balanceDao.rows.value.single().updatedAt)
        assertFalse(balanceDao.rows.value.single().dirty)

        // Строка чиста: новые проходы ничего не отправляют.
        syncEngine.trigger()
        advanceUntilIdle()
        assertEquals(2, remote.pushCount)
    }

    @Test
    fun `сбои повторяются по backoff и успех сбрасывает паузу`() = runTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        remote.failTimes = 2
        val syncEngine = engine()
        syncEngine.start()
        advanceUntilIdle()
        // Два сбоя подряд: паузы 5 и 10 секунд виртуального времени.
        assertEquals(15000L, testScheduler.currentTime)
        assertEquals(3, remote.pushCount)
        assertEquals(1, remote.posted.size)
        assertFalse(balanceDao.rows.value.single().dirty)

        // После полного успеха backoff сброшен: новый сбой снова ждёт 5 секунд.
        balanceDao.upsert(balanceRow(updatedAt = 100).copy(dirty = true))
        remote.failTimes = 1
        syncEngine.trigger()
        advanceUntilIdle()
        assertEquals(20000L, testScheduler.currentTime)
        assertEquals(5, remote.pushCount)
        assertEquals(2, remote.posted.size)
        assertFalse(balanceDao.rows.value.single().dirty)
    }

    @Test
    fun `backoff растёт до потолка пяти минут`() = runTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        remote.failTimes = 10
        engine().start()
        advanceUntilIdle()
        // Паузы 5, 10, 20, 40, 80, 160 с и затем потолок 5 мин (4 повтора).
        assertEquals(1515000L, testScheduler.currentTime)
        // Десять сбоев плюс успешный одиннадцатый проход.
        assertEquals(11, remote.pushCount)
        assertFalse(balanceDao.rows.value.single().dirty)
    }

    @Test
    fun `триггеры во время паузы не создают проходов сверх одного после неё`() = runTest {
        balanceDao.rows.value = listOf(
            balanceRow(updatedAt = 100).copy(dirty = true),
            balanceRow(updatedAt = 100).copy(dirty = true).copy(id = "b2"),
        )
        remote.acceptNext(setOf(SyncKey(SyncEntityType.BALANCE, "b1")))
        val syncEngine = engine()
        syncEngine.start()
        testScheduler.runCurrent()
        // Первый проход: b1 принят, b2 остался dirty; воркер ушёл в паузу backoff.
        assertEquals(1, remote.pushCount)
        repeat(5) { syncEngine.trigger() }
        advanceUntilIdle()
        // Пять триггеров во время паузы слились: после паузы один проход.
        assertEquals(2, remote.pushCount)
        assertEquals(5000L, testScheduler.currentTime)
        assertTrue(balanceDao.rows.value.all { !it.dirty })
    }

    @Test
    fun `ядовитая запись не блокирует остальные и уходит после снятия отказа`() = runTest {
        balanceDao.rows.value = listOf(
            balanceRow(updatedAt = 100).copy(dirty = true),
            balanceRow(updatedAt = 100).copy(dirty = true).copy(id = "b2"),
        )
        remote.alwaysExclude = setOf(SyncKey(SyncEntityType.BALANCE, "b2"))
        var pushes = 0
        remote.onPush = {
            pushes += 1
            if (pushes >= 3) remote.alwaysExclude = emptySet()
        }
        val syncEngine = engine()
        syncEngine.start()
        testScheduler.runCurrent()
        // Первый проход: здоровая строка принята и чиста сразу, ядовитая осталась dirty.
        assertEquals(1, remote.pushCount)
        assertFalse(balanceDao.rows.value.first { it.id == "b1" }.dirty)
        assertTrue(balanceDao.rows.value.first { it.id == "b2" }.dirty)
        advanceUntilIdle()
        // Проходы с ядом шли с backoff 5, 10, 20 с; после снятия отказа b2 принята.
        assertEquals(4, remote.pushCount)
        assertEquals(35000L, testScheduler.currentTime)
        assertTrue(balanceDao.rows.value.all { !it.dirty })
    }

    @Test
    fun `отклонение сервера помечает строку dirty и отправляет её заново`() = runTest {
        transactionDao.rows.value = listOf(transactionRow(updatedAt = 100, updatedBy = "old-uid").copy(dirty = true))
        val syncEngine = engine()
        syncEngine.start()
        advanceUntilIdle()
        assertEquals(1, remote.pushCount)
        assertFalse(transactionDao.rows.value.single().dirty)

        // Сервер отклонил принятую ранее запись: строка снова dirty и уходит повтором.
        remote.reject(SyncKey(SyncEntityType.TRANSACTION, "t1"))
        advanceUntilIdle()
        assertEquals(2, remote.pushCount)
        assertEquals(UID, remote.posted.last().fields["updatedBy"])
        assertFalse(transactionDao.rows.value.single().dirty)
    }

    @Test
    fun `стойкий отказ ключа растит паузу 5 10 20 с до потолка, здоровые строки уходят сразу`() = engineTest {
        balanceDao.rows.value = listOf(
            balanceRow(updatedAt = 100).copy(dirty = true),
            balanceRow(updatedAt = 100).copy(dirty = true).copy(id = "b2"),
        )
        remote.rejectAlways = setOf(SyncKey(SyncEntityType.BALANCE, "b1"))
        remote.emissionScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        remote.timeSource = { testScheduler.currentTime }
        engine().start()
        testScheduler.advanceTimeBy(60 * 60 * 1000L)
        // За час b1 отправлена 17 раз (не больше 20); паузы между отправками —
        // 5, 10, 20, 40, 80, 160 с, дальше потолок 5 мин (плюс 100 мс
        // запаздывания колбэка отказа). Здоровая b2 отправлена один раз, сразу.
        assertEquals(17, remote.pushCount)
        assertEquals(
            listOf(0L, 5100L, 15200L, 35300L, 75400L, 155500L, 315600L, 615700L, 915800L, 1215900L, 1516000L, 1816100L, 2116200L, 2416300L, 2716400L, 3016500L, 3316600L),
            remote.pushTimes,
        )
        assertEquals(
            listOf(5000L, 10000L, 20000L, 40000L, 80000L, 160000L) + List(10) { 300000L },
            remote.pushTimes.zipWithNext { previous, next -> next - previous }.map { it - 100L },
        )
        assertEquals(1, remote.posted.count { it.key.id == "b2" })
    }

    @Test
    fun `отказ во время отправки не теряется — строка уходит повтором после паузы`() = runTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        remote.timeSource = { testScheduler.currentTime }
        var rejectedOnce = false
        remote.onPush = {
            if (!rejectedOnce) {
                rejectedOnce = true
                remote.reject(SyncKey(SyncEntityType.BALANCE, "b1"))
            }
        }
        engine().start()
        testScheduler.runCurrent()
        assertEquals(1, remote.pushCount)
        testScheduler.advanceTimeBy(5 * 60 * 1000L)
        testScheduler.runCurrent()
        // Отказ записан внутри push: выпуск через 5 с повторно отправил строку.
        assertEquals(listOf(0L, 5000L), remote.pushTimes)
        assertTrue(balanceDao.rows.value.all { !it.dirty })
    }

    @Test
    fun `отказ между отправкой и markClean не теряется — чистка пропускается, уходит после паузы`() = runTest {
        balanceDao.rows.value = listOf(
            balanceRow(updatedAt = 100).copy(dirty = true),
            balanceRow(updatedAt = 100).copy(dirty = true).copy(id = "b2"),
        )
        remote.timeSource = { testScheduler.currentTime }
        var rejectedOnce = false
        remote.onPush = { changes ->
            // Отказ b2 попадает в буфер, пока b1 ещё чистится.
            if (!rejectedOnce && changes.any { it.key.id == "b2" }) {
                rejectedOnce = true
                remote.reject(SyncKey(SyncEntityType.BALANCE, "b2"))
            }
        }
        balanceDao.markCleanHook = { id, _ ->
            // Пока проход ещё чистит b1, коллектор отказов успевает записать
            // b2: именно delay() завершает текущую задачу планировщика —
            // возобновление коллектора из буфера отказов запускается раньше.
            if (id == "b1") delay(100)
        }
        engine().start()
        testScheduler.runCurrent()
        // Отказ b2 записан (t=0) до чистки b2: markClean пропущен, строка
        // осталась dirty; сам markClean(b1) завершится после паузы хука.
        assertEquals(1, remote.pushCount)
        assertTrue(balanceDao.rows.value.first { it.id == "b2" }.dirty)
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        assertEquals(listOf("b1"), balanceDao.cleanCalls.map { it.first })
        testScheduler.advanceTimeBy(5 * 60 * 1000L)
        testScheduler.runCurrent()
        // Выпуск после 5 с: b2 отправлена заново и принята.
        assertEquals(listOf(0L, 5000L), remote.pushTimes)
        assertEquals(2, remote.posted.count { it.key.id == "b2" })
        assertEquals(1, remote.posted.count { it.key.id == "b1" })
        assertTrue(balanceDao.rows.value.all { !it.dirty })
        assertEquals(listOf("b1", "b2"), balanceDao.cleanCalls.map { it.first })
    }

    @Test
    fun `отказ после markClean не теряется — строка снова dirty в выпуске и уходит`() = engineTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        remote.rejectAlways = setOf(SyncKey(SyncEntityType.BALANCE, "b1"))
        remote.emissionScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        remote.timeSource = { testScheduler.currentTime }
        engine().start()
        testScheduler.advanceTimeBy(3000)
        // Строка принята и почищена; отказ дошёл (t=100) и удержан, но немедленной
        // повтора нет — он возможен только по выпуску в 5100.
        assertEquals(1, remote.pushCount)
        assertFalse(balanceDao.rows.value.single().dirty)
        testScheduler.advanceTimeBy(2100)
        testScheduler.runCurrent()
        // Выпуск в 5100: строка снова dirty (updatedAt совпал с удержанной) и
        // отправлена заново.
        assertEquals(listOf(0L, 5100L), remote.pushTimes)
        assertTrue(balanceDao.rows.value.all { !it.dirty })
    }

    @Test
    fun `после прекращения отказов строка синхронизируется, проходов больше нет`() = runTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        remote.rejectAlways = setOf(SyncKey(SyncEntityType.BALANCE, "b1"))
        remote.emissionScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        remote.timeSource = { testScheduler.currentTime }
        engine().start()
        testScheduler.advanceTimeBy(5100)
        testScheduler.runCurrent()
        // Выпуск в 5100: повторная отправка и чистка; отказ повторного push ещё
        // не дошёл (он был бы на t=5200).
        assertEquals(listOf(0L, 5100L), remote.pushTimes)
        assertTrue(balanceDao.rows.value.all { !it.dirty })
        remote.rejectAlways = emptySet()
        testScheduler.advanceTimeBy(10 * 60 * 1000L)
        // Отказ на t=5200 не пришёл: новых отправок нет, строка осталась чиста.
        assertEquals(2, remote.pushCount)
        assertTrue(balanceDao.rows.value.all { !it.dirty })
    }

    @Test
    fun `новая версия удержанного ключа уходит сразу и со свежим счётчиком`() = engineTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        remote.rejectAlways = setOf(SyncKey(SyncEntityType.BALANCE, "b1"))
        remote.emissionScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        remote.timeSource = { testScheduler.currentTime }
        val syncEngine = engine()
        syncEngine.start()
        testScheduler.advanceTimeBy(3000)
        // Отказ версии 100 записан на t=100, выпланирован выпуск на 5100.
        assertEquals(listOf(0L), remote.pushTimes)
        // Правка до версии 200: уходит сразу, не дожидаясь выпуска, и чистится.
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 200).copy(dirty = true))
        syncEngine.trigger()
        testScheduler.runCurrent()
        assertEquals(listOf(0L, 3000L), remote.pushTimes)
        assertTrue(balanceDao.rows.value.all { !it.dirty })
        testScheduler.advanceTimeBy(2100)
        testScheduler.runCurrent()
        // В 5100 ничего не происходит: выпуск версии 100 отменён заменой
        // удержания на версию 200 (а строка и так уже на версии 200).
        assertTrue(balanceDao.rows.value.all { !it.dirty })
        assertEquals(listOf(0L, 3000L), remote.pushTimes)
        testScheduler.advanceTimeBy(3000)
        testScheduler.runCurrent()
        // Отказ версии 200 (t=3100) дал свежий счётчик: выпуск в 8100 (5 с), а
        // не по старому счётчику (было бы через 10 с после отказа версии 100).
        assertEquals(listOf(0L, 3000L, 8100L), remote.pushTimes)
        assertTrue(balanceDao.rows.value.all { !it.dirty })
    }

    @Test
    fun `другие триггеры не снимают удержание и не сбрасывают счётчик`() = engineTest {
        balanceDao.rows.value = listOf(
            balanceRow(updatedAt = 100).copy(dirty = true),
            balanceRow(updatedAt = 100).copy(dirty = true).copy(id = "b2"),
        )
        remote.rejectAlways = setOf(SyncKey(SyncEntityType.BALANCE, "b1"))
        remote.emissionScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        remote.timeSource = { testScheduler.currentTime }
        val syncEngine = engine()
        syncEngine.start()
        testScheduler.advanceTimeBy(200)
        assertEquals(listOf(0L), remote.pushTimes)
        // Правка b2: проход отправляет только b2, удержанная b1 не тронута.
        balanceDao.rows.value = balanceDao.rows.value.map { if (it.id == "b2") it.copy(updatedAt = 150, dirty = true) else it }
        syncEngine.trigger()
        testScheduler.runCurrent()
        assertEquals(listOf(0L, 200L), remote.pushTimes)
        assertEquals(1, remote.posted.count { it.key.id == "b1" })
        assertEquals(2, remote.posted.count { it.key.id == "b2" })
        // Эхо удержанного ключа дельтой со старой версией: строка снова dirty,
        // но удержание держит — проход не отправляет её и не считает неполным.
        remote.sendRemoteUpdate(deltaUpdate(SyncEntityType.BALANCE, listOf(snapshotChange(SyncEntityType.BALANCE, "b1", 50, "Чужое"))))
        testScheduler.runCurrent()
        assertEquals(listOf(0L, 200L), remote.pushTimes)
        assertEquals(1, remote.posted.count { it.key.id == "b1" })
        testScheduler.advanceTimeBy(4900)
        testScheduler.runCurrent()
        // Выпуск b1 в 5100 (пауза от его отказа на t=100, не укороченная триггерами).
        assertEquals(listOf(0L, 200L, 5100L), remote.pushTimes)
        assertEquals(2, remote.posted.count { it.key.id == "b1" })
        assertTrue(balanceDao.rows.value.first { it.id == "b1" }.dirty.not())
    }

    @Test
    fun `всплеск из 500 отказов обрабатывается полностью, проходы в границах`() = engineTest {
        val keys = (1..500).map { SyncKey(SyncEntityType.BALANCE, "b$it") }
        balanceDao.rows.value = (1..500).map { balanceRow(updatedAt = 100).copy(dirty = true).copy(id = "b$it") }
        remote.rejectAlways = keys.toSet()
        remote.emissionScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        remote.timeSource = { testScheduler.currentTime }
        engine().start()
        testScheduler.advanceTimeBy(5100)
        testScheduler.runCurrent()
        // Все 500 отправлены (t=0), все отклонены (t=100), все выпущены в 5100
        // и слиты в один проход: ни один ключ не потерян, отправок всего две.
        assertEquals(2, remote.pushCount)
        assertEquals(listOf(0L, 5100L), remote.pushTimes)
        keys.forEach { key -> assertEquals(2, remote.posted.count { it.key == key }) }
        assertTrue(balanceDao.rows.value.all { !it.dirty })
    }

    @Test
    fun `смена семьи очищает счётчики и отменяет job выпуска без ошибок`() = engineTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        remote.rejectAlways = setOf(SyncKey(SyncEntityType.BALANCE, "b1"))
        remote.emissionScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        remote.timeSource = { testScheduler.currentTime }
        val syncEngine = engine()
        syncEngine.start()
        testScheduler.advanceTimeBy(300)
        // Отказ записан (t=100), выпуск версии 100 выпланирован на 5100.
        assertEquals(listOf(0L), remote.pushTimes)
        familyStore.select("fam-2")
        testScheduler.advanceTimeBy(1000)
        // Выпуск отменён сменой семьи: отправок больше нет.
        assertEquals(1, remote.pushCount)
        assertEquals(listOf(0L), remote.pushTimes)
        // Правка той же версии 100 в новой сессии: уходит сразу (t=1300),
        // отказ даёт СВЕЖУЮ попытку — счётчик очищен reset() при смене сессии.
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        syncEngine.trigger()
        testScheduler.runCurrent()
        assertEquals(listOf(0L, 1300L), remote.pushTimes)
        assertEquals(listOf(FAMILY_ID, "fam-2"), remote.pushFamilyIds)
        testScheduler.advanceTimeBy(5100)
        testScheduler.runCurrent()
        // Выпуск в 6400: отказ пришёл на t=1400 (через 100 мс после отправки),
        // свежая попытка — пауза 5 с. Со старым счётчиком было бы 11400.
        assertEquals(listOf(0L, 1300L, 6400L), remote.pushTimes)
        assertTrue(balanceDao.rows.value.all { !it.dirty })
    }

    @Test
    fun `Room-ошибка listDirty не убивает воркера, повтор через 5 секунд и успех`() = runTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        balanceDao.listDirtyErrors = 1
        engine().start()
        advanceUntilIdle()
        assertEquals(5000L, testScheduler.currentTime)
        assertEquals(1, remote.pushCount)
        assertFalse(balanceDao.rows.value.single().dirty)
    }

    @Test
    fun `Room-ошибка markClean посреди прохода сохраняет dirty до повтора`() = runTest {
        balanceDao.rows.value = listOf(
            balanceRow(updatedAt = 100).copy(dirty = true),
            balanceRow(updatedAt = 100).copy(dirty = true).copy(id = "b2"),
        )
        balanceDao.markCleanFailAtCall = 2
        engine().start()
        advanceUntilIdle()
        // Первый проход: b1 чист, на чистке b2 ошибка; повтор через 5 с дочистил b2.
        assertEquals(5000L, testScheduler.currentTime)
        assertEquals(2, remote.pushCount)
        assertTrue(balanceDao.rows.value.all { !it.dirty })
        assertEquals(listOf("b1", "b2"), balanceDao.cleanCalls.map { it.first })
    }

    @Test
    fun `строки без пользователя не отправляются и уходят после входа`() = runTest {
        auth.isSignedIn.value = false
        transactionDao.rows.value = listOf(transactionRow(updatedAt = 100, updatedBy = "old-uid").copy(dirty = true))
        val syncEngine = engine()
        syncEngine.start()
        advanceUntilIdle()
        assertEquals(0, remote.pushCount)
        assertTrue(transactionDao.rows.value.single().dirty)

        // Вход: dirty-строка уходит, сессия поднимается.
        auth.isSignedIn.value = true
        advanceUntilIdle()
        assertEquals(1, remote.pushCount)
        assertEquals("t1" to 100L, transactionDao.cleanCalls.single())
        assertFalse(transactionDao.rows.value.single().dirty)
    }

    @Test
    fun `отмена скоупа завершает воркера без повторов`() = runTest {
        balanceDao.rows.value = listOf(balanceRow(updatedAt = 100).copy(dirty = true))
        remote.failTimes = 1
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val syncEngine = SyncEngine(
            remote = remote,
            authRepository = auth,
            familyStore = familyStore,
            balanceDao = balanceDao,
            accountDao = accountDao,
            categoryDao = categoryDao,
            transactionDao = transactionDao,
            scope = scope,
        )
        syncEngine.start()
        testScheduler.runCurrent()
        // Первый проход упал; воркер в паузе backoff.
        assertEquals(1, remote.pushCount)
        assertTrue(balanceDao.rows.value.single().dirty)

        scope.cancel()
        advanceUntilIdle()
        // Отмена завершает воркера (CancellationException пробрасывается): повторов нет.
        assertEquals(1, remote.pushCount)
        assertTrue(balanceDao.rows.value.single().dirty)
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
    private val auth = FakeAuthProvider()
    private val balanceDao = FakeBalanceDao()
    private val accountDao = FakeAccountDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val familyStore = FakeFamilyStore(FAMILY_ID)

    /** Движок с подменённым scope на планировщике теста (виртуальное время). */
    private fun TestScope.engine(): SyncEngine = SyncEngine(
        remote = remote,
        authRepository = auth,
        familyStore = familyStore,
        balanceDao = balanceDao,
        accountDao = accountDao,
        categoryDao = categoryDao,
        transactionDao = transactionDao,
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            .also { engineScope = it },
    )

    /** Скоуп движка текущего теста: тест глушит его явно в stopEngine(). */
    private var engineScope: CoroutineScope? = null

    /**
     * Явная остановка движка и фейковой эмиссии отказов в конце теста:
     * серверный цикл «отказ — выпуск» бесконечен по дизайну и иначе не даёт
     * runTest дождаться тишины планировщика (попытка OOM на вечном цикле).
     */
    private fun stopEngine() {
        engineScope?.cancel()
        engineScope = null
        remote.emissionScope?.cancel()
        remote.emissionScope = null
    }

    /**
     * runTest с остановкой движка в finally: если ассерт упал в середине тела,
     * trailing-вызов stopEngine не выполнился бы — и вечный цикл отказа снова
     * съел бы весь executor. Здесь остановка гарантирована при любом исходе.
     */
    private fun engineTest(block: suspend TestScope.() -> Unit) = runTest {
        try {
            block()
        } finally {
            stopEngine()
        }
    }

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

        /** Пока вход не выполнен, userId нет: строки ждут (инвариант 7). */
        override val userId: String? get() = if (isSignedIn.value) UID else null
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

        /** Виртуальное время каждого push; источник подключает тест. */
        val pushTimes = mutableListOf<Long>()
        var timeSource: () -> Long = { 0L }
        var pushCount = 0
            private set
        var failTimes = 0

        /**
         * Ключи, которые сервер никогда не принимает (яд через partial accepted),
         * пока не сняты набором.
         */
        var alwaysExclude: Set<SyncKey> = emptySet()

        /**
         * Ключи, которые сервер отклоняет ~100 мс (виртуальных) после каждой
         * отправки, содержащей ключ, — как в реальном коротком пути Firebase
         * (колбэк отказа приходит после возврата). Набор читается и при
         * отправке (ключ в accepted), и в момент события: тест может снять
         * отказы заранее. События уходят в [emissionScope].
         */
        var rejectAlways: Set<SyncKey> = emptySet()

        /** Скоуп для отложенных событий отказа; без него отказов нет. */
        var emissionScope: CoroutineScope? = null
        var onPush: suspend (List<SyncChange>) -> Unit = {}
        private var pendingAccepted: Set<SyncKey>? = null

        /** Ключи, принятые к доставке, но отклонённые позже: без потерь, как у транспорта. */
        private val rejectionsBuffer = RejectionBuffer()
        override val rejections: Flow<SyncKey> get() = rejectionsBuffer.flow
        private val updates = MutableStateFlow<List<RemoteUpdate>>(emptyList())

        /** Внеплановые дельты слияния, доставляемые живьём посреди теста. */
        private val liveUpdates = MutableSharedFlow<RemoteUpdate>(extraBufferCapacity = 16)

        /** Отказ сервера по ключу, который был принят к доставке. */
        fun reject(key: SyncKey) {
            rejectionsBuffer.offer(key)
        }

        /** Удалённое изменение посреди теста (слияние движком). */
        fun sendRemoteUpdate(update: RemoteUpdate) {
            liveUpdates.tryEmit(update)
        }

        fun setInitialUpdates(updates: List<RemoteUpdate>) {
            this.updates.value = updates
        }

        /** Частичный accepted только для следующего push. */
        fun acceptNext(accepted: Set<SyncKey>) {
            pendingAccepted = accepted
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observe(familyId: String): Flow<RemoteUpdate> = merge(updates.flatMapLatest { it.asFlow() }, liveUpdates)

        override suspend fun push(familyId: String, changes: List<SyncChange>): PushResult {
            pushCount++
            pushTimes += timeSource()
            if (failTimes > 0) {
                failTimes--
                throw IOException("transport down")
            }
            val accepted = pendingAccepted ?: changes.map { it.key }.toSet() - alwaysExclude
            pendingAccepted = null
            posted += changes
            pushFamilyIds += familyId
            emissionScope?.let { scope ->
                changes.filter { it.key in rejectAlways }.forEach { change ->
                    scope.launch {
                        delay(100)
                        if (change.key in rejectAlways) reject(change.key)
                    }
                }
            }
            onPush(changes)
            return PushResult(accepted)
        }
    }

    private class FakeBalanceDao : BalanceDao {
        val rows = MutableStateFlow<List<BalanceEntity>>(emptyList())
        val cleanCalls = mutableListOf<Pair<String, Long>>()

        /** Ошибки чтения dirty-строк: столько сбоев подряд перед успехом. */
        var listDirtyErrors = 0

        /** Бросать исключение на этом по счёту (с 1) вызове markClean; 0 — не бросать. */
        var markCleanFailAtCall = 0
        private var markCleanCalls = 0

        /**
         * Крючок внутрь markClean перед записью флага: тест может подождать
         * в нём (delay), чтобы событие отказа доставилось коллектору посреди
         * цикла чистки — тогда чистка следующей строки пропускается.
         */
        var markCleanHook: (suspend (id: String, updatedAt: Long) -> Unit)? = null

        override suspend fun upsert(balance: BalanceEntity) {
            rows.value = rows.value.filterNot { it.id == balance.id } + balance
        }

        override suspend fun upsertAll(balances: List<BalanceEntity>) = balances.forEach { upsert(it) }

        override fun observeActive(): Flow<List<BalanceEntity>> = flowOf(rows.value.filter { !it.deleted })

        override fun observeAll(): Flow<List<BalanceEntity>> = rows

        override suspend fun byId(id: String): BalanceEntity? = rows.value.firstOrNull { it.id == id }

        override suspend fun listDirty(): List<BalanceEntity> {
            if (listDirtyErrors > 0) {
                listDirtyErrors--
                throw IOException("room read failed")
            }
            return rows.value.filter { it.dirty }
        }

        override suspend fun markClean(id: String, updatedAt: Long): Int {
            markCleanCalls += 1
            if (markCleanCalls == markCleanFailAtCall) throw IOException("room write failed")
            markCleanHook?.invoke(id, updatedAt)
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
