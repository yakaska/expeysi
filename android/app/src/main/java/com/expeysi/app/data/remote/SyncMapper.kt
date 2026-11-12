package com.expeysi.app.data.remote

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.Currency

/**
 * Преобразование сущностей Room в изменения синхронизации и обратно —
 * одинаковое для любого транспорта. Набор полей и дефолты повторяют текущий
 * wire-формат Firestore (docs/data-model.md) дословно: currencyCode счёта
 * в синк не попадает — валюта счёта всегда берётся из его баланса.
 *
 * Числа читаются через (v as? Number)?.toLong(), поэтому Int, Long и целое
 * Double обрабатываются одинаково. null при разборе означает невалидный
 * документ (обязательное поле отсутствует) — синхронизация такие записи
 * пропускает; значения по умолчанию при отсутствии необязательных полей:
 * currencyCode "RUB", colorKey "default", iconKey "", deleted false,
 * updatedAt 0, note "", updatedBy "".
 */
object SyncMapper {

    fun toChange(value: BalanceEntity): SyncChange = SyncChange(
        key = SyncKey(SyncEntityType.BALANCE, value.id),
        updatedAt = value.updatedAt,
        fields = mapOf(
            "name" to value.name,
            "currencyCode" to value.currencyCode,
            "iconKey" to value.iconKey,
            "colorKey" to value.colorKey,
            "deleted" to value.deleted,
            "updatedAt" to value.updatedAt,
        ),
    )

    fun toChange(value: AccountEntity): SyncChange = SyncChange(
        key = SyncKey(SyncEntityType.ACCOUNT, value.id),
        updatedAt = value.updatedAt,
        fields = mapOf(
            "name" to value.name,
            "iconKey" to value.iconKey,
            "colorKey" to value.colorKey,
            "openingBalanceMinor" to value.openingBalanceMinor,
            "balanceId" to value.balanceId,
            "deleted" to value.deleted,
            "updatedAt" to value.updatedAt,
        ),
    )

    fun toChange(value: CategoryEntity): SyncChange = SyncChange(
        key = SyncKey(SyncEntityType.CATEGORY, value.id),
        updatedAt = value.updatedAt,
        fields = mapOf(
            "name" to value.name,
            "iconKey" to value.iconKey,
            "colorKey" to value.colorKey,
            "type" to value.type,
            "deleted" to value.deleted,
            "updatedAt" to value.updatedAt,
        ),
    )

    /** [currentUserId] становится updatedBy отправляемой транзакции. */
    fun toChange(value: TransactionEntity, currentUserId: String?): SyncChange = SyncChange(
        key = SyncKey(SyncEntityType.TRANSACTION, value.id),
        updatedAt = value.updatedAt,
        fields = mapOf(
            "type" to value.type,
            "amountMinor" to value.amountMinor,
            "accountId" to value.accountId,
            "categoryId" to value.categoryId,
            "date" to value.date,
            "note" to value.note,
            "deleted" to value.deleted,
            "updatedAt" to value.updatedAt,
            "updatedBy" to (currentUserId ?: value.updatedBy),
        ),
    )

    fun balance(change: SyncChange): BalanceEntity? {
        val name = string(change.fields, "name") ?: return null
        return BalanceEntity(
            id = change.key.id,
            name = name,
            currencyCode = string(change.fields, "currencyCode") ?: Currency.DEFAULT,
            iconKey = string(change.fields, "iconKey") ?: "",
            colorKey = string(change.fields, "colorKey") ?: DEFAULT_COLOR_KEY,
            deleted = boolean(change.fields, "deleted"),
            updatedAt = number(change.fields, "updatedAt") ?: 0,
        )
    }

    fun account(change: SyncChange): AccountEntity? {
        val name = string(change.fields, "name") ?: return null
        val balanceId = string(change.fields, "balanceId") ?: return null
        return AccountEntity(
            id = change.key.id,
            name = name,
            iconKey = string(change.fields, "iconKey") ?: "",
            colorKey = string(change.fields, "colorKey") ?: DEFAULT_COLOR_KEY,
            openingBalanceMinor = number(change.fields, "openingBalanceMinor") ?: 0,
            balanceId = balanceId,
            deleted = boolean(change.fields, "deleted"),
            updatedAt = number(change.fields, "updatedAt") ?: 0,
        )
    }

    fun category(change: SyncChange): CategoryEntity? {
        val name = string(change.fields, "name") ?: return null
        val type = string(change.fields, "type") ?: return null
        return CategoryEntity(
            id = change.key.id,
            name = name,
            iconKey = string(change.fields, "iconKey") ?: "",
            colorKey = string(change.fields, "colorKey") ?: DEFAULT_COLOR_KEY,
            type = type,
            deleted = boolean(change.fields, "deleted"),
            updatedAt = number(change.fields, "updatedAt") ?: 0,
        )
    }

    fun transaction(change: SyncChange): TransactionEntity? {
        val type = string(change.fields, "type") ?: return null
        val amountMinor = number(change.fields, "amountMinor") ?: return null
        val accountId = string(change.fields, "accountId") ?: return null
        val categoryId = string(change.fields, "categoryId") ?: return null
        val date = number(change.fields, "date") ?: return null
        return TransactionEntity(
            id = change.key.id,
            type = type,
            amountMinor = amountMinor,
            accountId = accountId,
            categoryId = categoryId,
            date = date,
            note = string(change.fields, "note") ?: "",
            deleted = boolean(change.fields, "deleted"),
            updatedAt = number(change.fields, "updatedAt") ?: 0,
            updatedBy = string(change.fields, "updatedBy") ?: "",
        )
    }

    private fun string(fields: Map<String, Any?>, name: String): String? = fields[name] as? String

    private fun number(fields: Map<String, Any?>, name: String): Long? = (fields[name] as? Number)?.toLong()

    private fun boolean(fields: Map<String, Any?>, name: String): Boolean = fields[name] as? Boolean ?: false

    private const val DEFAULT_COLOR_KEY = "default"
}
