package com.expeysi.app.data

import com.expeysi.app.data.db.FamilyDataReset
import com.expeysi.app.data.remote.AuthProvider
import com.expeysi.app.data.remote.FamilyRemote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * FamilyRepository без транспорта: тесты инвариантов онбординга (6) на фейках
 * FamilyRemote/AuthProvider и швов FamilyStore/FamilyDataReset.
 */
class FamilyRepositoryTest {

    private val remote = FakeFamilyRemote()
    private val reset = FakeFamilyDataReset()
    private val store = FakeFamilyStore()

    private fun repository(userId: String?) = FamilyRepository(
        remote = remote,
        authRepository = FakeAuthProvider(userId),
        familyStore = store,
        familyDataReset = reset,
    )

    @Test
    fun `createFamily без входа даёт исключение`() {
        assertThrows(FamilyNotSignedInException::class.java) {
            runBlocking { repository(userId = null).createFamily() }
        }
        assertNull(remote.createdFamilyId)
        assertEquals(0, store.selectCalls)
    }

    @Test
    fun `createFamily сохраняет полученный от транспорта familyId`() {
        runBlocking {
            repository(userId = "uid-1").createFamily()
        }
        assertEquals("uid-1", remote.createdUid)
        assertEquals("Тест Нейм", remote.createdDisplayName)
        assertEquals("family-created", store.lastSelected)
        assertEquals(0, reset.clearCalls)
    }

    @Test
    fun `joinFamily без входа даёт исключение`() {
        assertThrows(FamilyNotSignedInException::class.java) {
            runBlocking { repository(userId = null).joinFamily("fam-1") }
        }
        assertNull(remote.joinedFamilyId)
        assertEquals(0, reset.clearCalls)
    }

    @Test
    fun `пустой код входа даёт исключение без обращения к транспорту`() {
        val repository = repository(userId = "uid-1")
        assertThrows(FamilyCodeEmptyException::class.java) {
            runBlocking { repository.joinFamily("") }
        }
        assertThrows(FamilyCodeEmptyException::class.java) {
            runBlocking { repository.joinFamily("   ") }
        }
        assertNull(remote.joinedFamilyId)
        assertEquals(0, reset.clearCalls)
        assertEquals(0, store.selectCalls)
    }

    @Test
    fun `joinFamily очищает Room и выбирает семью по коду с пробелами`() {
        runBlocking {
            repository(userId = "uid-2").joinFamily("  family-join  ")
        }
        assertEquals("family-join", remote.joinedFamilyId)
        assertEquals(1, reset.clearCalls)
        assertEquals("family-join", store.lastSelected)
    }

    @Test
    fun `leaveFamily при сбое remote всё равно очищает Room и FamilyStore`() {
        store.select("family-leave")
        remote.leaveError = RuntimeException("нет сети")
        runBlocking {
            repository(userId = "uid-3").leaveFamily()
        }
        assertEquals("family-leave", remote.leftFamilyId)
        assertEquals(1, reset.clearCalls)
        assertNull(store.familyId.value)
    }

    @Test
    fun `leaveFamily без выбранной семьи ничего не делает`() {
        runBlocking {
            repository(userId = "uid-3").leaveFamily()
        }
        assertNull(remote.leftFamilyId)
        assertEquals(0, reset.clearCalls)
        assertEquals(0, store.clearCalls)
    }

    private class FakeAuthProvider(
        currentUserId: String?,
    ) : AuthProvider {
        override val isSignedIn: StateFlow<Boolean> = MutableStateFlow(currentUserId != null)
        override val userId: String? = currentUserId
        override val displayName: String = "Тест Нейм"
        override suspend fun signInWithGoogle(idToken: String) = Unit
    }

    private class FakeFamilyStore : FamilyStore {
        private val _familyId = MutableStateFlow<String?>(null)
        override val familyId: StateFlow<String?> = _familyId.asStateFlow()
        var lastSelected: String? = null
        var selectCalls = 0
        var clearCalls = 0

        override fun select(id: String) {
            selectCalls++
            lastSelected = id
            _familyId.value = id
        }

        override fun clear() {
            clearCalls++
            _familyId.value = null
        }
    }

    private class FakeFamilyDataReset : FamilyDataReset {
        var clearCalls = 0
        override suspend fun clearFamilyData() {
            clearCalls++
        }
    }

    private class FakeFamilyRemote : FamilyRemote {
        var createdFamilyId: String? = null
        var createdUid: String? = null
        var createdDisplayName: String? = null
        var joinedFamilyId: String? = null
        var leftFamilyId: String? = null
        var leaveError: Exception? = null

        override suspend fun createFamily(uid: String, displayName: String): String {
            createdUid = uid
            createdDisplayName = displayName
            return "family-created"
        }

        override suspend fun joinFamily(familyId: String, uid: String, displayName: String) {
            joinedFamilyId = familyId
        }

        override suspend fun leaveFamily(familyId: String, uid: String) {
            leftFamilyId = familyId
            leaveError?.let { throw it }
        }
    }
}
