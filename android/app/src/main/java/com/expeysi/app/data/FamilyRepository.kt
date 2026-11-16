package com.expeysi.app.data

import android.util.Log
import androidx.room.withTransaction
import com.expeysi.app.data.db.AppDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val familyStore: FamilyStore,
    private val database: AppDatabase,
) {
    suspend fun createFamily() {
        val uid = checkSignedIn()
        val family = firestore.collection("families").document()
        firestore.runBatch { batch ->
            batch.set(
                family,
                mapOf(
                    "name" to "Семья",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "createdBy" to uid,
                ),
            )
            batch.set(family.collection("members").document(uid), memberData(uid))
        }
        familyStore.select(family.id)
    }

    suspend fun joinFamily(code: String) {
        val uid = checkSignedIn()
        val familyId = code.trim()
        if (familyId.isEmpty()) throw FamilyCodeEmptyException()
        // Сначала проверяем существование семьи: запись члена в несуществующую
        // семью неявно создала бы родительский документ, и «любая строка
        // подходящего формата» присоединялась бы к фиктивной семье.
        // Source.SERVER: офлайн-кэш не подмешивается — без сети будет ошибка
        // соединения, а не ложное «код не найден».
        val family = try {
            firestore.collection("families").document(familyId)
                .get(Source.SERVER)
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Сбой самой проверки (правила не дают get, проблемы сервера) —
            // отдельная ошибка: причина внутри, чтобы не путать её с
            // «код не найден».
            throw FamilyCodeCheckException(e)
        }
        if (!family.exists()) throw FamilyNotFoundException()
        firestore.collection("families").document(familyId)
            .collection("members").document(uid)
            .set(memberData(uid))
        database.withTransaction {
            database.transactionDao().clear()
            database.accountDao().clear()
            database.categoryDao().clear()
            database.balanceDao().clear()
        }
        familyStore.select(familyId)
    }

    suspend fun leaveFamily() {
        val uid = checkSignedIn()
        val familyId = familyStore.familyId.value ?: return
        Log.d(TAG, "leave start uid=$uid family=$familyId")
        runCatching {
            // Таймаут, чтобы подтверждение удаления не могло держать загрузку
            // вечно: мутация остаётся в офлайн-очереди Firestore и применится
            // на сервере, как только появится сеть.
            withTimeout(MEMBER_DELETE_TIMEOUT_MILLIS) {
                firestore.collection("families").document(familyId)
                    .collection("members").document(uid)
                    .delete()
                    .await()
            }
        }.onFailure { error ->
            // Отказ удалить своё членство (нет сети, доступ запрещён, членство
            // уже снято на сервере) не отменяет выход: локальное состояние всё
            // равно сбрасываем, мутация дойдёт до сервера сама.
            Log.w(TAG, "member delete not acknowledged", error)
        }
        database.withTransaction {
            Log.d(TAG, "room clear begin")
            database.transactionDao().clear()
            database.accountDao().clear()
            database.categoryDao().clear()
            database.balanceDao().clear()
            Log.d(TAG, "room clear end")
        }
        familyStore.clear()
        Log.d(TAG, "family store cleared")
    }

    private fun memberData(uid: String) = mapOf(
        "displayName" to authRepository.displayName,
        "joinedAt" to FieldValue.serverTimestamp(),
        "uid" to uid,
    )

    private fun checkSignedIn(): String {
        val uid = authRepository.userId ?: throw FamilyNotSignedInException()
        return uid
    }
}

/** Вызывается без входа в аккаунт; текст показа резолвится в UI-слое. */
class FamilyNotSignedInException : Exception()

private const val TAG = "ExpeysiFamily"

/** Ожидание подтверждения удаления членства, после которого выход идёт локально. */
private const val MEMBER_DELETE_TIMEOUT_MILLIS = 10_000L

/** Вызован вход в семью с пустым кодом; текст показа резолвится в UI-слое. */
class FamilyCodeEmptyException : Exception()

/** Семья с таким кодом не найдена (проверка перед присоединением); текст показа резолвится в UI-слое. */
class FamilyNotFoundException : Exception()

/**
 * Сам запрос проверки семьи не удался (правила не дают get, проблемы сервера);
 * причина внутри для разбора в UI-слое. Не путать с FamilyNotFoundException:
 * это «не удалось проверить», а не «код не найден».
 */
class FamilyCodeCheckException(cause: Throwable) : Exception(cause)
