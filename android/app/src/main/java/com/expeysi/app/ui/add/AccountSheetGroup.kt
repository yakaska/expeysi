package com.expeysi.app.ui.add

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity

/** Группа счетов одного баланса для шторки выбора счёта: заголовок баланса, под ним счета. */
data class AccountSheetGroup(
    val balanceId: String?,
    val balanceName: String,
    val iconKey: String,
    val colorKey: String,
    val accounts: List<AccountEntity>,
)

/**
 * Счета, сгруппированные по балансам. Порядок групп следует карте балансов,
 * порядок счетов внутри группы сохраняется. Счёт с неизвестным балансом
 * попадает в безымянную группу в конце (в UI рисуется без заголовка).
 */
fun accountSheetGroups(
    accounts: List<AccountEntity>,
    balances: Map<String, BalanceEntity>,
): List<AccountSheetGroup> {
    val rowsByBalance = accounts.groupBy { it.balanceId }
    val named = balances.entries
        .mapNotNull { (id, balance) ->
            rowsByBalance[id]?.let { rows ->
                AccountSheetGroup(id, balance.name, balance.iconKey, balance.colorKey, rows)
            }
        }
    val orphan = rowsByBalance
        .filterKeys { id -> id !in balances }
        .map { (id, rows) -> AccountSheetGroup(id, "", "", "default", rows) }
    return if (orphan.isEmpty()) named else named + orphan
}
