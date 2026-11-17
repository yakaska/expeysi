package com.expeysi.app.ui.debts

import com.expeysi.app.R
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CounterpartyEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.DebtCalculator
import com.expeysi.app.domain.TxType
import kotlin.math.abs

/** Строка списка долгов: контрагент и его остатки по валютам. */
data class DebtsRow(
    val counterparty: CounterpartyEntity,
    /** Остатки «контрагент × валюта», отсортированы по модулю по убыванию. */
    val balances: List<DebtCalculator.Balance>,
    /**
     * У контрагента просроченный срок: долг с датой возврата раньше сегодня,
     * чья пара «контрагент × валюта» ещё не закрыта (см. [overdueCounterpartyIds]).
     */
    val overdue: Boolean = false,
)

/** Сортировка строк списка долгов; по умолчанию — по имени, как в БД. */
enum class DebtsSort {
    NAME,

    /** По наибольшему модулю остатка (в любой валюте). */
    BALANCE,

    /** Сначала просроченные, дальше по модулю остатка. */
    OVERDUE,
}

/** Фильтр строки по стороне долга: все, «должны мне», «должен я». */
enum class DebtsSideFilter {
    ALL,
    OWED_TO_ME,
    OWED_BY_ME,
}

/** Сводка всех долгов: по каждой валюте суммарно на каждой стороне. */
data class DebtsTotals(
    /** Контрагенты должны: сумма положительных остатков. */
    val owedToMe: Map<String, Long>,
    /** Должен я: сумма модулей отрицательных остатков. */
    val owedByMe: Map<String, Long>,
)

/**
 * Итоги по всем контрагентам, включая архивных и закрытых: секция скрывает
 * строку, но не отменяет сам долг — сводка показывает его целиком.
 */
fun debtsTotals(rows: List<DebtsRow>): DebtsTotals = DebtsTotals(
    owedToMe = rows
        .asSequence()
        .flatMap { it.balances }
        .filter { it.balanceMinor > 0 }
        .groupBy { it.currencyCode }
        .mapValues { (_, list) -> list.sumOf { it.balanceMinor } },
    owedByMe = rows
        .asSequence()
        .flatMap { it.balances }
        .filter { it.balanceMinor < 0 }
        .groupBy { it.currencyCode }
        .mapValues { (_, list) -> list.sumOf { abs(it.balanceMinor) } },
)

/**
 * Порядок строк выбранной сортировки. NAME — по алфавиту без регистра;
 * BALANCE — по наибольшему модулю остатка строки, запасной ключ — имя;
 * OVERDUE — просроченные первыми, дальше по модулю остатка.
 */
fun sortDebtsRows(rows: List<DebtsRow>, sort: DebtsSort): List<DebtsRow> = when (sort) {
    DebtsSort.NAME -> rows.sortedBy { it.counterparty.name.lowercase() }
    DebtsSort.BALANCE -> rows.sortedWith(
        compareByDescending<DebtsRow> { maxBalanceMagnitude(it) }
            .thenBy { it.counterparty.name.lowercase() },
    )
    DebtsSort.OVERDUE -> rows.sortedWith(
        compareByDescending<DebtsRow> { it.overdue }
            .thenByDescending { maxBalanceMagnitude(it) }
            .thenBy { it.counterparty.name.lowercase() },
    )
}

/** Наибольший модуль остатка строки: «сколько весит» этот контрагент. */
private fun maxBalanceMagnitude(row: DebtsRow): Long = row.balances
    .maxOfOrNull { abs(it.balanceMinor) } ?: 0L

/**
 * Фильтр строк по стороне долга: строка проходит OWED_TO_ME, если хотя бы
 * в одной валюте контрагент должен, OWED_BY_ME — если должен он.
 */
fun filterDebtsRows(rows: List<DebtsRow>, side: DebtsSideFilter): List<DebtsRow> = when (side) {
    DebtsSideFilter.ALL -> rows
    DebtsSideFilter.OWED_TO_ME -> rows.filter { row -> row.balances.any { it.balanceMinor > 0 } }
    DebtsSideFilter.OWED_BY_ME -> rows.filter { row -> row.balances.any { it.balanceMinor < 0 } }
}

/**
 * Идентификаторы контрагентов с просроченным сроком: долг с датой возврата
 * раньше начала сегодняшнего дня ([DebtCalculator.isOverdue]), у которого
 * пара «контрагент × валюта» ещё не закрыта. Сравнение идёт в валюте
 * операции — резолвер получает код по id счёта.
 *
 * TODO: напоминания о приближающихся сроках (как у расходного напоминания)
 * сознательно не построены — это отдельный воркер и настройка; вернуться при
 * явном запросе.
 */
fun overdueCounterpartyIds(
    transactions: List<TransactionEntity>,
    balances: List<DebtCalculator.Balance>,
    todayStartMillis: Long,
    currencyCodeOfAccount: (String) -> String,
): Set<String> {
    val openPairs = balances
        .filter { it.balanceMinor != 0L }
        .map { it.counterpartyId to it.currencyCode }
        .toSet()
    return transactions
        .filter { TxType.fromWire(it.type).isDebt && it.counterpartyId.isNotEmpty() }
        .mapNotNull { tx ->
            val dueDate = tx.dueDate ?: return@mapNotNull null
            if (!DebtCalculator.isOverdue(dueDate, todayStartMillis)) return@mapNotNull null
            tx.counterpartyId to currencyCodeOfAccount(tx.accountId)
        }
        .filter { it in openPairs }
        .map { it.first }
        .toSet()
}

data class DebtsUiState(
    val rows: List<DebtsRow>,
    /** Закрытые контрагенты: ноль во всех валютах, показываются в секции «Закрытые». */
    val settled: List<DebtsRow>,
    val archived: List<DebtsRow>,
    val totals: DebtsTotals,
    val currencySymbols: Map<String, String>,
    /** Фильтр строк по стороне долга: применён к открытым строкам. */
    val sideFilter: DebtsSideFilter = DebtsSideFilter.ALL,
    /** Выбранная сортировка строк. */
    val sort: DebtsSort = DebtsSort.NAME,
)

/* ----------------------- Отображение остатков строки ----------------------- */

/** Тон строки долга: зелёный — должны мне, красный — должен я, серый — закрыт. */
enum class DebtsTone { POSITIVE, NEGATIVE, NEUTRAL }

/** Одна валютная часть подписи строки: «Вам должны 1 000 ₽». */
data class DebtsBalancePart(
    /** Модуль остатка, всегда больше нуля: нулевые валюты скрываются. */
    val amountMinor: Long,
    val currencyCode: String,
    /** Строка подписи по знаку: «Вам должны» или «Вы должны». */
    val textRes: Int,
)

/** Как показать остатки контрагента: непустые валютные части и тон цвета. */
data class DebtsRowDisplay(
    val parts: List<DebtsBalancePart>,
    val tone: DebtsTone,
) {
    /** Ноль во всех валютах (или операций нет): одна строка «Расчёты закрыты». */
    val settled: Boolean get() = parts.isEmpty()
}

/**
 * Отображение строки по знаковым остаткам: нулевые валюты скрыты, тон — по
 * направлению. Зелёный — контрагент должен в какой-то валюте, красный —
 * должен он; смешанные направления и закрытый остаток — нейтральный серый.
 */
fun debtsRowDisplay(balances: List<DebtCalculator.Balance>): DebtsRowDisplay {
    val nonZero = balances.filter { it.balanceMinor != 0L }
    val owesYou = nonZero.any { it.balanceMinor > 0L }
    val owedByYou = nonZero.any { it.balanceMinor < 0L }
    val tone = when {
        owesYou && !owedByYou -> DebtsTone.POSITIVE
        !owesYou && owedByYou -> DebtsTone.NEGATIVE
        else -> DebtsTone.NEUTRAL
    }
    return DebtsRowDisplay(
        parts = nonZero.map { balance ->
            DebtsBalancePart(
                amountMinor = abs(balance.balanceMinor),
                currencyCode = balance.currencyCode,
                textRes = if (balance.balanceMinor > 0L) {
                    R.string.debts_row_owed_to_me
                } else {
                    R.string.debts_row_owed_by_me
                },
            )
        },
        tone = tone,
    )
}

/** Инициалы для аватара: первые буквы первых двух слов, верхним регистром. */
fun counterpartyInitials(name: String): String = name
    .trim()
    .split(Regex("\\s+"))
    .mapNotNull { word -> word.firstOrNull(Char::isLetter)?.uppercase() }
    .take(2)
    .joinToString("")

/**
 * Ключ результата «создан контрагент» на savedStateHandle записи формы
 * долговой операции: экран создания пишет id, форма — подставляет его.
 */
const val DEBTS_PICKED_COUNTERPARTY_ID = "picked_counterparty_id"

/**
 * Подпись типа долговой операции для лент: «Долг выдан», «Погашение
 * получено»… null — тип не долговая операция, подписи у него нет.
 */
fun debtKindLabelRes(type: TxType): Int? = when (type) {
    TxType.DEBT_LEND -> R.string.debts_kind_lend
    TxType.DEBT_BORROW -> R.string.debts_kind_borrow
    TxType.DEBT_REPAY_IN -> R.string.debts_kind_repay_in
    TxType.DEBT_REPAY_OUT -> R.string.debts_kind_repay_out
    else -> null
}

/**
 * Направление денег в форме долговой операции: «отдаю» — деньги уходят
 * со счёта контрагенту, «получаю» — приходят от него. Хранится wire-строкой
 * в аргументах маршрута.
 */
enum class DebtDirection(val wire: String) {
    OUT("out"),
    IN("in"),
    ;

    companion object {
        fun fromWire(value: String?): DebtDirection = if (value == IN.wire) IN else OUT
    }
}

/** Валюта счёта — валюта его баланса; без баланса — валюта самого счёта. */
fun currencyCodeOfAccount(account: AccountEntity, balances: Map<String, BalanceEntity>): String = balances[account.balanceId]?.currencyCode ?: account.currencyCode
