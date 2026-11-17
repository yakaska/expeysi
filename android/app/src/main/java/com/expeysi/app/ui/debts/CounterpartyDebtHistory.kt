package com.expeysi.app.ui.debts

import androidx.annotation.StringRes
import com.expeysi.app.R
import com.expeysi.app.data.db.DebtAdjustmentEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.DebtAdjustmentKind
import com.expeysi.app.domain.DebtCalculator
import com.expeysi.app.domain.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Чистая логика истории контрагента: вид операции, тон и знак строки
 * (направление наличных, а не остаток долга), бегущий остаток после каждой
 * операции, сводки по валютам и статус срока возврата. Здесь нет Compose:
 * расчёт живёт в ViewModel, экран только отображает готовые значения.
 */
object CounterpartyDebtHistory {

    /** Вид долговой операции: транзакции дают первые четыре, корректировки — последние два. */
    enum class DebtOpKind {
        LEND,
        BORROW,
        REPAY_IN,
        REPAY_OUT,
        WRITE_OFF,
        OPENING_BALANCE,
        ;
    }

    /** Из долгового типа транзакции: null — тип не долговая операция. */
    fun debtKindOf(type: TxType): DebtOpKind? = when (type) {
        TxType.DEBT_LEND -> DebtOpKind.LEND
        TxType.DEBT_BORROW -> DebtOpKind.BORROW
        TxType.DEBT_REPAY_IN -> DebtOpKind.REPAY_IN
        TxType.DEBT_REPAY_OUT -> DebtOpKind.REPAY_OUT
        else -> null
    }

    /** Из вида корректировки. */
    fun debtKindOf(kind: DebtAdjustmentKind): DebtOpKind = when (kind) {
        DebtAdjustmentKind.OPENING_BALANCE -> DebtOpKind.OPENING_BALANCE
        DebtAdjustmentKind.WRITE_OFF -> DebtOpKind.WRITE_OFF
    }

    /** Направление наличных строки: зелёное «пришли», красное «ушли» или нейтральное. */
    enum class DebtHistoryTone { POSITIVE, NEGATIVE, NEUTRAL }

    /** Знак суммы строки: «+», «−» или null — суммы без знака (наличных нет). */
    enum class DebtHistorySign { PLUS, MINUS }

    /**
     * Семантика строки истории: цвет суммы с иконкой и знак. Карта одна
     * на весь экран: тон идёт от потока наличных.
     */
    data class DebtRowSemantics(val tone: DebtHistoryTone, val sign: DebtHistorySign?)

    /**
     * Тон и знак по виду операции и вкладу в остаток:
     * - выданный долг и отправленное погашение — деньги ушли: минус, красный;
     * - взятый долг и полученное погашение — деньги пришли: плюс, зелёный;
     * - списание — наличных нет: нейтрально и без знака;
     * - начальный остаток — наличных нет: нейтрально, знак по вкладу
     *   (плюс — контрагент должен, минус — должен я).
     */
    fun debtRowSemantics(kind: DebtOpKind, deltaMinor: Long): DebtRowSemantics = when (kind) {
        DebtOpKind.LEND, DebtOpKind.REPAY_OUT -> DebtRowSemantics(
            tone = DebtHistoryTone.NEGATIVE,
            sign = DebtHistorySign.MINUS,
        )
        DebtOpKind.BORROW, DebtOpKind.REPAY_IN -> DebtRowSemantics(
            tone = DebtHistoryTone.POSITIVE,
            sign = DebtHistorySign.PLUS,
        )
        DebtOpKind.WRITE_OFF -> DebtRowSemantics(tone = DebtHistoryTone.NEUTRAL, sign = null)
        DebtOpKind.OPENING_BALANCE -> DebtRowSemantics(
            tone = DebtHistoryTone.NEUTRAL,
            sign = if (deltaMinor >= 0) DebtHistorySign.PLUS else DebtHistorySign.MINUS,
        )
    }

    /** Подпись вида операции: «Долг выдан», «Погашение получено»… */
    @StringRes
    fun debtKindLabelRes(kind: DebtOpKind): Int = when (kind) {
        DebtOpKind.LEND -> R.string.debts_kind_lend
        DebtOpKind.BORROW -> R.string.debts_kind_borrow
        DebtOpKind.REPAY_IN -> R.string.debts_kind_repay_in
        DebtOpKind.REPAY_OUT -> R.string.debts_kind_repay_out
        DebtOpKind.WRITE_OFF -> R.string.debts_kind_write_off
        DebtOpKind.OPENING_BALANCE -> R.string.debts_action_opening_balance
    }

    /**
     * Операция истории в общем виде: долговая транзакция или корректировка.
     * [deltaMinor] — знаковый вклад в остаток долга.
     */
    data class DebtOp(
        val id: String,
        val isAdjustment: Boolean,
        val date: Long,
        val updatedAt: Long,
        val currencyCode: String,
        val kind: DebtOpKind,
        val deltaMinor: Long,
        val note: String,
        /** Срок возврата: только у выданного и взятого долга. */
        val dueDate: Long? = null,
    )

    /** Из транзакции: валюта резолвится в ViewModel по счёту. */
    fun transactionOp(transaction: TransactionEntity, currencyCode: String): DebtOp? {
        val type = TxType.fromWire(transaction.type)
        val delta = DebtCalculator.transactionDelta(type, transaction.amountMinor) ?: return null
        val kind = debtKindOf(type) ?: return null
        return DebtOp(
            id = transaction.id,
            isAdjustment = false,
            date = transaction.date,
            updatedAt = transaction.updatedAt,
            currencyCode = currencyCode,
            kind = kind,
            deltaMinor = delta,
            note = transaction.note,
            dueDate = transaction.dueDate,
        )
    }

    /** Из корректировки: валюта и знак вклада живут в записи. */
    fun adjustmentOp(adjustment: DebtAdjustmentEntity): DebtOp = DebtOp(
        id = adjustment.id,
        isAdjustment = true,
        date = adjustment.date,
        updatedAt = adjustment.updatedAt,
        currencyCode = adjustment.currencyCode,
        kind = debtKindOf(DebtAdjustmentKind.fromWire(adjustment.kind)),
        deltaMinor = adjustment.amountMinor,
        note = adjustment.note,
    )

    /** Статус срока возврата у строки истории. */
    sealed interface DebtDueStatus {
        /**
         * Долг не закрыт: срок вернули строке как есть, [overdueDays] —
         * число просроченных дней (0 — срок ещё не прошёл).
         */
        data class Open(val overdueDays: Int) : DebtDueStatus

        /**
         * Долг закрыт: [daysDelta] — дней от срока, минус раньше срока,
         * ноль вовремя, плюс позже.
         */
        data class Closed(val daysDelta: Int) : DebtDueStatus
    }

    /** Дней между сроком и закрытием: минус — раньше срока, плюс — позже. */
    fun closedDaysDelta(closingDate: LocalDate, dueDate: LocalDate): Int =
        ChronoUnit.DAYS.between(dueDate, closingDate).toInt()

    /**
     * Статус срока строки: у закрытого долга — разница от даты закрытия,
     * у открытого — просрочка от сегодня (0 — срок не прошёл).
     */
    fun dueStatus(
        dueDate: LocalDate,
        closingDate: LocalDate?,
        today: LocalDate,
    ): DebtDueStatus = when {
        closingDate != null -> DebtDueStatus.Closed(daysDelta = closedDaysDelta(closingDate, dueDate))
        dueDate < today -> DebtDueStatus.Open(overdueDays = ChronoUnit.DAYS.between(dueDate, today).toInt())
        else -> DebtDueStatus.Open(overdueDays = 0)
    }

    /** Строка истории: операция, остаток долга сразу после неё и статус срока. */
    data class DebtHistoryRow(
        val op: DebtOp,
        /** Остаток пары «контрагент × валюта» после этой операции. */
        val runningBalanceMinor: Long,
        /** Срок возврата: задан только у LEND и BORROW. */
        val dueStatus: DebtDueStatus?,
    )

    /** Сводка пары «контрагент × валюта»: остаток и итоги потоков наличных. */
    data class DebtCurrencySummary(
        val currencyCode: String,
        val balanceMinor: Long,
        /** Всего выдано контрагенту. */
        val lentMinor: Long,
        /** Всего получено погашений. */
        val repaidInMinor: Long,
        /** Всего взято у контрагента. */
        val borrowedMinor: Long,
        /** Всего отправлено погашений. */
        val repaidOutMinor: Long,
        /** Последняя операция, обнулившая остаток; null — долг не закрыт. */
        val closingOp: DebtOp?,
    ) {
        val isSettled: Boolean get() = balanceMinor == 0L

        /** Дата закрытия долга: у закрытого пары — дата последней обнулившей операции. */
        val closingDate: Long? get() = closingOp?.date
    }

    /** Результат свёртки истории контрагента. */
    data class DebtHistoryFold(
        /** Строки списка: по убыванию даты, с бегущим остатком в каждой. */
        val rows: List<DebtHistoryRow>,
        /** Сводки по валютам, по убыванию модуля остатка. */
        val summaries: List<DebtCurrencySummary>,
        /** Дата первой операции; null — операций нет. */
        val firstDate: Long?,
        /**
         * Дата полного закрытия долга: последняя по всем валютам дата
         * обнуления, когда все пары закрыты; null — долг открыт или пуст.
         */
        val closingDate: Long?,
    )

    /**
     * Хронологическая свёртка операций: по возрастанию даты (при равенстве
     * — updatedAt, затем id), остаток пары копится по валюте. Список же
     * показывает строки по убыванию — бегущий остаток уже посчитан в каждой.
     */
    fun fold(ops: List<DebtOp>, today: LocalDate, zone: ZoneId): DebtHistoryFold {
        val ordered = ops.sortedWith(compareBy({ it.date }, { it.updatedAt }, { it.id }))

        val accumulators = mutableMapOf<String, Accumulator>()
        val runningById = mutableMapOf<String, Long>()
        ordered.forEach { op ->
            val acc = accumulators.getOrPut(op.currencyCode) { Accumulator() }
            acc.add(op)
            runningById[op.id] = acc.balance
        }

        val closingOpByCurrency = accumulators
            .filterValues { it.balance == 0L }
            .mapValues { (_, acc) -> acc.lastZeroOp }

        val summaries = accumulators
            .map { (code, acc) ->
                DebtCurrencySummary(
                    currencyCode = code,
                    balanceMinor = acc.balance,
                    lentMinor = acc.lent,
                    repaidInMinor = acc.repaidIn,
                    borrowedMinor = acc.borrowed,
                    repaidOutMinor = acc.repaidOut,
                    closingOp = closingOpByCurrency[code],
                )
            }
            .sortedByDescending { abs(it.balanceMinor) }

        val rows = ordered.asReversed().map { op ->
            val closingDate = closingOpByCurrency[op.currencyCode]?.date
                ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            DebtHistoryRow(
                op = op,
                runningBalanceMinor = runningById[op.id] ?: 0L,
                dueStatus = op.dueDate
                    ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                    ?.let { dueStatus(it, closingDate, today) },
            )
        }

        return DebtHistoryFold(
            rows = rows,
            summaries = summaries,
            firstDate = ordered.firstOrNull()?.date,
            closingDate = summaries
                .takeIf { it.isNotEmpty() && it.all(DebtCurrencySummary::isSettled) }
                ?.maxOf { it.closingDate ?: 0L },
        )
    }

    /** Накопитель одной пары «контрагент × валюта» внутри свёртки. */
    private class Accumulator {
        var balance = 0L
        var lent = 0L
        var repaidIn = 0L
        var borrowed = 0L
        var repaidOut = 0L
        var lastZeroOp: DebtOp? = null

        fun add(op: DebtOp) {
            balance += op.deltaMinor
            val amount = abs(op.deltaMinor)
            when (op.kind) {
                DebtOpKind.LEND -> lent += amount
                DebtOpKind.BORROW -> borrowed += amount
                DebtOpKind.REPAY_IN -> repaidIn += amount
                DebtOpKind.REPAY_OUT -> repaidOut += amount
                DebtOpKind.WRITE_OFF, DebtOpKind.OPENING_BALANCE -> Unit
            }
            // Остаток обнулился: последняя такая операция закрывает долг.
            if (balance == 0L) lastZeroOp = op
        }
    }
}
