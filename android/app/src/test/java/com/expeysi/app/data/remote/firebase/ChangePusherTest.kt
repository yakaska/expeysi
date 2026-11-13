package com.expeysi.app.data.remote.firebase

import com.expeysi.app.data.remote.SyncEntityType
import com.expeysi.app.data.remote.SyncKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Отправка изменений на узком интерфейсе записи (без SDK): короткий путь —
 * отдельные set() по документам; длинный — батчи по 500 с ожиданием первых
 * батчей; отказ батча — перезапись по одному без исключения наружу; после
 * первого таймаута — отправка без ожидания. Виртуальное время runTest.
 */
class ChangePusherTest {

    /** Виртуальное время не растёт дальше таймаута; реальные ожиданий нет. */
    private val noRejections: (SyncKey, Exception) -> Unit = { _, _ -> }

    @Test
    fun `короткая отправка идёт отдельными set по документам без батча`() = runTest {
        val writer = FakePushWriter()
        val accepted = pushChangeBatches(plainWrites(3), writer, PUSH_ACK_TIMEOUT_MILLIS, noRejections)
        assertEquals(plainKeys(3).toSet(), accepted)
        assertEquals(3, writer.withCallback.size)
        assertTrue(writer.batchSizes.isEmpty() && writer.awaitedSets.isEmpty())
    }

    @Test
    fun `отказ одного документа доходит до колбэка, остальные не затронуты`() = runTest {
        val writer = FakePushWriter()
        val rejected = mutableListOf<SyncKey>()
        val accepted = pushChangeBatches(plainWrites(3), writer, PUSH_ACK_TIMEOUT_MILLIS) { key, _ -> rejected += key }
        assertEquals(plainKeys(3).toSet(), accepted)
        writer.invokeAsyncFailure(keyAt(1))
        assertEquals(listOf(keyAt(1)), rejected)
    }

    @Test
    fun `длинная отправка режется батчами по 500 и ждёт их подтверждения`() = runTest {
        val writer501 = FakePushWriter()
        val accepted = pushChangeBatches(plainWrites(501), writer501, PUSH_ACK_TIMEOUT_MILLIS, noRejections)
        assertEquals(listOf(500, 1), writer501.batchSizes)
        assertTrue(writer501.withCallback.isEmpty())
        assertEquals(plainKeys(501).toSet(), accepted)

        val writer1200 = FakePushWriter()
        pushChangeBatches(plainWrites(1200), writer1200, PUSH_ACK_TIMEOUT_MILLIS, noRejections)
        assertEquals(listOf(500, 500, 200), writer1200.batchSizes)
        assertTrue(writer1200.awaitedSets.isEmpty())
    }

    @Test
    fun `отказ батча перезаписывается по одному без исключения наружу`() = runTest {
        val writer = FakePushWriter()
        writer.batchScript += BatchOutcome.FAIL
        writer.setScript += SetOutcome.FAIL
        val rejected = mutableListOf<SyncKey>()
        val accepted = pushChangeBatches(plainWrites(501), writer, PUSH_ACK_TIMEOUT_MILLIS) { key, _ -> rejected += key }
        // Первый батч пошёл по одному документу (500 set), второй — с подтверждением.
        assertEquals(listOf(500, 1), writer.batchSizes)
        assertEquals(500, writer.awaitedSets.size)
        assertTrue(writer.withCallback.isEmpty())
        // Первый документ отклонён сервером: не в accepted, ключ в колбэке.
        assertEquals(listOf(keyAt(0)), rejected)
        assertEquals(500, accepted.size)
        assertTrue(keyAt(0) !in accepted)
    }

    @Test
    fun `таймаут подтверждения переводит остальные отправки в режим без ожидания`() = runTest {
        val writer = FakePushWriter()
        writer.batchScript += BatchOutcome.TIMEOUT
        val accepted = pushChangeBatches(plainWrites(501), writer, PUSH_ACK_TIMEOUT_MILLIS, noRejections)
        assertEquals(PUSH_ACK_TIMEOUT_MILLIS, testScheduler.currentTime)
        // Подтверждение ждал только первый батч; он и второй досланы одиночными set.
        assertEquals(listOf(500), writer.batchSizes)
        assertEquals(501, writer.withCallback.size)
        assertTrue(writer.awaitedSets.isEmpty())
        assertEquals(plainKeys(501).toSet(), accepted)
    }

    @Test
    fun `таймаут одиночной перезаписи досылает остаток без ожидания`() = runTest {
        val writer = FakePushWriter()
        writer.batchScript += BatchOutcome.FAIL
        writer.setScript += SetOutcome.TIMEOUT
        val accepted = pushChangeBatches(plainWrites(501), writer, PUSH_ACK_TIMEOUT_MILLIS, noRejections)
        // Подтверждение ждали только документ a0 (таймаут); батч-подтверждений больше нет.
        assertEquals(1, writer.awaitedSets.size)
        assertEquals(listOf(500), writer.batchSizes)
        // a0, остаток первого батча и второй батч ушли без ожидания: 1 + 499 + 1.
        assertEquals(501, writer.withCallback.size)
        assertEquals(plainKeys(501).toSet(), accepted)
    }

    @Test
    fun `пустая отправка возвращает пустой accepted без обращений к транспорту`() = runTest {
        val writer = FakePushWriter()
        val accepted = pushChangeBatches(emptyList(), writer, PUSH_ACK_TIMEOUT_MILLIS) { _, _ -> }
        assertTrue(accepted.isEmpty())
        assertTrue(writer.withCallback.isEmpty() && writer.batchSizes.isEmpty() && writer.awaitedSets.isEmpty())
    }

    // Фикстуры и фейк писателя.

    /** Результат ожидаемой операции записи, управляемый тестом. */
    private enum class SetOutcome { ACK, TIMEOUT, FAIL }

    /** Результат ожидаемого коммита батча, управляемый тестом. */
    private enum class BatchOutcome { ACK, TIMEOUT, FAIL }

    private companion object {
        fun plainWrites(count: Int): List<SingleWrite> = (0 until count).map { SingleWrite(keyAt(it), mapOf("updatedAt" to 1L)) }

        fun plainKeys(count: Int): List<SyncKey> = (0 until count).map { keyAt(it) }

        fun keyAt(index: Int): SyncKey = SyncKey(SyncEntityType.ACCOUNT, "a$index")
    }

    private class FakePushWriter : PushWriter {
        val withCallback = mutableListOf<SingleWrite>()
        val awaitedSets = mutableListOf<SingleWrite>()
        val batchSizes = mutableListOf<Int>()

        /** Скрипт результатов батчей и одиночных set: пустые записи считаются успешными. */
        val batchScript = mutableListOf<BatchOutcome>()
        val setScript = mutableListOf<SetOutcome>()

        private val callbacks = mutableMapOf<SyncKey, (Exception) -> Unit>()
        private var batchCalls = 0
        private var setCalls = 0

        override fun setWithCallback(write: SingleWrite, onRejected: (Exception) -> Unit) {
            withCallback += write
            callbacks[write.key] = onRejected
        }

        override fun setAwait(write: SingleWrite): PushTask {
            val outcome = setScript.getOrNull(setCalls++) ?: SetOutcome.ACK
            awaitedSets += write
            return PushTask {
                when (outcome) {
                    SetOutcome.ACK -> Unit
                    SetOutcome.TIMEOUT -> delay(60_000L)
                    SetOutcome.FAIL -> throw RuntimeException("set denied")
                }
            }
        }

        override fun commitBatchAwait(batch: List<SingleWrite>): PushTask {
            val outcome = batchScript.getOrNull(batchCalls++) ?: BatchOutcome.ACK
            batchSizes += batch.size
            return PushTask {
                when (outcome) {
                    BatchOutcome.ACK -> Unit
                    BatchOutcome.TIMEOUT -> delay(60_000L)
                    BatchOutcome.FAIL -> throw RuntimeException("batch denied")
                }
            }
        }

        /** Имитация более позднего отказа сервера по асинхронной записи. */
        fun invokeAsyncFailure(key: SyncKey) {
            callbacks.getValue(key)(RuntimeException("server denied"))
        }
    }
}
