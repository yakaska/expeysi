package com.expeysi.app.ui.debts

import com.expeysi.app.R
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CounterpartyEntity
import com.expeysi.app.domain.DebtCalculator
import com.expeysi.app.domain.TxType
import kotlin.math.abs

/** Строка списка долгов: контрагент и его остатки по валютам. */
data class DebtsRow(
    val counterparty: CounterpartyEntity,
    /** Остатки «контрагент × валюта», отсортированы по модулю по убыванию. */
    val balances: List<DebtCalculator.Balance>,
)

/** Сводка всех долгов: по каждой валюте суммарно на каждой стороне. */
data class DebtsTotals(
    /** Контрагенты должны: сумма положительных остатков. */
    val owedToMe: Map<String, Long>,
    /** Должен я: сумма модулей отрицательных остатков. */
    val owedByMe: Map<String, Long>,
)

data class DebtsUiState(
    val rows: List<DebtsRow>,
    /** Закрытые контрагенты: ноль во всех валютах, показываются в секции «Закрытые». */
    val settled: List<DebtsRow>,
    val archived: List<DebtsRow>,
    val totals: DebtsTotals,
    val currencySymbols: Map<String, String>,
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
fun currencyCodeOfAccount(account: AccountEntity, balances: Map<String, BalanceEntity>): String =
    balances[account.balanceId]?.currencyCode ?: account.currencyCode
