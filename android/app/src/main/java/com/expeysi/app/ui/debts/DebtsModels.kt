package com.expeysi.app.ui.debts

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CounterpartyEntity
import com.expeysi.app.domain.DebtCalculator

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
    val archived: List<DebtsRow>,
    val totals: DebtsTotals,
    val currencySymbols: Map<String, String>,
)

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
