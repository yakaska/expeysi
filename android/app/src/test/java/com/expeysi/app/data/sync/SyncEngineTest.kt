package com.expeysi.app.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GRACE_TEST_MILLIS = 100L

class SyncEngineTest {

    @Test
    fun `отсутствующий удалённый документ требует загрузки`() {
        assertTrue(needsUpload(localUpdatedAt = 100, remoteUpdatedAt = null))
    }

    @Test
    fun `более новый локальный документ требует загрузки`() {
        assertTrue(needsUpload(localUpdatedAt = 101, remoteUpdatedAt = 100))
    }

    @Test
    fun `равные версии пропускаются`() {
        assertFalse(needsUpload(localUpdatedAt = 100, remoteUpdatedAt = 100))
    }

    @Test
    fun `более новая удалённая версия пропускается`() {
        assertFalse(needsUpload(localUpdatedAt = 100, remoteUpdatedAt = 101))
    }

    @Test
    fun `пакеты не превышают лимит Firestore`() {
        assertEquals(listOf(500, 1), firestoreBatches(List(501) { it }).map { it.size })
        assertEquals(listOf(500), firestoreBatches(List(500) { it }).map { it.size })
        assertEquals(emptyList<List<Int>>(), firestoreBatches(emptyList<Int>()))
    }

    @Test
    fun `содержимое совпадает при различии только метки времени и автора`() {
        val local = mapOf<String, Any?>(
            "name" to "Еда",
            "deleted" to false,
            "amountMinor" to 1500L,
            "updatedAt" to 2000L,
            "updatedBy" to "uid-2",
        )
        val remote = mapOf<String, Any?>(
            "name" to "Еда",
            "deleted" to false,
            "amountMinor" to 1500L,
            "updatedAt" to 1000L,
            "updatedBy" to "uid-1",
        )
        assertTrue(sameFields(essenceOf(local), essenceOf(remote)))
    }

    @Test
    fun `изменившееся содержимое различается`() {
        val fields = mapOf<String, Any?>("name" to "Еда", "deleted" to false, "updatedAt" to 1000L)
        assertFalse(sameFields(fields, mapOf("name" to "Транспорт", "deleted" to false, "updatedAt" to 1000L)))
        assertFalse(sameFields(fields, mapOf("name" to "Еда", "deleted" to true, "updatedAt" to 1000L)))
    }

    @Test
    fun `числа разных обёрток равны по значению`() {
        assertTrue(sameFields(mapOf("amountMinor" to 42), mapOf("amountMinor" to 42L)))
        assertFalse(sameFields(mapOf("amountMinor" to 42L), mapOf("amountMinor" to 43L)))
    }

    @Test
    fun `набор ключей участвует в сравнении содержимого`() {
        val fields = mapOf<String, Any?>("name" to "Еда", "iconKey" to "restaurant")
        // Недостающий на удалённой стороне ключ — различие содержимого.
        assertFalse(sameFields(fields, mapOf("name" to "Еда")))
        // Лишний на удалённой стороне ключ — тоже различие.
        assertFalse(sameFields(mapOf("name" to "Еда"), fields))
        // Метка времени в содержимое не входит и ключи не добавляет.
        assertTrue(sameFields(fields, essenceOf(fields + ("updatedAt" to 5L))))
    }

    @Test
    fun `сущность без метки времени и автора является содержимым`() {
        val fields = mapOf<String, Any?>(
            "name" to "Еда",
            "updatedAt" to 1000L,
            "updatedBy" to "uid-1",
        )
        assertEquals(mapOf("name" to "Еда"), essenceOf(fields))
    }

    @Test
    fun `пауза повтора слушателя растёт и ограничена`() {
        assertEquals(5_000L, backoffMillis(0))
        assertEquals(10_000L, backoffMillis(1))
        assertEquals(20_000L, backoffMillis(2))
        assertEquals(300_000L, backoffMillis(6))
        assertEquals(300_000L, backoffMillis(100))
    }

    @Test
    fun `фоновая пауза снимает синхронизацию по истечении окна`() = runTest {
        val foreground = MutableStateFlow(true)
        val states = mutableListOf<Boolean>()
        val job = launch { graceDetach(foreground, GRACE_TEST_MILLIS).collect { states.add(it) } }
        advanceUntilIdle()
        assertEquals(listOf(true), states)

        foreground.value = false
        advanceTimeBy(GRACE_TEST_MILLIS - 1)
        runCurrent()
        assertEquals(listOf(true), states)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(true, false), states)

        foreground.value = true
        runCurrent()
        assertEquals(listOf(true, false, true), states)
        job.cancel()
    }

    @Test
    fun `возвращение на передний план внутри окна переармает паузу`() = runTest {
        val foreground = MutableStateFlow(true)
        val states = mutableListOf<Boolean>()
        val job = launch { graceDetach(foreground, GRACE_TEST_MILLIS).collect { states.add(it) } }
        advanceUntilIdle()

        foreground.value = false
        advanceTimeBy(GRACE_TEST_MILLIS - 1)
        runCurrent()
        foreground.value = true
        runCurrent()
        advanceTimeBy(GRACE_TEST_MILLIS * 10)
        runCurrent()
        // Ни одного false: пауза отменена возвращением, слушатели не снимались.
        assertEquals(listOf(true), states)
        job.cancel()
    }
}
