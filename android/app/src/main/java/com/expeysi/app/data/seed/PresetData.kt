package com.expeysi.app.data.seed

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.domain.TxType

/**
 * Предзаписанные счета и категории. UUID детерминированные и одинаковые
 * на всех устройствах — после синхронизации дубликатов не будет.
 * Менять существующие id нельзя, можно только добавлять новые.
 */
object PresetData {

    const val ACCOUNT_CASH_ID = "00000000-0000-4000-8000-000000000001"
    const val ACCOUNT_CARD_ID = "00000000-0000-4000-8000-000000000002"

    data class PresetAccount(val id: String, val name: String, val iconKey: String)

    data class PresetCategory(
        val id: String,
        val name: String,
        val iconKey: String,
        val type: TxType,
    )

    val accounts: List<PresetAccount> = listOf(
        PresetAccount(ACCOUNT_CASH_ID, "Наличные", "payments"),
        PresetAccount(ACCOUNT_CARD_ID, "Карта", "credit_card"),
    )

    val categories: List<PresetCategory> = listOf(
        PresetCategory("00000000-0000-4000-8000-000000000101", "Продукты", "shopping_cart", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000102", "Транспорт", "directions_bus", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000103", "Жильё", "home", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000104", "Кафе", "local_cafe", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000105", "Здоровье", "medical_services", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000106", "Развлечения", "sports_esports", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000107", "Прочее", "more_horiz", TxType.EXPENSE),
        PresetCategory("00000000-0000-4000-8000-000000000201", "Зарплата", "work", TxType.INCOME),
        PresetCategory("00000000-0000-4000-8000-000000000202", "Подработка", "attach_money", TxType.INCOME),
        PresetCategory("00000000-0000-4000-8000-000000000203", "Прочее", "more_horiz", TxType.INCOME),
    )

    fun accountEntities(nowMillis: Long): List<AccountEntity> = accounts.map {
        AccountEntity(
            id = it.id,
            name = it.name,
            iconKey = it.iconKey,
            openingBalanceMinor = 0L,
            updatedAt = nowMillis,
        )
    }

    fun categoryEntities(nowMillis: Long): List<CategoryEntity> = categories.map {
        CategoryEntity(
            id = it.id,
            name = it.name,
            iconKey = it.iconKey,
            type = it.type.wire,
            updatedAt = nowMillis,
        )
    }
}
