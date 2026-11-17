package com.expeysi.app.data.analysis

import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.DebtAdjustmentDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.domain.DebtWriteOffs
import com.expeysi.app.domain.TransferFees
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
    private val debtAdjustmentDao: DebtAdjustmentDao,
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
            } +
            // Комиссия перевода — расход без категории: отдельным
            // псевдо-сектором (см. TransferFees), чтобы итог и донат
            // оставались равны сумме категорий. totalMinor по-прежнему
            // считается суммой категорий.
            feeStats(type, startMillis, endMillisExclusive, resolvedIds) +
            writeOffStats(balanceId, type, startMillis, endMillisExclusive)
        val sorted = stats.sortedByDescending { it.amountMinor }
        return CategorySummary(
            totalMinor = sorted.sumOf { it.amountMinor },
            currencySymbol = symbol,
            categories = sorted,
        )
    }

    /** Псевдо-категория комиссий переводов; пусто у доходов и при комиссиях нет. */
    private suspend fun feeStats(
        type: TxType,
        startMillis: Long?,
        endMillisExclusive: Long?,
        accountIds: List<String>,
    ): List<CategoryStat> {
        if (type != TxType.EXPENSE) return emptyList()
        val feeLegs = transactionDao
            .byTypeAndPeriod(TxType.TRANSFER_OUT.wire, startMillis, endMillisExclusive, accountIds)
            .filter { it.feeMinor > 0 }
        if (feeLegs.isEmpty()) return emptyList()
        return listOf(
            CategoryStat(
                categoryId = TransferFees.CATEGORY_ID,
                name = "",
                iconKey = TransferFees.ICON_KEY,
                colorKey = TransferFees.COLOR_KEY,
                amountMinor = feeLegs.sumOf { it.feeMinor },
                operationCount = feeLegs.size,
            ),
        )
    }

    /**
     * Псевдо-сектор списаний долга «в отчётах»; пусто у баланса без валюты и
     * когда списаний нужного знака за период нет. У корректировки нет счёта,
     * поэтому фильтр счетов её не отсекает — отбор по валюте отчёта: плюс
     * идёт в доходы (INCOME), минус в расходы (EXPENSE), как на форме.
     */
    private suspend fun writeOffStats(
        balanceId: String?,
        type: TxType,
        startMillis: Long?,
        endMillisExclusive: Long?,
    ): List<CategoryStat> {
        if (balanceId == null) return emptyList()
        val currencyCode = balanceDao.byId(balanceId)?.currencyCode ?: return emptyList()
        val rows = debtAdjustmentDao.listStatWriteOffs(currencyCode, startMillis, endMillisExclusive)
        val matching = rows.filter { if (type == TxType.EXPENSE) it.amountMinor < 0 else it.amountMinor > 0 }
        if (matching.isEmpty()) return emptyList()
        return listOf(
            CategoryStat(
                categoryId = DebtWriteOffs.CATEGORY_ID,
                name = "",
                iconKey = DebtWriteOffs.ICON_KEY,
                colorKey = DebtWriteOffs.COLOR_KEY,
                amountMinor = if (type == TxType.EXPENSE) {
                    -matching.sumOf { it.amountMinor }
                } else {
                    matching.sumOf { it.amountMinor }
                },
                operationCount = matching.size,
            ),
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
