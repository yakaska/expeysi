package com.expeysi.app.data.remote.firebase

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Единое место батчевой записи в Firestore: размер батча и логика ожидания
 * подтверждений живут только здесь — пользуется [FirebaseSyncRemote.push].
 */

/** Размер батча записи в Firestore (ограничение транспортного API). */
internal const val FIRESTORE_BATCH_SIZE = 500

internal fun <T> firestoreBatches(values: List<T>): List<List<T>> = values.chunked(FIRESTORE_BATCH_SIZE)

/**
 * Решение push о режиме отправки: короткая отправка (не больше одного батча)
 * идёт одним батчем без ожидания подтверждения (инвариант 5); длинная режется
 * батчами по 500, и первые батчи ждут подтверждения — до первого таймаута.
 */
internal fun pushWaitAcks(changeCount: Int): Boolean = changeCount > FIRESTORE_BATCH_SIZE

/**
 * Пишет [writes] батчами по 500. Первые батчи ждут подтверждения с таймаутом
 * [timeoutMillis]: подтверждение не должно держать импорт вечно, при подвисшей
 * связи записи уже в офлайн-очереди Firestore и доставятся сами — после
 * первого таймаута оставшиеся батчи отправляются без ожидания.
 */
internal suspend fun FirebaseFirestore.commitChangeBatches(
    writes: List<Pair<DocumentReference, Map<String, Any?>>>,
    timeoutMillis: Long,
) {
    val batches = firestoreBatches(writes)
    var awaitAcks = true
    batches.forEachIndexed { index, chunk ->
        val batch = batch()
        chunk.forEach { (document, data) -> batch.set(document, data) }
        when {
            awaitAcks -> try {
                withTimeout(timeoutMillis) { batch.commit().await() }
                Log.d("ExpeysiSync", "import upload batch ${index + 1}/${batches.size} acknowledged")
            } catch (e: TimeoutCancellationException) {
                awaitAcks = false
                Log.w("ExpeysiSync", "import upload batch ${index + 1}/${batches.size} deferred", e)
            }
            // Подтверждение не ждём: commit кладёт записи в очередь при вызове.
            else -> batch.commit()
        }
    }
    Log.d(TAG, "import upload documents=${writes.size}")
}

private const val TAG = "ExpeysiSync"
