package com.expeysi.app.data.remote

/**
 * Типы синхронизируемых сущностей. Имена коллекций удалённого хранилища
 * (balances, accounts, categories, transactions) знает только Firebase-
 * реализация транспорта — контракт от транспорта не зависит.
 */
enum class SyncEntityType { BALANCE, ACCOUNT, CATEGORY, TRANSACTION }

/** Адрес одной сущности в удалённом хранилище: тип плюс UUID записи. */
data class SyncKey(val type: SyncEntityType, val id: String)

/**
 * Изменение одной сущности: [fields] — ровно те поля, что сегодня пишутся
 * в wire-формат (см. docs/data-model.md), включая deleted и updatedAt,
 * без id (id живёт в [key]). Локальный флаг dirty в [fields] не входит —
 * он значит только «изменение ещё не передано транспорту».
 */
data class SyncChange(
    val key: SyncKey,
    val updatedAt: Long,
    val fields: Map<String, Any?>,
)

/**
 * Порция обновлений с удалённого хранилища. [isFullState] = true означает,
 * что [changes] — полное состояние данного типа (так ведёт себя snapshot-
 * listener Firestore); false — только дельта. Первичная сверка локальных
 * строк проводится только по транспортам с полным состоянием.
 */
data class RemoteUpdate(
    val type: SyncEntityType,
    val changes: List<SyncChange>,
    val isFullState: Boolean,
)

/** Итог отправки: ключи, которые транспорт принял к доставке. */
data class PushResult(val accepted: Set<SyncKey>)
