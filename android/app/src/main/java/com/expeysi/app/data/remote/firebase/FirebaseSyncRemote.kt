package com.expeysi.app.data.remote.firebase

import android.util.Log
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
import kotlinx.coroutines.flow.retryWhen
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-реализация [SyncRemote]. Поведение дословно прежнее из SyncEngine:
 * - снимок коллекции семьи -> [RemoteUpdate] с полным состоянием, разрыв связи
 *   закрывает поток и переподключается через 5 с;
 * - одиночная запись и батчи импорта подтверждения не ждут (кроме первых
 *   батчей импорта — см. [commitChangeBatches]).
 * wire-формат: коллекции families/<familyId>/{balances,accounts,categories,
 * transactions}, поля документов — как в docs/data-model.md.
 */
@Singleton
class FirebaseSyncRemote @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SyncRemote {

    override fun observeChanges(familyId: String, type: SyncEntityType): Flow<RemoteUpdate> = callbackFlow {
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

    override suspend fun postChange(familyId: String, change: SyncChange) {
        // Подтверждение не ждём: set кладёт запись в офлайн-очередь при вызове.
        firestore.collection("families").document(familyId)
            .collection(collectionName(change.key.type))
            .document(change.key.id)
            .set(change.fields)
    }

    override suspend fun uploadBulk(familyId: String, changes: List<SyncChange>, timeoutMillis: Long) {
        val family = firestore.collection("families").document(familyId)
        val writes = changes.map { change ->
            family.collection(collectionName(change.key.type))
                .document(change.key.id) to change.fields
        }
        firestore.commitChangeBatches(writes, timeoutMillis)
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
    }
}
