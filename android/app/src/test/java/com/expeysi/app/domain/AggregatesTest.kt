package com.expeysi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AggregatesTest {

    private fun expense(category: String, account: String, amount: Long) = AggEntry(category, account, TxType.EXPENSE, amount)

    private fun income(category: String, account: String, amount: Long) = AggEntry(category, account, TxType.INCOME, amount)

    @Test
    fun `суммы по категориям с разделением доходов и расходов`() {
        val totals = Aggregates.byCategory(
            listOf(
                expense("food", "cash", 100),
                expense("food", "card", 250),
                income("salary", "card", 5000),
            ),
        )
        assertEquals(2, totals.size)
        assertEquals(CategoryTotal("food", 350, 0), totals[0])
        assertEquals(CategoryTotal("salary", 0, 5000), totals[1])
    }

    @Test
    fun `сортировка по убыванию расхода`() {
        val totals = Aggregates.byCategory(
            listOf(
                expense("a", "cash", 100),
                expense("b", "cash", 900),
                expense("c", "cash", 500),
            ),
        )
        assertEquals(listOf("b", "c", "a"), totals.map { it.categoryId })
    }

    @Test
    fun `пустой вход даёт пустой список`() {
        assertEquals(emptyList<CategoryTotal>(), Aggregates.byCategory(emptyList()))
    }

    @Test
    fun `счета с вложенными категориями`() {
        val totals = Aggregates.byAccount(
            listOf(
                expense("food", "cash", 100),
                expense("bus", "cash", 50),
                expense("food", "card", 900),
                income("salary", "card", 5000),
            ),
        )
        assertEquals(listOf("card", "cash"), totals.map { it.accountId })
        assertEquals(900, totals[0].expenseMinor)
        assertEquals(listOf("food", "salary"), totals[0].categories.map { it.categoryId })
        assertEquals(150, totals[1].expenseMinor)
        assertEquals(listOf("food", "bus"), totals[1].categories.map { it.categoryId })
    }

    @Test
    fun `доходные операции попадают в суммы и категории счетов`() {
        val totals = Aggregates.byAccount(
            listOf(
                income("salary", "card", 5000),
                income("gift", "card", 1000),
                income("cashback", "cash", 200),
            ),
        )

        assertEquals(6000, totals[0].incomeMinor)
        assertEquals(listOf("salary", "gift"), totals[0].categories.map { it.categoryId })
        assertEquals(listOf(5000L, 1000L), totals[0].categories.map { it.incomeMinor })
        assertEquals(200, totals[1].incomeMinor)
        assertEquals(listOf("cashback"), totals[1].categories.map { it.categoryId })
    }
}
