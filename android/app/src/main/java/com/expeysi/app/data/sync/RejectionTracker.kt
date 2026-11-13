package com.expeysi.app.data.sync

import android.util.Log
import com.expeysi.app.data.remote.SyncKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Учёт отказов сервера по ключам: per-key backoff, независимый от backoff
 * рабочего цикла. Отказ относится к версии, отправлявшейся последней
 * ([notePushed]); попытки считаются по паре (ключ, отклонённая версия):
 * задержка попытки n — 5 с * 2^(n-1) до потолка 5 минут. Более свежая
 * локальная версия ключа (updatedAt больше) начинает счёт заново и не
 * удерживается — она может устранить причину отказа.
 *
 * Механизм: на отказ ключ ставится на удержание своей последней
 * отправленной версии; job выпуска через [backoffMillis] снимает удержание
 * и зовёт [onRelease] — тот повторно помечает строку dirty, только если она
 * всё ещё на удержанной версии, и запрашивает проход. Пока строка на
 * удержании своей версии, проход её не отправляет и не считает проход
 * неполным; любые другие триггеры (правки, слияния, N подряд триггеров)
 * удержание не снимают. Эхо ключа в снимках/дельтах счётчик не сбрасывает:
 * транспорт показывает локальные записи до отказа сервера, сброс там давал
 * бы вечный цикл по 5 с.
 *
 * Отказ без известной версии (устаревшее событие ключа, не отправлявшегося в
 * этой памяти) игнорируется. Счётчики — per-session: [reset] на старте сессии
 * семьи сбрасывает попытки и удержания; версии отправок перекрываются
 * следующей отправкой того же ключа. Запись отказов идёт из одной корутины —
 * единственного потребителя потока отказов; доступы к картам под
 * synchronized и никогда не приостанавливаются.
 */
internal class RejectionTracker(
    private val scope: CoroutineScope,
    private val onRelease: suspend (key: SyncKey, version: Long) -> Unit,
) {
    /** Счётчик попыток по отклонённой версии. */
    private data class Attempt(val version: Long, val count: Int)

    /** Удержание версии ключа до выпуска; job в [Hold.job] отменяется при замене. */
    private data class Hold(val version: Long, val job: Job? = null)

    private val lock = Any()
    private val attempts = HashMap<SyncKey, Attempt>()
    private val holds = HashMap<SyncKey, Hold>()
    private val pushedVersions = HashMap<SyncKey, Long>()

    /** Задержка попытки n: 5 с, 10 с, 20 с, ... потолок 5 мин. */
    internal fun backoffMillis(attempt: Int): Long {
        var millis = HOLD_RETRY_DELAY_MIN_MILLIS
        repeat((attempt - 1).coerceAtLeast(0)) {
            millis = (millis * 2).coerceAtMost(HOLD_RETRY_DELAY_MAX_MILLIS)
        }
        return millis
    }

    /** Версия ключа ушла в транспорт: будущий отказ относится к ней. */
    fun notePushed(key: SyncKey, version: Long) {
        synchronized(lock) { pushedVersions[key] = version }
    }

    /** Отказ по ключу: удержание последней отправленной версии. */
    fun recordRejection(key: SyncKey) {
        val version = synchronized(lock) { pushedVersions[key] }
        if (version == null) {
            Log.w(TAG, "rejection ignored, key was not pushed in this session: $key")
            return
        }
        synchronized(lock) {
            val previous = attempts[key]
            val attempt = if (previous?.version == version) previous.count + 1 else 1
            attempts[key] = Attempt(version, attempt)
            holds[key]?.job?.cancel()
            holds[key] = Hold(version)
            val delayMillis = backoffMillis(attempt)
            Log.w(TAG, "push rejected $key version=$version attempt=$attempt next push in $delayMillis ms")
            val job = scope.launch {
                delay(delayMillis)
                val released = synchronized(lock) {
                    if (holds[key]?.version == version) {
                        holds.remove(key)
                        true
                    } else {
                        false
                    }
                }
                if (released) {
                    Log.d(TAG, "rejection hold released $key version=$version")
                    onRelease(key, version)
                }
            }
            holds[key] = Hold(version, job)
        }
    }

    /** Ключ на удержании именно этой версии: строка не отправляется проходом. */
    fun isHeldAt(key: SyncKey, version: Long): Boolean = synchronized(lock) {
        holds[key]?.version == version
    }

    /**
     * Новая сессия семьи: счётчики попыток и удержания предыдущей сессии
     * сброшены, job выпуска отменены (отмена — нормальное завершение, ошибок
     * нет). Версии отправок не чистятся: проход нового сеанса может завершиться
     * до старта сессии и их записать, а стухшие версии перекрываются следующей
     * отправкой того же ключа.
     */
    fun reset() {
        synchronized(lock) {
            attempts.clear()
            holds.values.forEach { it.job?.cancel() }
            holds.clear()
        }
    }

    private companion object {
        const val TAG = "ExpeysiSync"

        /** Первая пауза повтора отклонённой версии. */
        const val HOLD_RETRY_DELAY_MIN_MILLIS = 5_000L

        /** Потолок паузы повтора отклонённой версии. */
        const val HOLD_RETRY_DELAY_MAX_MILLIS = 5 * 60 * 1000L
    }
}
