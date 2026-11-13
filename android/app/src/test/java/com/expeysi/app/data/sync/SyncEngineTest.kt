package com.expeysi.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
