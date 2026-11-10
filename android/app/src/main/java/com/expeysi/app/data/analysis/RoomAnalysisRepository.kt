package com.expeysi.app.data.analysis

import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.domain.TxType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomAnalysisRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val balanceDao: BalanceDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val currencyDao: CurrencyDao,
) : AnalysisRepository {

    override suspend fun accounts(balanceId: String?): List<AnalysisAccount> {
        if (balanceId == null) return emptyList()
        return accountDao.listByBalance(balanceId).map {
            AnalysisAccount(id = it.id, name = it.name, iconKey = it.iconKey, colorKey = it.colorKey)
        }
    }

    override suspend fun categorySummary(
        balanceId: String?,
        type: TxType,
        startMillis: Long?,
        endMillisExclusive: Long?,
        accountIds: Set<String>?,
    ): CategorySummary {
        val symbol = currencySymbol(balanceId)
        val resolvedIds = resolveAccountIds(balanceId, accountIds)
        if (resolvedIds.isEmpty()) return CategorySummary(0L, symbol, emptyList())
        val categories = categoryDao.listActive(type.wire)
        val stats = transactionDao
            .byTypeAndPeriod(type.wire, startMillis, endMillisExclusive, resolvedIds)
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
            currencySymbol = symbol,
            categories = stats,
        )
    }

    override suspend fun categoryOperations(
        balanceId: String?,
        categoryId: String,
        type: TxType,
        startMillis: Long?,
        endMillisExclusive: Long?,
        accountIds: Set<String>?,
        limit: Int,
        offset: Int,
    ): OperationPage {
        val symbol = currencySymbol(balanceId)
        val category = categoryDao.byId(categoryId)
        val resolvedIds = resolveAccountIds(balanceId, accountIds)
        if (resolvedIds.isEmpty()) {
            return OperationPage(
                categoryName = category?.name ?: "",
                operations = emptyList(),
                totalMinor = 0L,
                currencySymbol = symbol,
                hasMore = false,
            )
        }
        val rows = transactionDao.byCategoryAndPeriod(
            type = type.wire,
            categoryId = categoryId,
            startMillis = startMillis,
            endMillisExclusive = endMillisExclusive,
            accountIds = resolvedIds,
            limit = limit + 1,
            offset = offset,
        )
        val hasMore = rows.size > limit
        val loaded = rows.take(limit)
        val accountNameById = accountDao.listByBalance(balanceId.orEmpty()).associate { it.id to it.name }
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
                startMillis = startMillis,
                endMillisExclusive = endMillisExclusive,
                accountIds = resolvedIds,
            ),
            currencySymbol = symbol,
            hasMore = hasMore,
        )
    }

    override suspend fun earliestOperationMillis(balanceId: String?): Long? {
        val resolvedIds = resolveAccountIds(balanceId, null)
        if (resolvedIds.isEmpty()) return null
        return transactionDao.earliestMillis(resolvedIds)
    }

    /** Скоуп = все счета баланса, пересечённые с ручным выбором. */
    private suspend fun resolveAccountIds(balanceId: String?, accountIds: Set<String>?): List<String> {
        if (balanceId == null) return emptyList()
        val balanceAccountIds = accountDao.listByBalance(balanceId).map { it.id }.toSet()
        val selection = accountIds ?: balanceAccountIds
        return (balanceAccountIds intersect selection).toList()
    }

    /** Символ валюты баланса; у одного баланса одна валюта, смешивать нельзя. */
    private suspend fun currencySymbol(balanceId: String?): String {
        val code = balanceId?.let { balanceDao.byId(it)?.currencyCode } ?: return ""
        return currencyDao.listAll().find { it.code == code }?.symbol ?: code
    }
}
