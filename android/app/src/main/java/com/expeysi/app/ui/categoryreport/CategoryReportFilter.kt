package com.expeysi.app.ui.categoryreport

import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.DateRangeFilter
import com.expeysi.app.domain.TxType

internal fun categoryReportBalanceMinor(transactions: List<TransactionEntity>): Long = transactions.sumOf {
    if (TxType.fromWire(it.type) == TxType.INCOME) it.amountMinor else -it.amountMinor
}

internal fun filterCategoryTransactions(
    transactions: List<TransactionEntity>,
    accountIds: Set<String>,
    categoryIds: Set<String>,
    periodStartMillis: Long?,
    customRange: DateRangeFilter?,
): List<TransactionEntity> = transactions
    .filter { it.accountId in accountIds && it.categoryId in categoryIds }
    .filter { transaction ->
        when {
            customRange != null ->
                transaction.date >= customRange.startMillis &&
                    transaction.date < customRange.endMillisExclusive
            periodStartMillis != null -> transaction.date >= periodStartMillis
            else -> true
        }
    }
