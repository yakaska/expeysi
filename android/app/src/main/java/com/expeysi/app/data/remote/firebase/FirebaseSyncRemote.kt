package com.expeysi.app.data.remote.firebase

import android.util.Log
import com.expeysi.app.data.remote.PushResult
import com.expeysi.app.data.remote.RemoteUpdate
import com.expeysi.app.data.remote.SyncChange
import com.expeysi.app.data.remote.SyncEntityType
import com.expeysi.app.data.remote.SyncKey
import com.expeysi.app.data.remote.SyncRemote
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-реализация [SyncRemote].
 *
 * - [observe]: четыре snapshot-слушателя по типам (balances, accounts,
 *   categories, transactions), объединённые в один поток; каждый слушатель
 *   после ошибки переподключается через 5 с, исключения наружу не выходят.
 *   Снимок каждого типа приходит полным состоянием (isFullState = true).
 * - [push]: короткая отправка (не больше одного батча) идёт одним батчем без
 *   ожидания подтверждения (инвариант 5, как прежний set()); длинная —
 *   батчами по 500, где первые батчи ждут подтверждения с таймаутом
 *   [PUSH_ACK_TIMEOUT_MILLIS], после первого таймаута остальные — без
 *   ожидания. И те и другие считаются принятыми: записи ушли в
 *   офлайн-очередь и доставятся с сетью. Прочие ошибки бросаются наружу
 *   (ничего не принято). Повторная отправка идемпотентна: write по тому же
 *   пути с теми же полями.
 *
 * wire-формат: коллекции families/<familyId>/{balances,accounts,categories,
 * transactions}, поля документов — как в docs/data-model.md.
 */
@Singleton
class FirebaseSyncRemote @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SyncRemote {

    override fun observe(familyId: String): Flow<RemoteUpdate> = merge(
        observeType(familyId, SyncEntityType.BALANCE),
        observeType(familyId, SyncEntityType.ACCOUNT),
        observeType(familyId, SyncEntityType.CATEGORY),
        observeType(familyId, SyncEntityType.TRANSACTION),
    )

    override suspend fun push(familyId: String, changes: List<SyncChange>): PushResult {
        val family = firestore.collection("families").document(familyId)
        val writes = changes.map { change ->
            family.collection(collectionName(change.key.type))
                .document(change.key.id) to change.fields
        }
        if (writes.isEmpty()) return PushResult(emptySet())
        if (!pushWaitAcks(writes.size)) {
            val batch = firestore.batch()
            writes.forEach { (document, data) -> batch.set(document, data) }
            // Подтверждение не ждём (инвариант 5): записи попадают в офлайн-очередь при вызове.
            batch.commit()
        } else {
            firestore.commitChangeBatches(writes, PUSH_ACK_TIMEOUT_MILLIS)
        }
        return PushResult(changes.map { it.key }.toSet())
    }

    private fun observeType(familyId: String, type: SyncEntityType): Flow<RemoteUpdate> = callbackFlow {
        val name = collectionName(type)
        val registration = firestore.collection("families").document(familyId).collection(name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "listener $name", error)
                    close(error)
                } else if (snapshot != null) {
                    Log.d(TAG, "snapshot $name=${snapshot.documents.size}")
                    trySend(
                        RemoteUpdate(
                            type = type,
                            isFullState = true,
                            changes = snapshot.documents.map { document ->
                                SyncChange(
                                    key = SyncKey(type, document.id),
                                    updatedAt = document.getLong("updatedAt") ?: 0,
                                    fields = document.data ?: emptyMap(),
                                )
                            },
                        ),
                    )
                }
            }
        awaitClose { registration.remove() }
    }.retryWhen { _, _ ->
        delay(LISTENER_RETRY_DELAY_MILLIS)
        true
    }

    private fun collectionName(type: SyncEntityType): String = when (type) {
        SyncEntityType.BALANCE -> "balances"
        SyncEntityType.ACCOUNT -> "accounts"
        SyncEntityType.CATEGORY -> "categories"
        SyncEntityType.TRANSACTION -> "transactions"
    }

    private companion object {
        const val TAG = "ExpeysiSync"

        /** Пауза переподключения snapshot-слушателя после ошибки. */
        const val LISTENER_RETRY_DELAY_MILLIS = 5_000L

        /** Ожидание подтверждения первых батчей длинной отправки. */
        const val PUSH_ACK_TIMEOUT_MILLIS = 20_000L
    }
}
