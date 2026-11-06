package com.expeysi.app.ui.add

import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionEntity

internal fun List<CategoryEntity>.sortedByUsage(
    transactions: List<TransactionEntity>,
): List<CategoryEntity> {
    val usageCounts = transactions.groupingBy { it.categoryId }.eachCount()
    return sortedWith(
        compareByDescending<CategoryEntity> { usageCounts[it.id] ?: 0 }
            .thenBy { it.name },
    )
}
