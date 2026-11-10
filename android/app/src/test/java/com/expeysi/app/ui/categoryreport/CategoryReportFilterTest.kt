package com.expeysi.app.ui.categoryreport

import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.DateRangeFilter
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryReportFilterTest {

    @Test
    fun `баланс складывает доходы и вычитает расходы выбранных категорий`() {
        val transactions = listOf(
            transaction("expense", "food", TxType.EXPENSE, 250),
            transaction("income", "salary", TxType.INCOME, 5_000),
            transaction("other", "transport", TxType.EXPENSE, 100),
        )

        val result = filterCategoryTransactions(
            transactions = transactions,
            accountIds = setOf("account"),
            categoryIds = setOf("food", "salary"),
            periodStartMillis = null,
            customRange = null,
        )

        assertEquals(listOf("expense", "income"), result.map { it.id })
        assertEquals(4_750, categoryReportBalanceMinor(result))
    }

    @Test
    fun `учитываются только счета текущего баланса`() {
        val result = filterCategoryTransactions(
            transactions = listOf(
                transaction("selected", "food", TxType.EXPENSE, 250),
                transaction("other-account", "food", TxType.EXPENSE, 100, accountId = "other-account"),
            ),
            accountIds = setOf("account"),
            categoryIds = setOf("food"),
            periodStartMillis = null,
            customRange = null,
        )

        assertEquals(listOf("selected"), result.map { it.id })
    }

    @Test
    fun `произвольный период исключает правую границу`() {
        val result = filterCategoryTransactions(
            transactions = listOf(
                transaction("before", "food", TxType.EXPENSE, 1, date = 99),
                transaction("start", "food", TxType.EXPENSE, 1, date = 100),
                transaction("end", "food", TxType.EXPENSE, 1, date = 199),
                transaction("after", "food", TxType.EXPENSE, 1, date = 200),
            ),
            accountIds = setOf("account"),
            categoryIds = setOf("food"),
            periodStartMillis = 100,
            customRange = DateRangeFilter(startMillis = 100, endMillisExclusive = 200),
        )

        assertEquals(listOf("start", "end"), result.map { it.id })
    }

    private fun transaction(
        id: String,
        categoryId: String,
        type: TxType,
        amountMinor: Long,
        date: Long = 0,
        accountId: String = "account",
    ) = TransactionEntity(
        id = id,
        type = type.wire,
        amountMinor = amountMinor,
        accountId = accountId,
        categoryId = categoryId,
        date = date,
        updatedAt = 0,
    )
}
