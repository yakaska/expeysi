package com.expeysi.app.data.remote.firebase

import android.util.Log
import com.expeysi.app.data.remote.PushResult
import com.expeysi.app.data.remote.RejectionBuffer
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
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-реализация [SyncRemote].
 *
 * - [observe]: четыре snapshot-слушателя по типам (balances, accounts,
 *   categories, transactions), объединённые в один поток; каждый слушатель
 *   после ошибки переподключается через 5 с, исключения наружу не выходят.
 *   Снимок каждого типа приходит полным состоянием (isFullState = true).
 * - [push]: запись независима по документам (ChangePusher). Короткая отправка
 *   (в пределах одного батча) — отдельный set() на каждый документ без
 *   ожидания подтверждения (инвариант 5); длинная — батчи по 500, где первые
 *   батчи ждут подтверждения с таймаутом [PUSH_ACK_TIMEOUT_MILLIS] до первого
 *   таймаута; отказ батча не валит отправку — его документы перезаписываются
 *   по одному. Отказ сервера по отдельному документу фиксируется в
 *   [rejections]. Повторная отправка идемпотентна: write по тому же пути
 *   с теми же полями.
 *
 * wire-формат: коллекции families/<familyId>/{balances,accounts,categories,
 * transactions}, поля документов — как в docs/data-model.md.
 */
@Singleton
class FirebaseSyncRemote @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SyncRemote {

    /** Ключи, принятые к доставке, но позже отклонённые сервером (KDoc контракта). */
    private val rejectionBuffer = RejectionBuffer()
    override val rejections: Flow<SyncKey> get() = rejectionBuffer.flow

    override fun observe(familyId: String): Flow<RemoteUpdate> = merge(
        observeType(familyId, SyncEntityType.BALANCE),
        observeType(familyId, SyncEntityType.ACCOUNT),
        observeType(familyId, SyncEntityType.CATEGORY),
        observeType(familyId, SyncEntityType.TRANSACTION),
    )

    override suspend fun push(familyId: String, changes: List<SyncChange>): PushResult {
        val accepted = pushChangeBatches(
            writes = changes.map { SingleWrite(it.key, it.fields) },
            writer = FirestorePushWriter(familyId, firestore),
            timeoutMillis = PUSH_ACK_TIMEOUT_MILLIS,
            onRejected = { key, error ->
                Log.e(TAG, "push rejected $key", error)
                rejectionBuffer.offer(key)
            },
        )
        return PushResult(accepted)
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

    private companion object {
        const val TAG = "ExpeysiSync"

        /** Пауза переподключения snapshot-слушателя после ошибки. */
        const val LISTENER_RETRY_DELAY_MILLIS = 5_000L
    }
}

/**
 * Адаптер записи одной семьи к узкому [PushWriter]: маппинг ключа на путь
 * документа (families/<familyId>/<коллекция>) и Task на PushTask. Строится на
 * каждый push: отказ построения (нет транспорта) происходит до первой записи,
 * поэтому push не возвращает частичный accepted на неработающем транспорте.
 */
private class FirestorePushWriter(
    familyId: String,
    private val firestore: FirebaseFirestore,
) : PushWriter {
    private val family = firestore.collection("families").document(familyId)

    override fun setWithCallback(write: SingleWrite, onRejected: (Exception) -> Unit) {
        reference(write).set(write.fields).addOnFailureListener { error -> onRejected(error) }
    }

    override fun setAwait(write: SingleWrite): PushTask = PushTask { reference(write).set(write.fields).await() }

    override fun commitBatchAwait(batch: List<SingleWrite>): PushTask {
        val writeBatch = firestore.batch()
        batch.forEach { write -> writeBatch.set(reference(write), write.fields) }
        return PushTask { writeBatch.commit().await() }
    }

    private fun reference(write: SingleWrite) = family.collection(collectionName(write.key.type)).document(write.key.id)
}

/** Имя коллекции семьи для типа записи (wire-формат описан в docs/data-model.md). */
private fun collectionName(type: SyncEntityType): String = when (type) {
    SyncEntityType.BALANCE -> "balances"
    SyncEntityType.ACCOUNT -> "accounts"
    SyncEntityType.CATEGORY -> "categories"
    SyncEntityType.TRANSACTION -> "transactions"
}
