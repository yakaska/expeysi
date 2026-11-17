package com.expeysi.app.data.analysis

import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CounterpartyDao
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.DebtAdjustmentDao
import com.expeysi.app.data.db.DebtAdjustmentEntity
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.DebtWriteOffs
import com.expeysi.app.domain.TransferFees
import com.expeysi.app.domain.TxType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class RoomAnalysisRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val balanceDao: BalanceDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val currencyDao: CurrencyDao,
    private val debtAdjustmentDao: DebtAdjustmentDao,
    private val counterpartyDao: CounterpartyDao,
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
        // Псевдо-категория списаний долга: у корректировки нет ни счёта, ни
        // записи в журнале операций, поэтому список строится из debt_adjustments.
        if (categoryId == DebtWriteOffs.CATEGORY_ID) {
            return writeOffOperations(balanceId, type, startMillis, endMillisExclusive, symbol, limit, offset)
        }
        val category = categoryDao.byId(categoryId)
        val resolvedIds = resolveAccountIds(balanceId, accountIds)
        // Псевдо-категория комиссий переводов: строки — ноги transfer_out с
        // комиссией. id строки — id ноги, поэтому тап открывает сам перевод
        // (редактор поднимает пару по linkedId).
        if (categoryId == TransferFees.CATEGORY_ID) {
            return feeOperations(type, startMillis, endMillisExclusive, resolvedIds, symbol, limit, offset)
        }
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

    /**
     * Страница псевдо-категории списаний долга «в отчётах»: корректировки
     * нужного знака (плюс — доход, минус — расход) по убыванию даты. Счёта у
     * корректировки нет, поэтому фильтр счетов не применяется — как и в
     * [writeOffStats]. Имена контрагентов нужны для заголовка строки.
     */
    private suspend fun writeOffOperations(
        balanceId: String?,
        type: TxType,
        startMillis: Long?,
        endMillisExclusive: Long?,
        symbol: String,
        limit: Int,
        offset: Int,
    ): OperationPage {
        val currencyCode = balanceId?.let { balanceDao.byId(it)?.currencyCode }
        if (currencyCode == null) {
            return OperationPage(
                categoryName = "",
                operations = emptyList(),
                totalMinor = 0L,
                currencySymbol = symbol,
                hasMore = false,
            )
        }
        val adjustments = debtAdjustmentDao.listStatWriteOffs(currencyCode, startMillis, endMillisExclusive)
        val names = counterpartyDao.listActive().associate { it.id to it.name }
        return writeOffOperationPage(adjustments, names, type, currencyCode, symbol, limit, offset)
    }

    /**
     * Страница псевдо-категории комиссий переводов: ноги `transfer_out` с
     * комиссией в скоупе счетов (как и в [feeStats]). Комиссия — только
     * расход, у доходов список пуст. Строка — сама нога перевода, поэтому
     * открывается штатным редактором перевода.
     */
    private suspend fun feeOperations(
        type: TxType,
        startMillis: Long?,
        endMillisExclusive: Long?,
        resolvedIds: List<String>,
        symbol: String,
        limit: Int,
        offset: Int,
    ): OperationPage {
        if (type != TxType.EXPENSE || resolvedIds.isEmpty()) {
            return OperationPage(
                categoryName = "",
                operations = emptyList(),
                totalMinor = 0L,
                currencySymbol = symbol,
                hasMore = false,
            )
        }
        val legs = transactionDao.byTypeAndPeriod(TxType.TRANSFER_OUT.wire, startMillis, endMillisExclusive, resolvedIds)
        val names = accountDao.listActive().associate { it.id to it.name }
        return feeOperationPage(legs, names, symbol, limit, offset)
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

/**
 * Чистая сборка страницы списаний: только нужный знак (плюс — доход, минус —
 * расход), по убыванию даты, с пагинацией. [adjustments] уже отобраны DAO по
 * живому признаку, [countInStats] и виду «списание»; здесь добавляется знак
 * выбранной стороны и валюта отчёта.
 */
internal fun writeOffOperationPage(
    adjustments: List<DebtAdjustmentEntity>,
    counterpartyNames: Map<String, String>,
    type: TxType,
    currencyCode: String,
    symbol: String,
    limit: Int,
    offset: Int,
): OperationPage {
    val matching = adjustments
        .filter { if (type == TxType.EXPENSE) it.amountMinor < 0L else it.amountMinor > 0L }
        .sortedByDescending { it.date }
    val loaded = matching.drop(offset).take(limit)
    val operations = loaded.map { adjustment ->
        Operation(
            id = adjustment.id,
            dateMillis = adjustment.date,
            // Заголовок строки — контрагент, чей долг списан: категория у
            // корректировки одна на всех, а имя объясняет операцию.
            title = counterpartyNames[adjustment.counterpartyId].orEmpty(),
            subtitle = adjustment.note.ifBlank { currencyCode },
            amountMinor = abs(adjustment.amountMinor),
            iconKey = DebtWriteOffs.ICON_KEY,
            colorKey = DebtWriteOffs.COLOR_KEY,
            isIncome = adjustment.amountMinor > 0L,
            isWriteOff = true,
        )
    }
    return OperationPage(
        categoryName = "",
        operations = operations,
        totalMinor = matching.sumOf { abs(it.amountMinor) },
        currencySymbol = symbol,
        hasMore = matching.size > offset + limit,
    )
}

/**
 * Чистая сборка страницы комиссий переводов: ноги `transfer_out` с ненулевой
 * комиссией, по убыванию даты, с пагинацией; итог — сумма комиссий периода.
 * id строки — id ноги, поэтому тап открывает сам перевод.
 */
internal fun feeOperationPage(
    transferOutLegs: List<TransactionEntity>,
    accountNames: Map<String, String>,
    symbol: String,
    limit: Int,
    offset: Int,
): OperationPage {
    val matching = transferOutLegs
        .filter { it.feeMinor > 0L }
        .sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.updatedAt })
    val loaded = matching.drop(offset).take(limit)
    val operations = loaded.map { leg ->
        val from = accountNames[leg.accountId].orEmpty()
        val to = accountNames[leg.counterpartyAccountId].orEmpty()
        Operation(
            id = leg.id,
            dateMillis = leg.date,
            // Заголовок — заметка перевода, иначе счёт-получатель; подпись —
            // «откуда → куда», чтобы строка опознавалась без открытия.
            title = leg.note.ifBlank { to.ifBlank { from } },
            subtitle = if (from.isNotBlank() || to.isNotBlank()) "$from → $to" else "",
            amountMinor = leg.feeMinor,
            iconKey = TransferFees.ICON_KEY,
            colorKey = TransferFees.COLOR_KEY,
            isIncome = false,
        )
    }
    return OperationPage(
        categoryName = "",
        operations = operations,
        totalMinor = matching.sumOf { it.feeMinor },
        currencySymbol = symbol,
        hasMore = matching.size > offset + limit,
    )
}
