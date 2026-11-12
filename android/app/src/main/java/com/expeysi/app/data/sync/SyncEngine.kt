package com.expeysi.app.data.sync

import android.util.Log
import com.expeysi.app.data.FamilyStore
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.data.remote.AuthProvider
import com.expeysi.app.data.remote.SyncChange
import com.expeysi.app.data.remote.SyncEntityType
import com.expeysi.app.data.remote.SyncMapper
import com.expeysi.app.data.remote.SyncRemote
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Синхронизация локального журнала Room с удалённым журналом семьи через
 * [SyncRemote]. Room — источник правды UI (инвариант 1): входящие изменения
 * сравниваются по updatedAt (last-write-wins), локально более свежие строки
 * отправляются обратно. Записи в удалённое хранилище подтверждения не ждут
 * (инвариант 5), кроме массовой загрузки импорта (батчи по 500, таймаут
 * первых подтверждений). Сверка при выборе семьи ждёт сиды справочников
 * в Room (инвариант 4).
 */
@Singleton
class SyncEngine @Inject constructor(
    private val remote: SyncRemote,
    private val authRepository: AuthProvider,
    private val familyStore: FamilyStore,
    private val balanceDao: BalanceDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
) : SyncTrigger,
    ImportStore {
    /** Собственный scope движка: сеть и перенос dirty-строк никогда не идут в контексте вызывающего. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            familyStore.familyId.collectLatest { familyId ->
                Log.d(TAG, "family=$familyId user=${authRepository.userId}")
                if (familyId != null && authRepository.userId != null) {
                    runFamilySync(familyId)
                }
            }
        }
    }

    /**
     * Полный цикл для одной семьи: слушатели изменений четырёх типов плюс
     * первичная сверка, стартующая после первого снимка каждого типа.
     */
    internal suspend fun runFamilySync(familyId: String) {
        coroutineScope {
            val initialized = List(4) { CompletableDeferred<Unit>() }
            val remoteVersions = RemoteVersions()
            SyncEntityType.entries.forEachIndexed { index, type ->
                launch {
                    remote.observeChanges(familyId, type).collect { update ->
                        update.changes.forEach { change ->
                            remoteVersions[type, change.key.id] = change.updatedAt
                            merge(familyId, change)
                        }
                        initialized[index].complete(Unit)
                    }
                }
            }
            initialized.awaitAll()
            uploadMissingLocal(familyId, remoteVersions)
        }
    }

    private suspend fun uploadMissingLocal(familyId: String, remoteVersions: RemoteVersions) {
        // Сиды справочников обязаны быть в Room, сверка до их появления бессмысленна.
        val balances = balanceDao.observeAll().first { it.isNotEmpty() }
        val accounts = accountDao.observeAll().first { it.isNotEmpty() }
        val categories = categoryDao.observeAll().first { it.isNotEmpty() }
        val transactions = transactionDao.observeAll().first()
        var uploaded = 0
        balances.forEach {
            if (needsUpload(it.updatedAt, remoteVersions[SyncEntityType.BALANCE, it.id])) {
                remote.postChange(familyId, SyncMapper.toChange(it))
                uploaded++
            }
        }
        accounts.forEach {
            if (needsUpload(it.updatedAt, remoteVersions[SyncEntityType.ACCOUNT, it.id])) {
                remote.postChange(familyId, SyncMapper.toChange(it))
                uploaded++
            }
        }
        categories.forEach {
            if (needsUpload(it.updatedAt, remoteVersions[SyncEntityType.CATEGORY, it.id])) {
                remote.postChange(familyId, SyncMapper.toChange(it))
                uploaded++
            }
        }
        transactions.forEach {
            if (needsUpload(it.updatedAt, remoteVersions[SyncEntityType.TRANSACTION, it.id])) {
                remote.postChange(familyId, SyncMapper.toChange(it, authRepository.userId))
                uploaded++
            }
        }
        val total = balances.size + accounts.size + categories.size + transactions.size
        Log.d(TAG, "startup upload uploaded=$uploaded skipped=${total - uploaded}")
    }

    private suspend fun merge(familyId: String, change: SyncChange) {
        when (change.key.type) {
            SyncEntityType.BALANCE -> mergeBalance(familyId, change)
            SyncEntityType.ACCOUNT -> mergeAccount(familyId, change)
            SyncEntityType.CATEGORY -> mergeCategory(familyId, change)
            SyncEntityType.TRANSACTION -> mergeTransaction(familyId, change)
        }
    }

    private suspend fun mergeBalance(familyId: String, change: SyncChange) {
        val value = SyncMapper.balance(change) ?: return
        val local = balanceDao.byId(value.id)
        when {
            local == null || value.updatedAt > local.updatedAt -> balanceDao.upsert(value)
            value.updatedAt < local.updatedAt -> remote.postChange(familyId, SyncMapper.toChange(local))
        }
    }

    private suspend fun mergeAccount(familyId: String, change: SyncChange) {
        val value = SyncMapper.account(change) ?: return
        val local = accountDao.byId(value.id)
        when {
            local == null || value.updatedAt > local.updatedAt -> accountDao.upsert(value)
            value.updatedAt < local.updatedAt -> remote.postChange(familyId, SyncMapper.toChange(local))
        }
    }

    private suspend fun mergeCategory(familyId: String, change: SyncChange) {
        val value = SyncMapper.category(change) ?: return
        val local = categoryDao.byId(value.id)
        when {
            local == null || value.updatedAt > local.updatedAt -> categoryDao.upsert(value)
            value.updatedAt < local.updatedAt -> remote.postChange(familyId, SyncMapper.toChange(local))
        }
    }

    private suspend fun mergeTransaction(familyId: String, change: SyncChange) {
        val value = SyncMapper.transaction(change) ?: return
        val local = transactionDao.byId(value.id)
        when {
            local == null || value.updatedAt > local.updatedAt -> transactionDao.upsert(value)
            value.updatedAt < local.updatedAt -> remote.postChange(familyId, SyncMapper.toChange(local, authRepository.userId))
        }
    }

    /**
     * Запускает перенос dirty-строк и возвращается, не дожидаясь отправки
     * (инвариант 5): работа идёт в собственном scope движка — сеть никогда
     * не вызывается из контекста вызывающего.
     */
    override fun trigger() {
        scope.launch { requestSync() }
    }

    /**
     * Переносит dirty-строки Room в удалённый журнал. Подтверждения не ждёт:
     * доставка транспорта асинхронна. Флаг снимается условно по updatedAt —
     * правка строки во время переноса не потеряется, строка уйдёт заново при
     * следующем requestSync.
     */
    suspend fun requestSync() {
        val familyId = familyStore.familyId.value ?: return
        balanceDao.listDirty().forEach { value ->
            remote.postChange(familyId, SyncMapper.toChange(value))
            balanceDao.markClean(value.id, value.updatedAt)
        }
        accountDao.listDirty().forEach { value ->
            remote.postChange(familyId, SyncMapper.toChange(value))
            accountDao.markClean(value.id, value.updatedAt)
        }
        categoryDao.listDirty().forEach { value ->
            remote.postChange(familyId, SyncMapper.toChange(value))
            categoryDao.markClean(value.id, value.updatedAt)
        }
        transactionDao.listDirty().forEach { value ->
            remote.postChange(familyId, SyncMapper.toChange(value, authRepository.userId))
            transactionDao.markClean(value.id, value.updatedAt)
        }
    }

    /**
     * Массовая загрузка импортированных данных: батчи по 500 в транспорте,
     * подтверждения первых батчей ждут с таймаутом (инвариант 5).
     */
    override suspend fun uploadImportedData(
        balances: List<BalanceEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
    ) {
        val familyId = familyStore.familyId.value ?: return
        val changes = balanceChanges(balances) +
            accountChanges(accounts) +
            categoryChanges(categories) +
            transactionChanges(transactions)
        remote.uploadBulk(familyId, changes, IMPORT_UPLOAD_TIMEOUT_MILLIS)
    }

    private fun balanceChanges(values: List<BalanceEntity>): List<SyncChange> = values.map { SyncMapper.toChange(it) }

    private fun accountChanges(values: List<AccountEntity>): List<SyncChange> = values.map { SyncMapper.toChange(it) }

    private fun categoryChanges(values: List<CategoryEntity>): List<SyncChange> = values.map { SyncMapper.toChange(it) }

    private fun transactionChanges(values: List<TransactionEntity>): List<SyncChange> = values.map { SyncMapper.toChange(it, authRepository.userId) }

    private companion object {
        const val TAG = "ExpeysiSync"
    }
}

/** Версии строк удалённого журнала по типам: сравнение при первичной сверке. */
private class RemoteVersions {
    private val byType = SyncEntityType.entries.associateWith { mutableMapOf<String, Long>() }

    operator fun get(type: SyncEntityType): MutableMap<String, Long> = byType.getValue(type)

    operator fun get(type: SyncEntityType, id: String): Long? = get(type)[id]

    operator fun set(type: SyncEntityType, id: String, updatedAt: Long) {
        get(type)[id] = updatedAt
    }
}

internal fun needsUpload(localUpdatedAt: Long, remoteUpdatedAt: Long?): Boolean = remoteUpdatedAt == null || localUpdatedAt > remoteUpdatedAt

/** Порог ожидания подтверждения одного батча загрузки импорта. */
private const val IMPORT_UPLOAD_TIMEOUT_MILLIS = 20_000L
