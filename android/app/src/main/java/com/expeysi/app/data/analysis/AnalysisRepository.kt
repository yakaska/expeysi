package com.expeysi.app.data.analysis

import com.expeysi.app.domain.TxType
import java.time.YearMonth

/** Счёт для фильтра шторки. */
data class AnalysisAccount(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    /** Последние 4 цифры; пусто, когда в данных нет номера. */
    val last4: String = "",
)

/** Итог по категории за период внутри отчёта. */
data class CategoryStat(
    val categoryId: String,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val amountMinor: Long,
    val operationCount: Int,
)

/** Итог отчёта за период: сумма, валюта и категории по убыванию суммы. */
data class CategorySummary(
    val totalMinor: Long,
    val currencySymbol: String,
    val categories: List<CategoryStat>,
)

/** Операция в списке категории. */
data class Operation(
    val id: String,
    val dateMillis: Long,
    val title: String,
    val subtitle: String,
    val amountMinor: Long,
    val iconKey: String,
    val colorKey: String,
    val isIncome: Boolean,
)

/** Страница списка: операции по убыванию даты, сумма за весь период и признак продолжения. */
data class OperationPage(
    val categoryName: String,
    val operations: List<Operation>,
    val totalMinor: Long,
    val currencySymbol: String,
    val hasMore: Boolean,
)

/**
 * Данные экрана «Анализ финансов». Расходы и доходы запрашиваются раздельно,
 * всегда в рамках одного баланса; [accountIds] == null — все счета баланса,
 * [balanceId] == null — баланса нет, данные пустые.
 */
interface AnalysisRepository {

    /** Счета одного баланса; при [balanceId] == null — пустой список. */
    suspend fun accounts(balanceId: String?): List<AnalysisAccount>

    suspend fun categorySummary(
        balanceId: String?,
        type: TxType,
        yearMonth: YearMonth,
        accountIds: Set<String>?,
    ): CategorySummary

    suspend fun categoryOperations(
        balanceId: String?,
        categoryId: String,
        type: TxType,
        yearMonth: YearMonth,
        accountIds: Set<String>?,
        limit: Int,
        offset: Int,
    ): OperationPage
}
