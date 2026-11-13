package com.expeysi.app.data

import android.util.Log
import com.expeysi.app.data.db.FamilyDataReset
import com.expeysi.app.data.remote.AuthProvider
import com.expeysi.app.data.remote.FamilyRemote
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Онбординг семьи: удалённые мутации через [FamilyRemote] плюс локальные
 * действия — очистка Room и переключение [FamilyStore]. createFamily и
 * joinFamily не ждут подтверждения записи (см. контракт FamilyRemote);
 * выход ждёт удаления членства с таймаутом и при сбое всё равно очищает
 * локальные данные.
 */
@Singleton
class FamilyRepository @Inject constructor(
    private val remote: FamilyRemote,
    private val authRepository: AuthProvider,
    private val familyStore: FamilyStore,
    private val familyDataReset: FamilyDataReset,
) {
    suspend fun createFamily() {
        val uid = checkSignedIn()
        val familyId = remote.createFamily(uid, authRepository.displayName)
        familyStore.select(familyId)
    }

    suspend fun joinFamily(code: String) {
        val uid = checkSignedIn()
        val familyId = code.trim()
        if (familyId.isEmpty()) throw FamilyCodeEmptyException()
        remote.joinFamily(familyId, uid, authRepository.displayName)
        familyDataReset.clearFamilyData()
        familyStore.select(familyId)
    }

    suspend fun leaveFamily() {
        val uid = checkSignedIn()
        val familyId = familyStore.familyId.value ?: return
        Log.d(TAG, "leave start uid=$uid family=$familyId")
        runCatching {
            // Таймаут, чтобы подтверждение удаления не могло держать выход
            // вечно: мутация остаётся в очереди удалённого хранилища и
            // применится на сервере, как только появится сеть.
            withTimeout(MEMBER_DELETE_TIMEOUT_MILLIS) {
                remote.leaveFamily(familyId, uid)
            }
        }.onFailure { error ->
            // Отказ удалить своё членство (нет сети, доступ запрещён, членство
            // уже снято на сервере) не отменяет выход: локальное состояние всё
            // равно сбрасываем, мутация дойдёт до сервера сама.
            Log.w(TAG, "member delete not acknowledged", error)
        }
        familyDataReset.clearFamilyData()
        familyStore.clear()
        Log.d(TAG, "family store cleared")
    }

    private fun checkSignedIn(): String {
        val uid = authRepository.userId ?: throw FamilyNotSignedInException()
        return uid
    }
}

/** Вызывается без входа в аккаунт; текст показа резолвится в UI-слое. */
class FamilyNotSignedInException : Exception()

/** Вызован вход в семью с пустым кодом; текст показа резолвится в UI-слое. */
class FamilyCodeEmptyException : Exception()

private const val TAG = "ExpeysiFamily"

/** Ожидание подтверждения удаления членства, после которого выход идёт локально. */
private const val MEMBER_DELETE_TIMEOUT_MILLIS = 10_000L
