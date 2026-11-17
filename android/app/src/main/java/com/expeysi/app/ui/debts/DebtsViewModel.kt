package com.expeysi.app.ui.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.CounterpartyDao
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.DebtAdjustmentDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.domain.DebtCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class DebtsViewModel @Inject constructor(
    accountDao: AccountDao,
    balanceDao: BalanceDao,
    counterpartyDao: CounterpartyDao,
    transactionDao: TransactionDao,
    debtAdjustmentDao: DebtAdjustmentDao,
    currencyDao: CurrencyDao,
) : ViewModel() {

    /**
     * Вклады всех долговых операций и корректировок в остатки. Валюта
     * операции резолвится по счёту, корректировка несёт валюту сама.
     */
    private val deltas = combine(
        transactionDao.observeActive(),
        debtAdjustmentDao.observeAll(),
        accountDao.observeActive(),
        balanceDao.observeAll(),
    ) { transactions, adjustments, accounts, balances ->
        val balancesById = balances.associateBy { it.id }
        val currencyByAccount = accounts.associate { it.id to currencyCodeOfAccount(it, balancesById) }
        DebtCalculator.transactionDeltas(transactions, { currencyByAccount[it].orEmpty() }) +
            DebtCalculator.adjustmentDeltas(adjustments)
    }

    val uiState: StateFlow<DebtsUiState> = combine(
        counterpartyDao.observeAll(),
        deltas,
        currencyDao.observeAll(),
    ) { counterparties, deltas, currencies ->
        val balancesByCounterparty = DebtCalculator.balances(deltas).groupBy { it.counterpartyId }
        val currencySymbols = currencies.associate { it.code to it.symbol }
        val rows = counterparties
            .filterNot { it.deleted }
            .map { counterparty ->
                DebtsRow(
                    counterparty = counterparty,
                    balances = balancesByCounterparty[counterparty.id].orEmpty()
                        .sortedByDescending { abs(it.balanceMinor) },
                )
            }
        val active = rows.filterNot { it.counterparty.archived }
        val (open, settled) = active.partition { !debtsRowDisplay(it.balances).settled }
        // Всего по всем контрагентам, включая архивных и закрытых: секция
        // скрывает строку, но не отменяет сам долг.
        DebtsUiState(
            rows = open,
            settled = settled,
            archived = rows.filter { it.counterparty.archived },
            totals = DebtsTotals(
                owedToMe = totals(rows, positive = true),
                owedByMe = totals(rows, positive = false),
            ),
            currencySymbols = currencySymbols,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DebtsUiState(
            rows = emptyList(),
            settled = emptyList(),
            archived = emptyList(),
            totals = DebtsTotals(emptyMap(), emptyMap()),
            currencySymbols = emptyMap(),
        ),
    )

    private fun totals(rows: List<DebtsRow>, positive: Boolean): Map<String, Long> = rows
        .asSequence()
        .flatMap { it.balances }
        .filter { if (positive) it.balanceMinor > 0 else it.balanceMinor < 0 }
        .groupBy { it.currencyCode }
        .mapValues { (_, list) -> list.sumOf { abs(it.balanceMinor) } }
}
