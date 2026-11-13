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
import com.expeysi.app.data.remote.SyncKey
import com.expeysi.app.data.remote.SyncMapper
import com.expeysi.app.data.remote.SyncRemote
import com.expeysi.app.di.SyncScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Синхронизация локального журнала Room с удалённым журналом семьи через
 * [SyncRemote]. Room — источник правды UI (инвариант 1): входящие изменения
 * сравниваются по updatedAt (last-write-wins), локально более свежие строки
 * помечаются dirty и уезжают при ближайшем проходе рабочего цикла; ключи,
 * отклонённые сервером после приёма к доставке, ставятся на удержание своей
 * версии с per-key паузой 5 с * 2^(n-1) до 5 мин ([RejectionTracker]):
 * выпуск после паузы помечает строку dirty и запрашивает проход. Отправка
 * dirty-строк идёт долгоживущим воркером
 * через conflated-канал: подтверждения не ждут (инвариант 5), неполный
 * проход или исключение — backoff от [RETRY_DELAY_MIN_MILLIS] до
 * [RETRY_DELAY_MAX_MILLIS] с повтором, полный успех сбрасывает паузу; флаги
 * снимаются только после accepted, ошибки Room не теряют dirty. Без выбранной
 * семьи или входа (инвариант 7) ничего не отправляется и не падает: строки
 * уходят после выбора семьи или входа (isSignedIn ставит запрос прохода и
 * поднимает сессию). Первичная сверка запускается один раз за сессию семьи
 * после полного состояния всех четырёх типов и появления сидов в Room
 * (инвариант 4) и только помечает строки dirty — отправка всегда через
 * рабочий цикл.
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

    /** Учёт отказов сервера: per-key удержания и паузы (см. [RejectionTracker]). */
    private val heldRejections = RejectionTracker(
        scope = scope,
        onRelease = { key, version -> releaseRejectedRow(key, version) },
    )

    fun start() {
        if (started) return
        started = true
        scope.launch {
            // Сессия семьи зависит и от входа: приложение, запущенное до
            // входа (familyId сохранён, userId ещё нет), поднимает сессию,
            // когда isSignedIn становится true.
            combine(familyStore.familyId, authRepository.isSignedIn) { familyId, signedIn ->
                if (familyId != null && signedIn && authRepository.userId != null) familyId else null
            }.collectLatest { familyId ->
                if (familyId != null) {
                    Log.d(TAG, "family=$familyId user=${authRepository.userId}")
                    runFamilySync(familyId)
                }
            }
        }
        scope.launch {
            // Строки, накопленные до входа, отправляются после него.
            // Начальное значение true тоже ставит запрос прохода.
            authRepository.isSignedIn.collect { signedIn ->
                if (signedIn) trigger()
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
        // Сессия семьи началась: счётчики попыток и удержания предыдущей
        // сессии сброшены, job выпуска отменены.
        heldRejections.reset()
        trigger()
        coroutineScope {
            val remoteVersions = RemoteVersions()
            val fullStateTypes = mutableSetOf<SyncEntityType>()
            var reconciling = false
            launch {
                // Отклонение сервером ранее принятой записи: только запись
                // удержания — без markDirty и без немедленного триггера,
                // выпуск после per-key паузы сделает и то и другое.
                remote.rejections.collect { key -> heldRejections.recordRejection(key) }
            }
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

    /**
     * Воркер рабочего цикла: один проход на полученный триггер, подряд
     * слитые — вместе. Проход без полного успеха или любое исключение (кроме
     * отмены) — пауза backoff и повтор: 5 с, затем удвоение до потолка 5 мин;
     * полный успех сбрасывает на 5 с. Триггеры, пришедшие во время паузы,
     * сливаются в канале и не обходят её: после паузы — один проход.
     * Исключения воркера не убивают (лог с сбойной операцией), dirty
     * не теряются: флаг снимается только после accepted.
     */
    private suspend fun worker() {
        var backoff = RETRY_DELAY_MIN_MILLIS
        while (true) {
            // CONFLATED-канал: N подряд пришедших триггеров дают один проход.
            triggers.receive()
            var fullSuccess = false
            try {
                fullSuccess = syncDirty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Иная ошибка не убивает рабочий цикл: лог и продолжение.
                Log.e(TAG, "sync pass failed", e)
            }
            if (fullSuccess) {
                backoff = RETRY_DELAY_MIN_MILLIS
            } else {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(RETRY_DELAY_MAX_MILLIS)
                triggers.trySend(Unit)
            }
        }
    }

    /**
     * Один проход отправки: dirty-строки всех четырёх типов одним
     * [SyncRemote.push]; флаг снимается условно по updatedAt отправленной
     * версии — правка строки во время отправки не потеряется, строка уйдёт
     * заново. Строки на удержании отклонённой версии не отправляются и не
     * делают проход неполным; markClean пропускается и для строки, по которой
     * отказ уже записан на отправленную версию (отказ между отправкой и
     * чисткой не теряется: строка останется dirty до выпуска). Возвращает
     * признак полного успеха: все отправленные строки приняты (или отправлять
     * нечего, включая случай «остались только удержанные»). Повтор недобора и
     * паузы — ответственность [worker]. Без выбранной семьи или пользователя
     * (инвариант 7) ничего не отправляется и не падает, строки остаются
     * dirty; такой проход считается завершённым — повтор не нужен, строки
     * уйдут после выбора семьи или входа. Ошибки Room и push не глушатся:
     * пробрасываются воркеру с именем сбойной операции.
     */
    internal suspend fun syncDirty(): Boolean {
        val familyId = familyStore.familyId.value ?: return true
        val userId = authRepository.userId ?: return true
        val balances = stage("listDirty BALANCE") { balanceDao.listDirty() }.map { SyncMapper.toChange(it) }
        val accounts = stage("listDirty ACCOUNT") { accountDao.listDirty() }.map { SyncMapper.toChange(it) }
        val categories = stage("listDirty CATEGORY") { categoryDao.listDirty() }.map { SyncMapper.toChange(it) }
        val transactions = stage("listDirty TRANSACTION") { transactionDao.listDirty() }.map { SyncMapper.toChange(it, userId) }
        val changes = balances + accounts + categories + transactions
        if (changes.isEmpty()) return true
        // Строки на удержании отклонённой версии не отправляются и не считаются
        // неполным проходом: здоровые строки и строки с более свежей версией
        // уходят сразу.
        val toSend = changes.filterNot { heldRejections.isHeldAt(it.key, it.updatedAt) }
        if (toSend.isEmpty()) return true
        toSend.forEach { heldRejections.notePushed(it.key, it.updatedAt) }
        val accepted = stage("push") { remote.push(familyId, toSend).accepted }
        balances.forEach { if (it.key in accepted && !heldRejections.isHeldAt(it.key, it.updatedAt)) stage("markClean BALANCE") { balanceDao.markClean(it.key.id, it.updatedAt) } }
        accounts.forEach { if (it.key in accepted && !heldRejections.isHeldAt(it.key, it.updatedAt)) stage("markClean ACCOUNT") { accountDao.markClean(it.key.id, it.updatedAt) } }
        categories.forEach { if (it.key in accepted && !heldRejections.isHeldAt(it.key, it.updatedAt)) stage("markClean CATEGORY") { categoryDao.markClean(it.key.id, it.updatedAt) } }
        transactions.forEach { if (it.key in accepted && !heldRejections.isHeldAt(it.key, it.updatedAt)) stage("markClean TRANSACTION") { transactionDao.markClean(it.key.id, it.updatedAt) } }
        return accepted.size >= toSend.size
    }

    /** Отклонённая сервером строка остаётся в журнале dirty до успешного прохода. */
    private suspend fun markRejectedDirty(key: SyncKey) {
        when (key.type) {
            SyncEntityType.BALANCE -> balanceDao.markDirty(key.id)
            SyncEntityType.ACCOUNT -> accountDao.markDirty(key.id)
            SyncEntityType.CATEGORY -> categoryDao.markDirty(key.id)
            SyncEntityType.TRANSACTION -> transactionDao.markDirty(key.id)
        }
    }

    /**
     * Выпуск удержанной строки после per-key паузы: повторная пометка dirty,
     * только если строка всё ещё на отклонённой версии — более свежая правка
     * уходит сама. Проход запрашивается только при фактической пометке, чтобы
     * посторонние триггеры не порождали пустые проходы.
     */
    private suspend fun releaseRejectedRow(key: SyncKey, version: Long) {
        val stillAt = when (key.type) {
            SyncEntityType.BALANCE -> balanceDao.byId(key.id)?.updatedAt == version
            SyncEntityType.ACCOUNT -> accountDao.byId(key.id)?.updatedAt == version
            SyncEntityType.CATEGORY -> categoryDao.byId(key.id)?.updatedAt == version
            SyncEntityType.TRANSACTION -> transactionDao.byId(key.id)?.updatedAt == version
        }
        if (stillAt) {
            Log.d(TAG, "rejection retry for $key version=$version")
            markRejectedDirty(key)
            trigger()
        }
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

        /** Первая пауза backoff после неполного прохода или ошибки. */
        const val RETRY_DELAY_MIN_MILLIS = 5_000L

        /** Потолок backoff. */
        const val RETRY_DELAY_MAX_MILLIS = 5 * 60 * 1000L
    }
}

/**
 * Ошибки Room и транспорта внутри прохода не теряют контекст: воркер логирует
 * имя сбойной операции. Отмена корутины пробрасывается как есть.
 */
private suspend fun <T> stage(name: String, block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw IllegalStateException("sync stage: $name", e)
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
