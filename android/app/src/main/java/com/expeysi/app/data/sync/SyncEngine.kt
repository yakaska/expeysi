package com.expeysi.app.data.sync

import android.util.Log
import com.expeysi.app.AppForeground
import com.expeysi.app.data.AuthRepository
import com.expeysi.app.data.FamilyStore
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEngine @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val appForeground: AppForeground,
    private val familyStore: FamilyStore,
    private val balanceDao: BalanceDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    /** Доставленные снимки удалённых коллекций текущей семьи: версии и содержимое. */
    private val remote = RemoteState()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            // Сессия держится на трёх условиях: семья, вход и видимость приложения.
            // Снятие слушателей в фоне после паузы не тратит платные realtime-чтения
            // на телефон, который никто не смотрит; возвращение на передний план
            // переподнимает их — офлайн-кэш Firestore и Room от этого не страдают.
            combine(
                familyStore.familyId,
                authRepository.isSignedIn,
                graceDetach(appForeground.active, SYNC_BACKGROUND_GRACE_MILLIS),
            ) { familyId, signedIn, active ->
                if (familyId != null && signedIn && active) familyId else null
            }.collectLatest { familyId ->
                Log.d(TAG, "family=$familyId user=${authRepository.userId}")
                if (familyId != null) {
                    coroutineScope {
                        remote.reset()
                        val initialized = List(4) { CompletableDeferred<Unit>() }
                        launch { listen(familyId, COLLECTION_BALANCES, initialized[0], ::mergeBalance) }
                        launch { listen(familyId, COLLECTION_ACCOUNTS, initialized[1], ::mergeAccount) }
                        launch { listen(familyId, COLLECTION_CATEGORIES, initialized[2], ::mergeCategory) }
                        launch { listen(familyId, COLLECTION_TRANSACTIONS, initialized[3], ::mergeTransaction) }
                        initialized.awaitAll()
                        uploadMissingLocal()
                    }
                }
            }
        }
    }

    private suspend fun uploadMissingLocal() {
        val balances = balanceDao.observeAll().first { it.isNotEmpty() }
        val accounts = accountDao.observeAll().first { it.isNotEmpty() }
        val categories = categoryDao.observeAll().first { it.isNotEmpty() }
        val transactions = transactionDao.observeAll().first()
        val writes = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()
        var wrote = 0
        var unchanged = 0
        fun stage(name: String, id: String, fields: Map<String, Any?>) {
            if (stageWrite(name, id, fields, writes)) wrote++ else unchanged++
        }
        balances.forEach {
            if (needsUpload(it.updatedAt, remote.updatedAt(COLLECTION_BALANCES, it.id))) {
                stage(COLLECTION_BALANCES, it.id, balanceFields(it))
            }
        }
        accounts.forEach {
            if (needsUpload(it.updatedAt, remote.updatedAt(COLLECTION_ACCOUNTS, it.id))) {
                stage(COLLECTION_ACCOUNTS, it.id, accountFields(it))
            }
        }
        categories.forEach {
            if (needsUpload(it.updatedAt, remote.updatedAt(COLLECTION_CATEGORIES, it.id))) {
                stage(COLLECTION_CATEGORIES, it.id, categoryFields(it))
            }
        }
        transactions.forEach {
            if (needsUpload(it.updatedAt, remote.updatedAt(COLLECTION_TRANSACTIONS, it.id))) {
                stage(COLLECTION_TRANSACTIONS, it.id, transactionFields(it))
            }
        }
        commitInBatches(writes)
        val total = balances.size + accounts.size + categories.size + transactions.size
        Log.d(TAG, "startup upload wrote=$wrote unchanged=$unchanged skipped=${total - wrote - unchanged}")
    }

    fun uploadBalance(value: BalanceEntity) {
        writeEntity(COLLECTION_BALANCES, value.id, balanceFields(value))
    }

    fun uploadAccount(value: AccountEntity) {
        writeEntity(COLLECTION_ACCOUNTS, value.id, accountFields(value))
    }

    fun uploadCategory(value: CategoryEntity) {
        writeEntity(COLLECTION_CATEGORIES, value.id, categoryFields(value))
    }

    fun uploadTransaction(value: TransactionEntity) {
        writeEntity(COLLECTION_TRANSACTIONS, value.id, transactionFields(value))
    }

    /**
     * Каскадное мягкое удаление баланса: баланс, его счета и их операции
     * отправляются пачками батчей вместо отдельных set(); уже удалённые
     * на сервере документы не перезаписываются.
     */
    fun uploadBalanceDeletion(
        balance: BalanceEntity,
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
    ) {
        if (familyStore.familyId.value == null) return
        val writes = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()
        var wrote = 0
        var unchanged = 0
        fun stage(name: String, id: String, fields: Map<String, Any?>) {
            if (stageWrite(name, id, fields, writes)) wrote++ else unchanged++
        }
        stage(COLLECTION_BALANCES, balance.id, balanceFields(balance))
        accounts.forEach { stage(COLLECTION_ACCOUNTS, it.id, accountFields(it)) }
        transactions.forEach { stage(COLLECTION_TRANSACTIONS, it.id, transactionFields(it)) }
        commitInBatches(writes)
        Log.d(TAG, "balance deletion wrote=$wrote unchanged=$unchanged")
    }

    suspend fun uploadImportedData(
        balances: List<BalanceEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
    ) {
        val familyId = familyStore.familyId.value ?: return
        val family = firestore.collection("families").document(familyId)
        val writes = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()
        balances.forEach { value ->
            writes += family.collection(COLLECTION_BALANCES).document(value.id) to balanceFields(value)
        }
        accounts.forEach { value ->
            writes += family.collection(COLLECTION_ACCOUNTS).document(value.id) to accountFields(value)
        }
        categories.forEach { value ->
            writes += family.collection(COLLECTION_CATEGORIES).document(value.id) to categoryFields(value)
        }
        transactions.forEach { value ->
            writes += family.collection(COLLECTION_TRANSACTIONS).document(value.id) to transactionFields(value)
        }
        val batches = firestoreBatches(writes)
        var awaitAcks = true
        batches.forEachIndexed { index, chunk ->
            val batch = firestore.batch()
            chunk.forEach { (document, data) -> batch.set(document, data) }
            when {
                // Подтверждение батча не должно держать импорт вечно: при подвисшей
                // связи записи уже в офлайн-очереди Firestore и доставятся сами.
                awaitAcks -> try {
                    withTimeout(IMPORT_UPLOAD_TIMEOUT_MILLIS) { batch.commit().await() }
                    Log.d(TAG, "import upload batch ${index + 1}/${batches.size} acknowledged")
                } catch (e: TimeoutCancellationException) {
                    awaitAcks = false
                    Log.w(TAG, "import upload batch ${index + 1}/${batches.size} deferred", e)
                }
                // Подтверждение не ждём: commit кладёт записи в очередь при вызове.
                else -> batch.commit()
            }
        }
        Log.d(TAG, "import upload documents=${writes.size}")
    }

    private fun collection(name: String): CollectionReference? = familyStore.familyId.value?.let {
        firestore.collection("families").document(it).collection(name)
    }

    private fun balanceFields(value: BalanceEntity) = mapOf(
        "name" to value.name,
        "currencyCode" to value.currencyCode,
        "iconKey" to value.iconKey,
        "colorKey" to value.colorKey,
        "deleted" to value.deleted,
        "updatedAt" to value.updatedAt,
    )

    private fun accountFields(value: AccountEntity) = mapOf(
        "name" to value.name,
        "iconKey" to value.iconKey,
        "colorKey" to value.colorKey,
        "openingBalanceMinor" to value.openingBalanceMinor,
        "balanceId" to value.balanceId,
        "deleted" to value.deleted,
        "updatedAt" to value.updatedAt,
    )

    private fun categoryFields(value: CategoryEntity) = mapOf(
        "name" to value.name,
        "iconKey" to value.iconKey,
        "colorKey" to value.colorKey,
        "type" to value.type,
        "deleted" to value.deleted,
        "updatedAt" to value.updatedAt,
    )

    private fun transactionFields(value: TransactionEntity) = mapOf(
        "type" to value.type,
        "amountMinor" to value.amountMinor,
        "accountId" to value.accountId,
        "categoryId" to value.categoryId,
        "date" to value.date,
        "note" to value.note,
        "deleted" to value.deleted,
        "updatedAt" to value.updatedAt,
        "updatedBy" to (authRepository.userId ?: value.updatedBy),
    )

    /** Неизменившееся содержимое не пишется: без снимка удалённой стороны запись не пропускается. */
    private fun isUnchanged(name: String, id: String, fields: Map<String, Any?>): Boolean {
        val remoteEssence = remote.fields(name, id)?.let(::essenceOf) ?: return false
        return sameFields(essenceOf(fields), remoteEssence)
    }

    /** Разовая запись документа: действие пользователя или ретрансляция слияния. */
    private fun writeEntity(name: String, id: String, fields: Map<String, Any?>) {
        if (isUnchanged(name, id, fields)) {
            Log.d(TAG, "upload $name/$id skipped: unchanged")
            return
        }
        collection(name)?.document(id)?.set(fields)
    }

    /** Готовит отложенную запись батчем; неизменившееся содержимое пропускает. */
    private fun stageWrite(
        name: String,
        id: String,
        fields: Map<String, Any?>,
        writes: MutableList<Pair<DocumentReference, Map<String, Any?>>>,
    ): Boolean {
        if (isUnchanged(name, id, fields)) return false
        collection(name)?.document(id)?.let { writes += it to fields }
        return true
    }

    /** Отправка пачками ≤500 без ожидания подтверждения: офлайн-очередь Firestore доставит сама. */
    private fun commitInBatches(writes: List<Pair<DocumentReference, Map<String, Any?>>>) {
        firestoreBatches(writes).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (document, data) -> batch.set(document, data) }
            batch.commit()
        }
    }

    private suspend fun listen(
        familyId: String,
        name: String,
        initialized: CompletableDeferred<Unit>,
        merge: suspend (DocumentSnapshot) -> Unit,
    ) {
        changes(familyId, name).collect { documents ->
            Log.d(TAG, "snapshot $name=${documents.size}")
            val documentsState = documents.associate { document ->
                document.id to RemoteDocument(
                    updatedAt = document.getLong("updatedAt") ?: 0,
                    fields = document.data ?: emptyMap(),
                )
            }
            remote.replace(name, documentsState)
            documents.forEach { document -> merge(document) }
            initialized.complete(Unit)
        }
    }

    private fun changes(familyId: String, name: String): Flow<List<DocumentSnapshot>> {
        var failureStreak = 0
        return callbackFlow {
            val registration = firestore.collection("families").document(familyId).collection(name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "listener $name", error)
                        close(error)
                    } else if (snapshot != null) {
                        trySend(snapshot.documents)
                    }
                }
            awaitClose { registration.remove() }
        }
            .retryWhen { cause, _ ->
                // PERMISSION_DENIED повторов не запускает: членство в семье снято
                // сервером, переконнекты лишь гоняли бы полные снимки впустую.
                // Остальные сбои — паузами с ростом до потолка.
                when ((cause as? FirebaseFirestoreException)?.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                        Log.w(TAG, "listener $name stopped: $cause")
                        false
                    }
                    else -> {
                        delay(backoffMillis(failureStreak))
                        failureStreak++
                        true
                    }
                }
            }
            .onEach { failureStreak = 0 }
            .catch {
                // Остановка без повторов не должна ронять процесс: сессия живёт
                // без этой коллекции, а отправка при старте не начнётся без
                // полного набора снимков (initialized не завершён).
            }
    }

    private suspend fun mergeBalance(document: DocumentSnapshot) {
        val value = BalanceEntity(
            id = document.id,
            name = document.getString("name") ?: return,
            currencyCode = document.getString("currencyCode") ?: "RUB",
            iconKey = document.getString("iconKey") ?: "",
            colorKey = document.getString("colorKey") ?: "default",
            deleted = document.getBoolean("deleted") ?: false,
            updatedAt = document.getLong("updatedAt") ?: 0,
        )
        val local = balanceDao.byId(value.id)
        when {
            local == null || value.updatedAt > local.updatedAt -> balanceDao.upsert(value)
            value.updatedAt < local.updatedAt -> uploadBalance(local)
        }
    }

    private suspend fun mergeAccount(document: DocumentSnapshot) {
        val value = AccountEntity(
            id = document.id,
            name = document.getString("name") ?: return,
            iconKey = document.getString("iconKey") ?: "",
            colorKey = document.getString("colorKey") ?: "default",
            openingBalanceMinor = document.getLong("openingBalanceMinor") ?: 0,
            balanceId = document.getString("balanceId") ?: return,
            deleted = document.getBoolean("deleted") ?: false,
            updatedAt = document.getLong("updatedAt") ?: 0,
        )
        val local = accountDao.byId(value.id)
        when {
            local == null || value.updatedAt > local.updatedAt -> accountDao.upsert(value)
            value.updatedAt < local.updatedAt -> uploadAccount(local)
        }
    }

    private suspend fun mergeCategory(document: DocumentSnapshot) {
        val value = CategoryEntity(
            id = document.id,
            name = document.getString("name") ?: return,
            iconKey = document.getString("iconKey") ?: "",
            colorKey = document.getString("colorKey") ?: "default",
            type = document.getString("type") ?: return,
            deleted = document.getBoolean("deleted") ?: false,
            updatedAt = document.getLong("updatedAt") ?: 0,
        )
        val local = categoryDao.byId(value.id)
        when {
            local == null || value.updatedAt > local.updatedAt -> categoryDao.upsert(value)
            value.updatedAt < local.updatedAt -> uploadCategory(local)
        }
    }

    private suspend fun mergeTransaction(document: DocumentSnapshot) {
        val value = TransactionEntity(
            id = document.id,
            type = document.getString("type") ?: return,
            amountMinor = document.getLong("amountMinor") ?: return,
            accountId = document.getString("accountId") ?: return,
            categoryId = document.getString("categoryId") ?: return,
            date = document.getLong("date") ?: return,
            note = document.getString("note") ?: "",
            deleted = document.getBoolean("deleted") ?: false,
            updatedAt = document.getLong("updatedAt") ?: 0,
            updatedBy = document.getString("updatedBy") ?: "",
        )
        val local = transactionDao.byId(value.id)
        when {
            local == null || value.updatedAt > local.updatedAt -> transactionDao.upsert(value)
            value.updatedAt < local.updatedAt -> uploadTransaction(local)
        }
    }
}

/** Последнее доставленное состояние удалённого документа: версия и полный payload. */
private data class RemoteDocument(
    val updatedAt: Long,
    val fields: Map<String, Any?>,
)

/** Снимки удалённых коллекций текущей семьи: полный payload последнего события. */
private class RemoteState {
    private val collections = ConcurrentHashMap<String, MutableMap<String, RemoteDocument>>()

    /** Смена семьи или её повторный выбор: снимки прежней семьи больше неактуальны. */
    fun reset() = collections.values.forEach { it.clear() }

    private fun collection(name: String): MutableMap<String, RemoteDocument> =
        collections.getOrPut(name) { ConcurrentHashMap() }

    /** Замена снимков коллекции содержимым нового полного события. */
    fun replace(name: String, documents: Map<String, RemoteDocument>) {
        collection(name).apply { clear(); putAll(documents) }
    }

    fun updatedAt(name: String, id: String): Long? = collection(name)[id]?.updatedAt

    fun fields(name: String, id: String): Map<String, Any?>? = collection(name)[id]?.fields
}

internal fun needsUpload(localUpdatedAt: Long, remoteUpdatedAt: Long?): Boolean = remoteUpdatedAt == null || localUpdatedAt > remoteUpdatedAt

internal fun <T> firestoreBatches(values: List<T>): List<List<T>> = values.chunked(500)

/** Содержимое документа без метки времени и автора правки: их различие не меняет данные. */
internal fun essenceOf(fields: Map<String, Any?>): Map<String, Any?> = fields - "updatedAt" - "updatedBy"

/**
 * Содержимое документов совпадает? Числа сравниваются как целые, а не по типу-обёртке:
 * снимок Firestore возвращает Long, локальные поля — тоже Long, но обёртки не должны
 * решать равенство.
 */
internal fun sameFields(local: Map<String, Any?>, remote: Map<String, Any?>): Boolean {
    if (local.keys != remote.keys) return false
    for ((key, value) in local) {
        val other = remote[key] ?: return false
        if (value is Number && other is Number) {
            if (value.toLong() != other.toLong()) return false
        } else if (value != other) return false
    }
    return true
}

/** Пауза переподключения слушателя: 5 с, 10 с, 20 с… до потолка. */
internal fun backoffMillis(failureStreak: Int): Long =
    (RETRY_DELAY_START_MILLIS shl failureStreak.coerceAtMost(RETRY_SHIFT_MAX)).coerceAtMost(RETRY_DELAY_MAX_MILLIS)

/**
 * Окно синхронизации по видимости приложения: на переднем плане — true; ушедшее
 * в фон приложение удерживает ещё [graceMillis], чтобы переключение «на минуту»
 * или шторка недавних не срывало слушатели и не гоняло attach впустую. Удержание
 * переармится на каждом новом событии фона: длинный сид в фоне всё равно снимет
 * слушатели, а возвращение переднего плана отменяет паузу мгновенно. Возврат
 * внутри окна не переиздаёт true: повторное значение не должно пересобирать
 * сессию синхронизации.
 */
internal fun graceDetach(foreground: Flow<Boolean>, graceMillis: Long): Flow<Boolean> =
    foreground.transformLatest { isForeground ->
        if (isForeground) {
            emit(true)
            awaitCancellation()
        } else {
            delay(graceMillis)
            emit(false)
            awaitCancellation()
        }
    }.distinctUntilChanged()

/** Порог ожидания подтверждения одного батча загрузки импорта. */
private const val IMPORT_UPLOAD_TIMEOUT_MILLIS = 20_000L

/** Стартовая пауза переподключения слушателя после сбоя. */
private const val RETRY_DELAY_START_MILLIS = 5_000L

/** Потолок роста степени двойки для паузы переподключения. */
private const val RETRY_SHIFT_MAX = 6

/** Потолок паузы переподключения, чтобы спящий слушатель не ждал вечность. */
private const val RETRY_DELAY_MAX_MILLIS = 300_000L

/** Пауза в фоне до снятия слушателей: быстро вернувшийся пользователь их не замечает. */
private const val SYNC_BACKGROUND_GRACE_MILLIS = 600_000L

private const val TAG = "ExpeysiSync"

private const val COLLECTION_BALANCES = "balances"

private const val COLLECTION_ACCOUNTS = "accounts"

private const val COLLECTION_CATEGORIES = "categories"

private const val COLLECTION_TRANSACTIONS = "transactions"
