package com.expeysi.app.domain

data class AggEntry(
    val categoryId: String,
    val accountId: String,
    val type: TxType,
    val amountMinor: Long,
)

data class CategoryTotal(
    val categoryId: String,
    val expenseMinor: Long,
    val incomeMinor: Long,
)

data class AccountTotal(
    val accountId: String,
    val expenseMinor: Long,
    val categories: List<CategoryTotal>,
)

object Aggregates {

    /** Суммы по категориям, по убыванию расхода; категории без операций не возникают. */
    fun byCategory(entries: List<AggEntry>): List<CategoryTotal> =
        entries.groupBy { it.categoryId }
            .map { (categoryId, group) -> total(categoryId, group) }
            .sortedByDescending { it.expenseMinor }

    /** Суммы по счетам с разбивкой по категориям, счета — по убыванию расхода. */
    fun byAccount(entries: List<AggEntry>): List<AccountTotal> =
        entries.groupBy { it.accountId }
            .map { (accountId, group) ->
                AccountTotal(
                    accountId = accountId,
                    expenseMinor = group.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor },
                    categories = byCategory(group).filter { it.expenseMinor > 0 },
                )
            }
            .sortedByDescending { it.expenseMinor }

    private fun total(categoryId: String, group: List<AggEntry>) = CategoryTotal(
        categoryId = categoryId,
        expenseMinor = group.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor },
        incomeMinor = group.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor },
    )
}
