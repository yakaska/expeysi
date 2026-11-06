package com.expeysi.app.ui.add

import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorySortingTest {

    @Test
    fun `категории сортируются по убыванию числа операций`() {
        val categories = listOf(category("food", "Еда"), category("home", "Дом"))
        val transactions = listOf(
            transaction("1", "food"),
            transaction("2", "food"),
            transaction("3", "home"),
        )

        val sorted = categories.sortedByUsage(transactions)

        assertEquals(listOf("food", "home"), sorted.map { it.id })
    }

    @Test
    fun `при одинаковом числе операций категории сортируются по имени`() {
        val categories = listOf(category("transport", "Транспорт"), category("home", "Дом"))
        val transactions = listOf(
            transaction("1", "transport"),
            transaction("2", "home"),
            transaction("3", "other"),
        )

        val sorted = categories.sortedByUsage(transactions)

        assertEquals(listOf("home", "transport"), sorted.map { it.id })
    }

    private fun category(id: String, name: String) = CategoryEntity(
        id = id,
        name = name,
        iconKey = "category",
        type = TxType.EXPENSE.wire,
        updatedAt = 0,
    )

    private fun transaction(id: String, categoryId: String) = TransactionEntity(
        id = id,
        type = TxType.EXPENSE.wire,
        amountMinor = 100,
        accountId = "account",
        categoryId = categoryId,
        date = 0,
        updatedAt = 0,
    )
}
