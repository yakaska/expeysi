package com.expeysi.app.domain

import com.expeysi.app.data.db.DebtAdjustmentEntity
import com.expeysi.app.data.db.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Доходы и расходы по начислению («P&L»): обычные доходы и расходы, комиссии
 * переводов и списания долга, учтённые в отчётах (countInStats). Долговые
 * потоки денег (выдача, взятие, погашения) — движение наличных по счёту,
 * в доходы и расходы они не попадают: долгу соответствует свой раздел.
 *
 * Это определение одинаково применяется в сводках главного экрана
 * ([windowSums]) и в шапках дней лент — псевдо-строки учтённых списаний
 * приходят в ленты wire-типами INCOME/EXPENSE и потому считаются теми же
 * фильтрами (см. ui/home FeedEntries).
 */
object ProfitAndLoss {

    /** Окна сумм обеих сторон P&L: для карточек «Доходы» и «Расходы» сводки. */
    data class Sums(val income: WindowSums, val expense: WindowSums)

    /**
     * Скользящие окна (сегодня/7 дней/30 дней) по операциям скоупа:
     * @param accountIds счета выбранного баланса — фильтр операций;
     * @param balanceCurrencyCode валюта баланса — списания долга «в отчётах»
     *   входят по ней (у корректировки счёта нет), null — баланса нет и в
     *   сводку они не попадают.
     */
    fun windowSums(
        transactions: List<TransactionEntity>,
        adjustments: List<DebtAdjustmentEntity>,
        accountIds: Set<String>,
        balanceCurrencyCode: String?,
        todayStartMillis: Long,
        zone: ZoneId,
    ): Sums {
        val scoped = transactions.filter { !it.deleted && it.accountId in accountIds }
        fun dayStart(millis: Long): Long = Periods.day(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), zone).startMillis

        // Комиссия перевода — реальная потеря денег семьи: входит в окно
        // расходов сверх обычных расходов; у ноги зачисления комиссии нет.
        val feeAmounts = scoped
            .filter { TxType.fromWire(it.type) == TxType.TRANSFER_OUT && it.feeMinor > 0 }
            .map { TxDayAmount(it.feeMinor, dayStart(it.date)) }
        // Списание «в отчётах» — безналичный плюс/минус: плюс — доход,
        // минус — расход того дня, когда корректировка сделана.
        // Только списания: у начального остатка счётчик в отчётах всегда
        // выключен формой, но синк мог принести запись и с ним — в доходы
        // и расходы попадают лишь WRITE_OFF (как и в разрезках анализа).
        val writeOffAmounts = adjustments
            .filter {
                !it.deleted &&
                    it.countInStats &&
                    it.currencyCode == balanceCurrencyCode &&
                    DebtAdjustmentKind.fromWire(it.kind) == DebtAdjustmentKind.WRITE_OFF
            }
            .map { TxDayAmount(it.amountMinor, dayStart(it.date)) }

        return Sums(
            income = Windows.sums(
                scoped
                    .filter { TxType.fromWire(it.type) == TxType.INCOME }
                    .map { TxDayAmount(it.amountMinor, dayStart(it.date)) } +
                    writeOffAmounts.filter { it.amountMinor > 0L },
                todayStartMillis,
            ),
            expense = Windows.sums(
                scoped
                    .filter { TxType.fromWire(it.type) == TxType.EXPENSE }
                    .map { TxDayAmount(it.amountMinor, dayStart(it.date)) } +
                    feeAmounts +
                    writeOffAmounts.filter { it.amountMinor < 0L }
                        .map { TxDayAmount(-it.amountMinor, it.dayStartMillis) },
                todayStartMillis,
            ),
        )
    }

    /** День начала окна «сегодня» — тот же расчёт, что и в лентах. */
    fun todayStartMillis(zone: ZoneId, today: LocalDate = LocalDate.now(zone)): Long = Periods.day(today, zone).startMillis
}
