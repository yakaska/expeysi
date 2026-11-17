package com.expeysi.app.ui.home

import com.expeysi.app.data.db.DebtAdjustmentEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.DebtWriteOffs
import com.expeysi.app.domain.Transfers
import com.expeysi.app.domain.TxType
import kotlin.math.abs

/**
 * Общая логика лент (главной и истории) поверх [TransactionItem]: суммы для
 * шапок дней и сборка псевдо-строки учтённого списания долга. Здесь нет
 * Compose — расчёт можно покрыть JVM-тестами.
 */
object FeedEntries {

    /** Итог дня для стик-заголовка ленты: сколько доходов и расходов. */
    data class DaySums(val incomeMinor: Long, val expenseMinor: Long)

    /**
     * Доход/расход дня для шапки — тот же P&L по начислению, что и в сводке
     * (ProfitAndLoss): INCOME и EXPENSE плюс комиссии переводов. Переводы
     * нейтральны (перемещение денег), долговые операции тоже: они движут
     * наличными, но не доход и не расход. Псевдо-строки учтённых списаний
     * приходят wire-типами INCOME/EXPENSE, потому считаются этими же
     * фильтрами — шапка дня и сводка всегда сходятся.
     */
    fun daySums(items: List<TransactionItem>): DaySums {
        val incomeMinor = items
            .filter { TxType.fromWire(it.transaction.type) == TxType.INCOME }
            .sumOf { it.transaction.amountMinor }
        val expenseMinor = items
            .filter { TxType.fromWire(it.transaction.type) == TxType.EXPENSE }
            .sumOf { it.transaction.amountMinor } +
            Transfers.feesMinor(items.map { it.transaction })
        return DaySums(incomeMinor, expenseMinor)
    }

    /**
     * Псевдо-строка списания долга «в отчётах» для ленты: у корректировки нет
     * ни счёта, ни записи в журнале операций, поэтому строка строится из неё
     * и не открывается (id с префиксом не ведёт никуда). Знак суммы решает
     * wire-тип: плюс — доход, минус — расход; категория и оформление —
     * псевдо-категория [DebtWriteOffs].
     */
    fun writeOffItem(adjustment: DebtAdjustmentEntity, currencySymbol: String): TransactionItem = TransactionItem(
        transaction = TransactionEntity(
            id = "writeoff-${adjustment.id}",
            type = if (adjustment.amountMinor > 0L) TxType.INCOME.wire else TxType.EXPENSE.wire,
            amountMinor = abs(adjustment.amountMinor),
            accountId = "",
            categoryId = DebtWriteOffs.CATEGORY_ID,
            date = adjustment.date,
            note = adjustment.note,
            updatedAt = adjustment.updatedAt,
        ),
        categoryName = "",
        categoryIconKey = DebtWriteOffs.ICON_KEY,
        categoryColorKey = DebtWriteOffs.COLOR_KEY,
        accountName = "",
        currencySymbol = currencySymbol,
        isWriteOffEntry = true,
    )
}
