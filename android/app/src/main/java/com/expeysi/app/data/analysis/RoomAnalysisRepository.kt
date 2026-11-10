package com.expeysi.app.data.analysis

import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.domain.Periods
import com.expeysi.app.domain.TxType
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomAnalysisRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val currencyDao: CurrencyDao,
) : AnalysisRepository {

    private val zone: ZoneId = ZoneId.systemDefault()

    override suspend fun accounts(): List<AnalysisAccount> = accountDao.listActive().map {
        AnalysisAccount(id = it.id, name = it.name, iconKey = it.iconKey, colorKey = it.colorKey)
    }

    override suspend fun categorySummary(
        type: TxType,
        yearMonth: YearMonth,
        accountIds: Set<String>?,
    ): CategorySummary {
        val resolvedIds = resolveAccountIds(accountIds)
        if (resolvedIds.isEmpty()) return CategorySummary(0L, currencySymbol(), emptyList())
        val bounds = Periods.month(yearMonth.atDay(1), zone)
        val categories = categoryDao.listActive(type.wire)
        val stats = transactionDao
            .byTypeAndPeriod(type.wire, bounds.startMillis, bounds.endMillisExclusive, resolvedIds)
            .groupBy { it.categoryId }
            .map { (categoryId, rows) ->
                val category = categories.firstOrNull { it.id == categoryId }
                CategoryStat(
                    categoryId = categoryId,
                    name = category?.name ?: "",
                    iconKey = category?.iconKey ?: "",
                    colorKey = category?.colorKey ?: "",
                    amountMinor = rows.sumOf { it.amountMinor },
                    operationCount = rows.size,
                )
            }
            .sortedByDescending { it.amountMinor }
        return CategorySummary(
            totalMinor = stats.sumOf { it.amountMinor },
            currencySymbol = currencySymbol(),
            categories = stats,
        )
    }

    override suspend fun categoryOperations(
        categoryId: String,
        type: TxType,
        yearMonth: YearMonth,
        accountIds: Set<String>?,
        limit: Int,
        offset: Int,
    ): OperationPage {
        val resolvedIds = resolveAccountIds(accountIds)
        val category = categoryDao.byId(categoryId)
        if (resolvedIds.isEmpty()) {
            return OperationPage(
                categoryName = category?.name ?: "",
                operations = emptyList(),
                totalMinor = 0L,
                currencySymbol = currencySymbol(),
                hasMore = false,
            )
        }
        val bounds = Periods.month(yearMonth.atDay(1), zone)
        val rows = transactionDao.byCategoryAndPeriod(
            type = type.wire,
            categoryId = categoryId,
            startMillis = bounds.startMillis,
            endMillis = bounds.endMillisExclusive,
            accountIds = resolvedIds,
            limit = limit + 1,
            offset = offset,
        )
        val hasMore = rows.size > limit
        val loaded = rows.take(limit)
        val accountNameById = accountDao.listActive().associate { it.id to it.name }
        val operations = loaded.map { tx ->
            Operation(
                id = tx.id,
                dateMillis = tx.date,
                title = category?.name ?: "",
                subtitle = tx.note.ifBlank { accountNameById[tx.accountId].orEmpty() },
                amountMinor = tx.amountMinor,
                iconKey = category?.iconKey ?: "",
                colorKey = category?.colorKey ?: "",
                isIncome = type == TxType.INCOME,
            )
        }
        return OperationPage(
            categoryName = category?.name ?: "",
            operations = operations,
            totalMinor = transactionDao.sumByCategoryAndPeriod(
                type = type.wire,
                categoryId = categoryId,
                startMillis = bounds.startMillis,
                endMillis = bounds.endMillisExclusive,
                accountIds = resolvedIds,
            ),
            currencySymbol = currencySymbol(),
            hasMore = hasMore,
        )
    }

    private suspend fun resolveAccountIds(accountIds: Set<String>?): List<String> = accountIds?.toList() ?: accountDao.listActive().map { it.id }

    private suspend fun currencySymbol(): String = currencyDao.listAll().find { it.code == "RUB" }?.symbol ?: "RUB"
}
