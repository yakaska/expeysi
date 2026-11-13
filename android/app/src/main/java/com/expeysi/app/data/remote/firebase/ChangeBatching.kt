package com.expeysi.app.data.remote.firebase

/**
 * Единое место батчевой записи в Firestore: размер батча, порог ожидания
 * подтверждений и таймаут ack'ов. Исполнение записи живёт в ChangePusher.kt
 * на узком интерфейсе [PushWriter]; Firebase-типы остаются в адаптере
 * FirebaseSyncRemote.
 */

/** Размер батча записи в Firestore (ограничение транспортного API). */
internal const val FIRESTORE_BATCH_SIZE = 500

/** Ожидание подтверждения первых батчей длинной отправки. */
internal const val PUSH_ACK_TIMEOUT_MILLIS = 20_000L

internal fun <T> firestoreBatches(values: List<T>): List<List<T>> = values.chunked(FIRESTORE_BATCH_SIZE)

/**
 * Решение push о режиме отправки: короткая отправка (не больше одного батча)
 * идёт отдельными set() по документам без ожидания подтверждения (инвариант 5);
 * длинная режется батчами по 500, и первые батчи ждут подтверждения — до
 * первого таймаута.
 */
internal fun pushWaitAcks(changeCount: Int): Boolean = changeCount > FIRESTORE_BATCH_SIZE
