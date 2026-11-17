package com.expeysi.app.domain

import com.expeysi.app.data.db.DebtAdjustmentEntity
import com.expeysi.app.data.db.TransactionEntity
import kotlin.math.abs

/**
 * Чистая математика долгов: знаки операций, вывод kind из направления денег
 * и знака остатка, списание и перепогашение. Остаток нигде не хранится и
 * каждый раз выводится из операций и корректировок ([balances]).
 *
 * Ограничение модели: погашения не привязаны к конкретному выданному долгу —
 * история плоская, а kind выводится из направления и знака остатка
 * ([kindFor]). Сколько из выданного погашено и что именно списано, модель не
 * различает: смешение сторон долга в одной валюте отражается общим остатком
 * пары. Привязка «погашение → долг» здесь не строится сознательно: приложению
 * достаточно остатка отношений с контрагентом, а не учёта по каждому займу.
 */
object DebtCalculator {

    /** Вклад одной операции или корректировки в остаток по паре «контрагент × валюта». */
    data class Delta(val counterpartyId: String, val currencyCode: String, val deltaMinor: Long)

    /** Остаток по контрагенту в одной валюте, выведенный из истории. */
    data class Balance(val counterpartyId: String, val currencyCode: String, val balanceMinor: Long) {
        /** Расчёт закрыт: ноль во всех валютах контрагента выводится по каждой. */
        val isSettled: Boolean get() = balanceMinor == 0L

        /** Положительный остаток — контрагент должен, отрицательный — должен он. */
        val theyOwe: Boolean get() = balanceMinor > 0
    }

    /**
     * Знак вклада долговой транзакции в остаток: выданный долг и отправленное
     * погашение увеличивают остаток (контрагент должен или должен меньше в
     * минусе), взятый долг и полученное погашение — уменьшают. null — операция
     * не долговая и в остаток долга не попадает.
     */
    fun transactionDelta(type: TxType, amountMinor: Long): Long? = when (type) {
        TxType.DEBT_LEND, TxType.DEBT_REPAY_OUT -> amountMinor
        TxType.DEBT_BORROW, TxType.DEBT_REPAY_IN -> -amountMinor
        else -> null
    }

    /** Деньги выходят из счёта? У выданного долга и отправленного погашения — да. */
    fun isOutgoing(type: TxType): Boolean = type == TxType.DEBT_LEND || type == TxType.DEBT_REPAY_OUT

    /**
     * Kind записи из направления денег и знака текущего остатка: деньги из
     * счёта при положительном остатке — новый долг выдан, при минусе —
     * погашение; деньги на счёт при остатке в вашу пользу — полученное
     * погашение, иначе — новый долг взят.
     */
    fun kindFor(outgoing: Boolean, balanceMinor: Long): TxType = when {
        outgoing && balanceMinor < 0 -> TxType.DEBT_REPAY_OUT
        outgoing -> TxType.DEBT_LEND
        balanceMinor > 0 -> TxType.DEBT_REPAY_IN
        else -> TxType.DEBT_BORROW
    }

    /**
     * Kind записи при правке. Пока направление денег не переведено, запись
     * остаётся прежним kind — частично погашенный долг не «переименовывается»
     * в выданный долг из-за изменившегося знака остатка. При смене направления
     * kind выводится заново из направления и знака остатка ([kindFor]).
     */
    fun editKind(
        originalType: TxType,
        directionChanged: Boolean,
        outgoing: Boolean,
        balanceMinor: Long,
    ): TxType = if (!directionChanged) originalType else kindFor(outgoing, balanceMinor)

    /**
     * Знаковая дельта списания: модуль задаёт сумма из формы, знак
     * противоположен остатку. Полное списание обнуляет остаток, частичное —
     * уменьшает модуль. Списание при нулевом остатке долга не меняет ничего:
     * возвращаем 0, иначе ноль «перевернулся» бы в долг обратной стороны.
     */
    fun writeOffDelta(balanceMinor: Long, amountMinor: Long): Long = when {
        balanceMinor == 0L -> 0L
        balanceMinor < 0 -> amountMinor
        else -> -amountMinor
    }

    /**
     * Перепогашение меняет знак: был должником — стал кредитором. Ноль до или
     * после сменой знака не считается — это закрытие или открытие остатка.
     */
    fun flipsSign(before: Long, after: Long): Boolean = before != 0L && after != 0L && (before < 0L) != (after < 0L)

    /** Поле «Погасить» префill — модулем остатка: погашают в сторону нуля. */
    fun repaymentAmount(balanceMinor: Long): Long = abs(balanceMinor)

    /** Срок прошёл раньше начала сегодняшнего дня: сам день ещё не просрочен. */
    fun isOverdue(dueDate: Long, todayStartMillis: Long): Boolean = dueDate < todayStartMillis

    /**
     * Остатки по всем парам «контрагент × валюта». Пустых пар нет: пара
     * появляется, когда по ней есть хотя бы одна операция или корректировка.
     */
    fun balances(deltas: List<Delta>): List<Balance> = deltas
        .groupBy { it.counterpartyId to it.currencyCode }
        .map { (key, rows) -> Balance(key.first, key.second, rows.sumOf { it.deltaMinor }) }

    /** Остаток конкретной пары «контрагент × валюта»; без истории — ноль. */
    fun balanceOf(deltas: List<Delta>, counterpartyId: String, currencyCode: String): Long = deltas
        .filter { it.counterpartyId == counterpartyId && it.currencyCode == currencyCode }
        .sumOf { it.deltaMinor }

    /**
     * Вклады транзакций в остатки долга: не-долговые операции пропускаются,
     * мягко удалённые записи фильтруются здесь же (DAO тоже фильтрует, но
     * расчёт не доверяет вызывающему). Валюта берётся у счёта операции —
     * резолвер получает код по id счёта.
     */
    fun transactionDeltas(
        transactions: List<TransactionEntity>,
        currencyCodeOfAccount: (String) -> String,
    ): List<Delta> = transactions
        .filterNot { it.deleted }
        .mapNotNull { transaction ->
            val type = TxType.fromWire(transaction.type)
            val delta = transactionDelta(type, transaction.amountMinor) ?: return@mapNotNull null
            val counterpartyId = transaction.counterpartyId
            if (counterpartyId.isEmpty()) {
                return@mapNotNull null
            }
            Delta(counterpartyId, currencyCodeOfAccount(transaction.accountId), delta)
        }

    /** Вклады корректировок в остатки долга: знаковая сумма и валюта — внутри записи. */
    fun adjustmentDeltas(adjustments: List<DebtAdjustmentEntity>): List<Delta> = adjustments
        .filterNot { it.deleted }
        .map { Delta(it.counterpartyId, it.currencyCode, it.amountMinor) }

    /**
     * Пары «контрагент × валюта», чей долг закрыт списанием: суммарный вклад
     * пары ноль (расчёты закрыты) и среди её корректировок есть живое
     * списание. Учёт списания в отчётах (countInStats) роли не играет —
     * долг списан независимо от того, попадает ли оно в отчёты. Пары,
     * закрытые деньгами, пометки не требуют: погашение видно в ленте.
     */
    fun pairsClosedByWriteOff(
        deltas: List<Delta>,
        adjustments: List<DebtAdjustmentEntity>,
    ): Set<Pair<String, String>> {
        val settledPairs = deltas
            .groupBy(keySelector = { it.counterpartyId to it.currencyCode }) { it.deltaMinor }
            .filterValues { rows -> rows.sum() == 0L }
            .keys
        val writeOffPairs = adjustments
            .filterNot { it.deleted }
            .filter { DebtAdjustmentKind.fromWire(it.kind) == DebtAdjustmentKind.WRITE_OFF }
            .map { it.counterpartyId to it.currencyCode }
            .toSet()
        return settledPairs intersect writeOffPairs
    }
}
