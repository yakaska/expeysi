package com.expeysi.app.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Доставка отказов без потерь: события буферизуются до подписки и при
 * всплесках, дублирования нет, offer работает из обычного потока без
 * корутин. Виртуальное время runTest: «медленный» сборщик — delay на
 * каждое событие. Сборщик — дочерняя корутина теста, отменяемая перед
 * завершением: на бесконечном receiving-канале сам он не завершается.
 */
class RejectionBufferTest {

    @Test
    fun `событие, предложенное до подписки, доходит сборщику`() = runTest {
        val buffer = RejectionBuffer()
        val key = SyncKey(SyncEntityType.BALANCE, "b1")
        // Подписчиков ещё нет: событие обязано остаться в буфере канала.
        buffer.offer(key)
        val received = mutableListOf<SyncKey>()
        val collector = launch {
            buffer.flow.collect { received += it }
        }
        advanceUntilIdle()
        assertEquals(listOf(key), received)
        collector.cancel()
    }

    @Test
    fun `всплеск из 1000 предложений доставляется весь и по порядку`() = runTest {
        val buffer = RejectionBuffer()
        val expected = (0 until 1000).map { SyncKey(SyncEntityType.ACCOUNT, "a$it") }
        val received = mutableListOf<SyncKey>()
        val collector = launch {
            buffer.flow.collect { key ->
                received += key
                // Медленный сборщик: обработка события занимает время,
                // следующие offer копятся в буфере, а не отбрасываются.
                delay(1)
            }
        }
        advanceUntilIdle()
        expected.forEach { buffer.offer(it) }
        advanceUntilIdle()
        assertEquals(1000, received.size)
        assertEquals(expected, received)
        collector.cancel()
    }

    @Test
    fun `offer из обычного потока без корутин доходит сборщику`() = runTest {
        val buffer = RejectionBuffer()
        val key = SyncKey(SyncEntityType.BALANCE, "b1")
        val received = mutableListOf<SyncKey>()
        val collector = launch {
            buffer.flow.collect { received += it }
        }
        advanceUntilIdle()
        // Колбэк отказа транспорта живёт на обычном потоке (у Firebase — на
        // главном): offer не приостанавливается и не требует корутины.
        val callbackThread = Thread { buffer.offer(key) }
        callbackThread.start()
        callbackThread.join()
        advanceUntilIdle()
        assertEquals(listOf(key), received)
        collector.cancel()
    }

    @Test
    fun `каждое предложение доставляется ровно один раз`() = runTest {
        val buffer = RejectionBuffer()
        val first = SyncKey(SyncEntityType.BALANCE, "b1")
        val second = SyncKey(SyncEntityType.BALANCE, "b2")
        val offers = listOf(first, second, first)
        offers.forEach { buffer.offer(it) }
        val received = mutableListOf<SyncKey>()
        val collector = launch {
            buffer.flow.collect { received += it }
        }
        advanceUntilIdle()
        // Повторный offer того же ключа — отдельное событие; дублей доставки нет.
        assertEquals(3, received.size)
        assertEquals(offers, received)
        collector.cancel()
    }
}
