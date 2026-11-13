package com.expeysi.app.data.remote.firebase

import android.util.Log
import com.expeysi.app.data.remote.SyncKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Единое место отправки изменений в Firebase. Логика живёт на узком интерфейсе
 * [PushWriter] и не знает Firestore: Firebase-типы остаются в адаптере
 * FirebaseSyncRemote, JVM-тесты гоняют её на фейковом писателе.
 *
 * Режимы отправки (решение — `pushWaitAcks`):
 * - короткая (в пределах одного батча): отдельный set() на каждый документ,
 *   подтверждение не ждём (инвариант 5 — записи попадают в офлайн-очередь при
 *   вызове); accepted перечисляет все ключи, отказ сервера приходит позже
 *   через колбэк отказа;
 * - длинная: батчи по [FIRESTORE_BATCH_SIZE] с ожиданием подтверждения первых
 *   батчей до первого таймаута — после него все отправки идут без ожидания.
 *   Отказ батча, не являющийся таймаутом, не валит отправку: его документы
 *   перезаписываются по одному, остальные батчи продолжают отправляться.
 *
 * Отказы сервера по отдельным документам всегда фиксируются колбэком [SyncKey]:
 * в коротком режиме и после досылки без ожидания ключ остаётся в accepted
 * (запись принята к доставке, отказ сервера выявился позже); в перезаписи
 * по одному отклонённый документ в accepted не попадает. Исключение наружу
 * выходит только если не передано ничего (запись не начата); настоящая
 * отмена корутины ([CancellationException], кроме таймаута подтверждения)
 * пробрасывается.
 */

/** Одна исходящая запись: адрес (ключ) и полный wire-формат полей. */
internal data class SingleWrite(val key: SyncKey, val fields: Map<String, Any?>)

/** Одна операция записи с ожиданием результата; await() бросает отказ сервера. */
internal fun interface PushTask {
    suspend fun await()
}

/**
 * Узкий интерфейс записи, достаточный push-логике: асинхронный set с колбэком
 * отказа, одиночный set с ожиданием и запись батча с ожиданием.
 */
internal interface PushWriter {

    /** Отправка записи без ожидания (инвариант 5). */
    fun setWithCallback(write: SingleWrite, onRejected: (Exception) -> Unit)

    /** Отправка записи с ожиданием результата. */
    fun setAwait(write: SingleWrite): PushTask

    /** Запись батча с ожиданием результата. */
    fun commitBatchAwait(batch: List<SingleWrite>): PushTask
}

/**
 * Отправляет [writes] в принятом для размера режиме (см. KDoc) и возвращает
 * ключи, принятые к доставке. Отказы сервера по документам доставляет
 * [onRejected] — в обоих режимах записи.
 */
internal suspend fun pushChangeBatches(
    writes: List<SingleWrite>,
    writer: PushWriter,
    timeoutMillis: Long,
    onRejected: (SyncKey, Exception) -> Unit,
): Set<SyncKey> {
    if (writes.isEmpty()) return emptySet()
    val accepted = mutableSetOf<SyncKey>()
    if (!pushWaitAcks(writes.size)) {
        sendWithoutAck(writes, writer, onRejected, accepted)
        return accepted
    }
    val batches = firestoreBatches(writes)
    var awaitAcks = true
    batches.forEachIndexed { index, batch ->
        if (awaitAcks) {
            when (awaitBatch(batch, writer, timeoutMillis)) {
                BatchOutcome.ACKNOWLEDGED -> {
                    Log.d(TAG, "push batch ${index + 1}/${batches.size} acknowledged")
                    accepted += batch.map { it.key }
                    return@forEachIndexed
                }
                BatchOutcome.TIMED_OUT -> {
                    // После первого таймаута оставшиеся отправки идут в
                    // офлайн-очередь без ожидания (инвариант 5), включая досылку
                    // этого батча.
                    Log.w(TAG, "push batch ${index + 1}/${batches.size} timed out")
                    awaitAcks = false
                }
                BatchOutcome.REJECTED -> {
                    // Отказ батча не валит отправку: его документы перезаписываются
                    // по одному, остальные батчи продолжают отправляться.
                    if (rewriteByDocuments(batch, writer, timeoutMillis, onRejected, accepted)) {
                        awaitAcks = false
                    }
                    return@forEachIndexed
                }
            }
        }
        sendWithoutAck(batch, writer, onRejected, accepted)
    }
    return accepted
}

private enum class BatchOutcome { ACKNOWLEDGED, TIMED_OUT, REJECTED }

private suspend fun awaitBatch(
    batch: List<SingleWrite>,
    writer: PushWriter,
    timeoutMillis: Long,
): BatchOutcome = try {
    withTimeout(timeoutMillis) { writer.commitBatchAwait(batch).await() }
    BatchOutcome.ACKNOWLEDGED
} catch (e: TimeoutCancellationException) {
    BatchOutcome.TIMED_OUT
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w(TAG, "push batch failed", e)
    BatchOutcome.REJECTED
}

/**
 * Перезаписывает отказавший батч по одному документу: принятые попадают в
 * [accepted], отклонённые сервером — в [onRejected]. Возвращает true, если в
 * перезаписи встретился таймаут: дальнейшие отправки — без ожидания.
 */
private suspend fun rewriteByDocuments(
    batch: List<SingleWrite>,
    writer: PushWriter,
    timeoutMillis: Long,
    onRejected: (SyncKey, Exception) -> Unit,
    accepted: MutableSet<SyncKey>,
): Boolean {
    var awaitAcks = true
    batch.forEach { write ->
        if (!awaitAcks) {
            sendWithoutAck(listOf(write), writer, onRejected, accepted)
            return@forEach
        }
        try {
            withTimeout(timeoutMillis) { writer.setAwait(write).await() }
            accepted += write.key
        } catch (e: TimeoutCancellationException) {
            // Досылка без ожидания; остальные записи так же.
            awaitAcks = false
            sendWithoutAck(listOf(write), writer, onRejected, accepted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "push rejected ${write.key}", e)
            onRejected(write.key, e)
        }
    }
    return !awaitAcks
}

/** Отправка без ожидания (инвариант 5): accepted сразу, отказ приходит позже колбэком. */
private fun sendWithoutAck(
    writes: List<SingleWrite>,
    writer: PushWriter,
    onRejected: (SyncKey, Exception) -> Unit,
    accepted: MutableSet<SyncKey>,
) {
    writes.forEach { write ->
        writer.setWithCallback(write) { error -> onRejected(write.key, error) }
        accepted += write.key
    }
}

private const val TAG = "ExpeysiSync"
