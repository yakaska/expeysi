package com.expeysi.app.data.sync

import android.util.Log
import com.expeysi.app.data.FamilyStore
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.remote.AuthProvider
import com.expeysi.app.data.remote.SyncChange
import com.expeysi.app.data.remote.SyncEntityType
import com.expeysi.app.data.remote.SyncMapper
import com.expeysi.app.data.remote.SyncRemote
import com.expeysi.app.di.SyncScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Синхронизация локального журнала Room с удалённым журналом семьи через
 * [SyncRemote]. Room — источник правды UI (инвариант 1): входящие изменения
 * сравниваются по updatedAt (last-write-wins), локально более свежие строки
 * помечаются dirty и уезжают при ближайшем проходе рабочего цикла. Отправка
 * dirty-строк идёт долгоживущим воркером через conflated-канал: подтверждения
 * не ждут (инвариант 5), сбой — пауза [RETRY_DELAY_MILLIS] и повтор, иные
 * ошибки рабочий цикл не убивают. Первичная сверка запускается один раз за
 * сессию семьи после полного состояния всех четырёх типов и появления сидов
 * в Room (инвариант 4) и только помечает строки dirty — отправка всегда
 * через рабочий цикл.
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
    @SyncScope private val scope: CoroutineScope,
) : SyncTrigger {

    /** Запросы прохода отправки; N подряд дают не более одного лишнего прохода. */
    private val triggers = Channel<Unit>(Channel.CONFLATED)
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
        scope.launch {
            worker()
        }
    }

    /**
     * Полный цикл для одной семьи: один поток обновлений транспорта со
     * слиянием и первичной сверкой. Сессия начинается с прохода отправки,
     * чтобы строки, созданные до выбора семьи, ушли.
     */
    internal suspend fun runFamilySync(familyId: String) {
        trigger()
        coroutineScope {
            val remoteVersions = RemoteVersions()
            val fullStateTypes = mutableSetOf<SyncEntityType>()
            var reconciling = false
            launch {
                remote.observe(familyId).collect { update ->
                    if (update.isFullState) {
                        remoteVersions.replace(
                            update.type,
                            update.changes.associate { it.key.id to it.updatedAt },
                        )
                    } else {
                        update.changes.forEach { change ->
                            remoteVersions.add(update.type, change.key.id, change.updatedAt)
                        }
                    }
                    update.changes.forEach { merge(it) }
                    if (update.isFullState) {
                        fullStateTypes += update.type
                    }
                    if (!reconciling && fullStateTypes.size == SyncEntityType.entries.size) {
                        reconciling = true
                        launch {
                            awaitSeeds()
                            reconcile(remoteVersions)
                            trigger()
                        }
                    }
                }
            }
        }
    }

    /**
     * Сиды справочников обязаны быть в Room, сверка до их появления
     * бессмысленна (инвариант 4).
     */
    private suspend fun awaitSeeds() {
        balanceDao.observeAll().first { it.isNotEmpty() }
        accountDao.observeAll().first { it.isNotEmpty() }
        categoryDao.observeAll().first { it.isNotEmpty() }
    }

    /**
     * Первичная сверка локальных строк с удалённым состоянием: строки,
     * отсутствующие в удалённом состоянии или новее его (needsUpload),
     * помечаются dirty. Прямых отправок сверка не делает — рабочий цикл
     * заберёт их при ближайшем проходе.
     */
    private suspend fun reconcile(remoteVersions: RemoteVersions) {
        balanceDao.observeAll().first().forEach { value ->
            if (needsUpload(value.updatedAt, remoteVersions[SyncEntityType.BALANCE, value.id])) {
                balanceDao.markDirty(value.id)
            }
        }
        accountDao.observeAll().first().forEach { value ->
            if (needsUpload(value.updatedAt, remoteVersions[SyncEntityType.ACCOUNT, value.id])) {
                accountDao.markDirty(value.id)
            }
        }
        categoryDao.observeAll().first().forEach { value ->
            if (needsUpload(value.updatedAt, remoteVersions[SyncEntityType.CATEGORY, value.id])) {
                categoryDao.markDirty(value.id)
            }
        }
        transactionDao.observeAll().first().forEach { value ->
            if (needsUpload(value.updatedAt, remoteVersions[SyncEntityType.TRANSACTION, value.id])) {
                transactionDao.markDirty(value.id)
            }
        }
    }

    /** Воркер рабочего цикла: один проход на полученный триггер, подряд слитые — вместе. */
    private suspend fun worker() {
        while (true) {
            // CONFLATED-канал: N подряд пришедших триггеров дают один проход.
            triggers.receive()
            try {
                syncDirty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Иная ошибка не убивает рабочий цикл: лог и продолжение.
                Log.e(TAG, "sync pass failed", e)
            }
        }
    }

    /**
     * Один проход отправки: dirty-строки всех четырёх типов одним
     * [SyncRemote.push]; флаг снимается условно по updatedAt отправленной
     * версии — правка строки во время отправки не потеряется, строка уйдёт
     * заново. Недобор accepted или ошибка push: лог, пауза [RETRY_DELAY_MILLIS]
     * и повтор. CancellationException пробрасывается. Без выбранной семьи
     * проход завершается, строки остаются dirty.
     */
    internal suspend fun syncDirty() {
        val familyId = familyStore.familyId.value ?: return
        val balances = balanceDao.listDirty().map { SyncMapper.toChange(it) }
        val accounts = accountDao.listDirty().map { SyncMapper.toChange(it) }
        val categories = categoryDao.listDirty().map { SyncMapper.toChange(it) }
        val transactions = transactionDao.listDirty().map { SyncMapper.toChange(it, authRepository.userId) }
        val changes = balances + accounts + categories + transactions
        if (changes.isEmpty()) return
        try {
            val accepted = remote.push(familyId, changes).accepted
            balances.forEach { if (it.key in accepted) balanceDao.markClean(it.key.id, it.updatedAt) }
            accounts.forEach { if (it.key in accepted) accountDao.markClean(it.key.id, it.updatedAt) }
            categories.forEach { if (it.key in accepted) categoryDao.markClean(it.key.id, it.updatedAt) }
            transactions.forEach { if (it.key in accepted) transactionDao.markClean(it.key.id, it.updatedAt) }
            if (accepted.size >= changes.size) return
            Log.w(TAG, "push partial accepted=${accepted.size} sent=${changes.size}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "push failed; retry in $RETRY_DELAY_MILLIS ms", e)
        }
        delay(RETRY_DELAY_MILLIS)
        triggers.trySend(Unit)
    }

    /**
     * Слияние одного удалённого изменения по updatedAt (инвариант 1):
     * удалённое новее или локальной нет — upsert удалённой версии с
     * dirty = false; удалённое старее — локальная строка помечается dirty и
     * рабочий цикл отправит её; равные версии — без действий. Невалидные
     * документы пропускаются.
     */
    private suspend fun merge(change: SyncChange) {
        when (change.key.type) {
            SyncEntityType.BALANCE -> mergeBalance(change)
            SyncEntityType.ACCOUNT -> mergeAccount(change)
            SyncEntityType.CATEGORY -> mergeCategory(change)
            SyncEntityType.TRANSACTION -> mergeTransaction(change)
        }
    }

    private suspend fun mergeBalance(change: SyncChange) {
        val value = SyncMapper.balance(change) ?: return
        val local = balanceDao.byId(value.id)
        when {
            local == null || value.updatedAt > local.updatedAt -> balanceDao.upsert(value)
            value.updatedAt < local.updatedAt -> {
                balanceDao.markDirty(value.id)
                trigger()
            }
        }
    }

    private suspend fun mergeAccount(change: SyncChange) {
        val value = SyncMapper.account(change) ?: return
        val local = accountDao.byId(value.id)
        when {
            local == null || value.updatedAt > local.updatedAt -> accountDao.upsert(value)
            value.updatedAt < local.updatedAt -> {
                accountDao.markDirty(value.id)
                trigger()
            }
        }
    }

    private suspend fun mergeCategory(change: SyncChange) {
        val value = SyncMapper.category(change) ?: return
        val local = categoryDao.byId(value.id)
        when {
            local == null || value.updatedAt > local.updatedAt -> categoryDao.upsert(value)
            value.updatedAt < local.updatedAt -> {
                categoryDao.markDirty(value.id)
                trigger()
            }
        }
    }

    private suspend fun mergeTransaction(change: SyncChange) {
        val value = SyncMapper.transaction(change) ?: return
        val local = transactionDao.byId(value.id)
        when {
            local == null || value.updatedAt > local.updatedAt -> transactionDao.upsert(value)
            value.updatedAt < local.updatedAt -> {
                transactionDao.markDirty(value.id)
                trigger()
            }
        }
    }

    /**
     * Ставит запрос прохода отправки и возвращается, не дожидаясь работы
     * (инвариант 5): воркер живёт в скоупе движка, сеть никогда не
     * вызывается из контекста вызывающего.
     */
    override fun trigger() {
        triggers.trySend(Unit)
    }

    private companion object {
        const val TAG = "ExpeysiSync"

        /** Пауза перед повтором прохода отправки после сбоя. */
        const val RETRY_DELAY_MILLIS = 5_000L
    }
}

/**
 * Версии строк удалённого журнала по типам. Полное состояние типа
 * (RemoteUpdate.isFullState = true) заменяет карту типа целиком; дельта —
 * дополняет. Так строка, исчезнувшая из нового снимка, перестаёт считаться
 * присутствующей в удалённом состоянии.
 */
private class RemoteVersions {
    private val byType = SyncEntityType.entries.associateWith { mutableMapOf<String, Long>() }

    operator fun get(type: SyncEntityType, id: String): Long? = byType.getValue(type)[id]

    /** Замена карты типа полным состоянием снимка. */
    fun replace(type: SyncEntityType, snapshot: Map<String, Long>) {
        byType.getValue(type).apply {
            clear()
            putAll(snapshot)
        }
    }

    /** Дополнение карты типа дельтой. */
    fun add(type: SyncEntityType, id: String, updatedAt: Long) {
        byType.getValue(type)[id] = updatedAt
    }
}

internal fun needsUpload(localUpdatedAt: Long, remoteUpdatedAt: Long?): Boolean = remoteUpdatedAt == null || localUpdatedAt > remoteUpdatedAt
