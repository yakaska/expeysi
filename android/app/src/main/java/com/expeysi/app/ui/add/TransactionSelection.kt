package com.expeysi.app.ui.add

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.domain.TxType

internal fun AccountEntity.selectableId(): String? = id.takeUnless { deleted }

internal fun CategoryEntity.selectableId(type: TxType): String? = id.takeUnless { deleted || this.type != type.wire }

/**
 * Код валюты счёта: валюта его баланса, резерв — accounts.currencyCode
 * (как в CsvExportService). Счета в мапе не ищутся — вызывающий сам резолвит
 * счёт по id. Чисел в коде нет, только ISO-код пары валют перевода.
 */
internal fun currencyCodeOf(account: AccountEntity?, balances: Map<String, BalanceEntity>): String? = account?.let { balances[it.balanceId]?.currencyCode ?: it.currencyCode }
