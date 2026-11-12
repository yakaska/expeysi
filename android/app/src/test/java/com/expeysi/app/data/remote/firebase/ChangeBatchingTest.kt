package com.expeysi.app.data.remote.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Чистая часть решения push: батчи по 500 и порог ожидания подтверждений
 * (короткая отправка — без ожидания, длинная — с ожиданием первых батчей).
 */
class ChangeBatchingTest {

    @Test
    fun `батчи ровно по 500 документов Firestore`() {
        assertEquals(listOf(500, 1), firestoreBatches(List(501) { it }).map { it.size })
        assertEquals(listOf(500), firestoreBatches(List(500) { it }).map { it.size })
        assertEquals(emptyList<List<Int>>(), firestoreBatches(emptyList<Int>()))
    }

    @Test
    fun `push в пределах одного батча идёт без ожидания подтверждения`() {
        assertFalse(pushWaitAcks(changeCount = 0))
        assertFalse(pushWaitAcks(changeCount = 1))
        assertFalse(pushWaitAcks(changeCount = 500))
    }

    @Test
    fun `push сверх батча режется по 500 с ожиданием первых батчей`() {
        assertTrue(pushWaitAcks(changeCount = 501))
        assertTrue(pushWaitAcks(changeCount = 1200))
        assertEquals(listOf(500, 1), firestoreBatches(List(501) { it }).map { it.size })
        assertEquals(listOf(500, 500, 200), firestoreBatches(List(1200) { it }).map { it.size })
    }
}
