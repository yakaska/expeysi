package com.expeysi.app.data.sync

import android.util.Log
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEngine @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val familyStore: FamilyStore,
    private val balanceDao: BalanceDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            familyStore.familyId.collectLatest { familyId ->
                Log.d("ExpeysiSync", "family=$familyId user=${authRepository.userId}")
                if (familyId != null && authRepository.userId != null) {
                    coroutineScope {
                        val initialized = List(4) { CompletableDeferred<Unit>() }
                        val remote = RemoteVersions()
                        launch { listen(familyId, "balances", initialized[0], remote.balances, ::mergeBalance) }
                        launch { listen(familyId, "accounts", initialized[1], remote.accounts, ::mergeAccount) }
                        launch { listen(familyId, "categories", initialized[2], remote.categories, ::mergeCategory) }
                        launch { listen(familyId, "transactions", initialized[3], remote.transactions, ::mergeTransaction) }
                        initialized.awaitAll()
                        uploadMissingLocal(remote)
                    }
                }
            }
        }
    }

    private suspend fun uploadMissingLocal(remote: RemoteVersions) {
        val balances = balanceDao.observeAll().first { it.isNotEmpty() }
        val accounts = accountDao.observeAll().first { it.isNotEmpty() }
        val categories = categoryDao.observeAll().first { it.isNotEmpty() }
        val transactions = transactionDao.observeAll().first()
        var uploaded = 0
        balances.forEach {
            if (needsUpload(it.updatedAt, remote.balances[it.id])) {
                uploadBalance(it)
                uploaded++
            }
        }
        accounts.forEach {
            if (needsUpload(it.updatedAt, remote.accounts[it.id])) {
                uploadAccount(it)
                uploaded++
            }
        }
        categories.forEach {
            if (needsUpload(it.updatedAt, remote.categories[it.id])) {
                uploadCategory(it)
                uploaded++
            }
        }
        transactions.forEach {
            if (needsUpload(it.updatedAt, remote.transactions[it.id])) {
                uploadTransaction(it)
                uploaded++
            }
        }
        val total = balances.size + accounts.size + categories.size + transactions.size
        Log.d("ExpeysiSync", "startup upload uploaded=$uploaded skipped=${total - uploaded}")
    }

    fun uploadBalance(value: BalanceEntity) {
        collection("balances")?.document(value.id)?.set(
            mapOf(
                "name" to value.name,
                "currencyCode" to value.currencyCode,
                "iconKey" to value.iconKey,
                "colorKey" to value.colorKey,
                "deleted" to value.deleted,
                "updatedAt" to value.updatedAt,
            ),
        )
    }

    fun uploadAccount(value: AccountEntity) {
        collection("accounts")?.document(value.id)?.set(
            mapOf(
                "name" to value.name,
                "iconKey" to value.iconKey,
                "colorKey" to value.colorKey,
                "openingBalanceMinor" to value.openingBalanceMinor,
                "balanceId" to value.balanceId,
                "deleted" to value.deleted,
                "updatedAt" to value.updatedAt,
            ),
        )
    }

    fun uploadCategory(value: CategoryEntity) {
        collection("categories")?.document(value.id)?.set(
            mapOf(
                "name" to value.name,
                "iconKey" to value.iconKey,
                "colorKey" to value.colorKey,
                "type" to value.type,
                "deleted" to value.deleted,
                "updatedAt" to value.updatedAt,
            ),
        )
    }

    fun uploadTransaction(value: TransactionEntity) {
        collection("transactions")?.document(value.id)?.set(
            mapOf(
                "type" to value.type,
                "amountMinor" to value.amountMinor,
                "accountId" to value.accountId,
                "categoryId" to value.categoryId,
                "date" to value.date,
                "note" to value.note,
                "deleted" to value.deleted,
                "updatedAt" to value.updatedAt,
                "updatedBy" to (authRepository.userId ?: value.updatedBy),
            ),
        )
    }

    suspend fun uploadImportedData(
        balances: List<BalanceEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
    ) {
        val familyId = familyStore.familyId.value ?: return
        val family = firestore.collection("families").document(familyId)
        val writes = mutableListOf<Pair<DocumentReference, Map<String, Any>>>()
        balances.forEach { value ->
            writes += family.collection("balances").document(value.id) to mapOf(
                "name" to value.name,
                "currencyCode" to value.currencyCode,
                "iconKey" to value.iconKey,
                "colorKey" to value.colorKey,
                "deleted" to value.deleted,
                "updatedAt" to value.updatedAt,
            )
        }
        accounts.forEach { value ->
            writes += family.collection("accounts").document(value.id) to mapOf(
                "name" to value.name,
                "iconKey" to value.iconKey,
                "colorKey" to value.colorKey,
                "openingBalanceMinor" to value.openingBalanceMinor,
                "balanceId" to value.balanceId,
                "deleted" to value.deleted,
                "updatedAt" to value.updatedAt,
            )
        }
        categories.forEach { value ->
            writes += family.collection("categories").document(value.id) to mapOf(
                "name" to value.name,
                "iconKey" to value.iconKey,
                "colorKey" to value.colorKey,
                "type" to value.type,
                "deleted" to value.deleted,
                "updatedAt" to value.updatedAt,
            )
        }
        transactions.forEach { value ->
            writes += family.collection("transactions").document(value.id) to mapOf(
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
        }
        firestoreBatches(writes).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (document, data) -> batch.set(document, data) }
            batch.commit().await()
        }
        Log.d("ExpeysiSync", "import upload documents=${writes.size}")
    }

    private fun collection(name: String): CollectionReference? = familyStore.familyId.value?.let {
        firestore.collection("families").document(it).collection(name)
    }

    private suspend fun listen(
        familyId: String,
        name: String,
        initialized: CompletableDeferred<Unit>,
        remoteVersions: MutableMap<String, Long>,
        merge: suspend (DocumentSnapshot) -> Unit,
    ) {
        changes(familyId, name).collect { documents ->
            Log.d("ExpeysiSync", "snapshot $name=${documents.size}")
            remoteVersions.clear()
            documents.forEach { document ->
                remoteVersions[document.id] = document.getLong("updatedAt") ?: 0
                merge(document)
            }
            initialized.complete(Unit)
        }
    }

    private fun changes(familyId: String, name: String) = callbackFlow {
        val registration = firestore.collection("families").document(familyId).collection(name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ExpeysiSync", "listener $name", error)
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents)
                }
            }
        awaitClose { registration.remove() }
    }.retryWhen { _, _ ->
        delay(5_000)
        true
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

private data class RemoteVersions(
    val balances: MutableMap<String, Long> = mutableMapOf(),
    val accounts: MutableMap<String, Long> = mutableMapOf(),
    val categories: MutableMap<String, Long> = mutableMapOf(),
    val transactions: MutableMap<String, Long> = mutableMapOf(),
)

internal fun needsUpload(localUpdatedAt: Long, remoteUpdatedAt: Long?): Boolean = remoteUpdatedAt == null || localUpdatedAt > remoteUpdatedAt

internal fun <T> firestoreBatches(values: List<T>): List<List<T>> = values.chunked(500)
