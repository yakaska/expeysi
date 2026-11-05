package com.expeysi.app.data.seed

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.domain.Currency
import com.expeysi.app.domain.TxType

/**
 * Предзаписанные счета и категории. UUID детерминированные и одинаковые
 * на всех устройствах — после синхронизации дубликатов не будет.
 * Менять существующие id нельзя, можно только добавлять новые.
 */
object PresetData {

    /** nameUUIDFromBytes("expeysi-balance-RUB") — совпадает с миграцией 3→4. */
    const val BALANCE_MAIN_ID = "532e3a4a-ff74-3c81-a308-f3ebe3ff8982"

    const val ACCOUNT_CASH_ID = "00000000-0000-4000-8000-000000000001"
    const val ACCOUNT_CARD_ID = "00000000-0000-4000-8000-000000000002"

    data class PresetBalance(val id: String, val name: String, val currencyCode: String)

    data class PresetAccount(val id: String, val name: String, val iconKey: String)

    data class PresetCategory(
        val id: String,
        val name: String,
        val iconKey: String,
        val colorKey: String,
        val type: TxType,
    )

    val balances: List<PresetBalance> = listOf(
        PresetBalance(BALANCE_MAIN_ID, "Основной", Currency.DEFAULT),
    )

    val accounts: List<PresetAccount> = listOf(
        PresetAccount(ACCOUNT_CASH_ID, "Наличные", "payments"),
        PresetAccount(ACCOUNT_CARD_ID, "Карта", "credit_card"),
    )

    val categories: List<PresetCategory> = listOf(
        PresetCategory("00000000-0000-4000-8000-000000000101", "Еда", "restaurant", "orange", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000102", "Транспорт", "directions_bus", "blue", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000103", "Жильё", "home", "brown", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000108", "Одежда", "checkroom", "purple", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000109", "Связь", "smartphone", "teal", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000105", "Здоровье", "medical_services", "red", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000110", "Подарки", "card_giftcard", "pink", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000106", "Развлечения", "sports_esports", "deep_purple", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000111", "Косметика", "face", "pink_light", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000201", "Зарплата", "work", "green", TxType.INCOME),
        PresetCategory("00000000-0000-4000-8000-000000000204", "Премия", "star", "amber", TxType.INCOME),
        PresetCategory("00000000-0000-4000-8000-000000000205", "Подарки", "card_giftcard", "pink", TxType.INCOME),
    )

    fun balanceEntities(nowMillis: Long) = balances.map {
        BalanceEntity(
            id = it.id,
            name = it.name,
            currencyCode = it.currencyCode,
            updatedAt = nowMillis,
        )
    }

    fun accountEntities(nowMillis: Long): List<AccountEntity> = accounts.map {
        AccountEntity(
            id = it.id,
            name = it.name,
            iconKey = it.iconKey,
            openingBalanceMinor = 0L,
            currencyCode = Currency.DEFAULT,
            balanceId = BALANCE_MAIN_ID,
            updatedAt = nowMillis,
        )
    }

    fun categoryEntities(nowMillis: Long): List<CategoryEntity> = categories.map {
        CategoryEntity(
            id = it.id,
            name = it.name,
            iconKey = it.iconKey,
            colorKey = it.colorKey,
            type = it.type.wire,
            updatedAt = nowMillis,
        )
    }
}
