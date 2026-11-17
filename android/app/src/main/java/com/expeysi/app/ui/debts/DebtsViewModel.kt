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
import com.expeysi.app.domain.ProfitAndLoss
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs

/**
 * Состояние списков долгов: вклады операций и корректировок, идентификаторы
 * просроченных контрагентов — считается в одном combine для лент и строк.
 */
private data class DebtLedger(
    val deltas: List<DebtCalculator.Delta>,
    val overdueCounterpartyIds: Set<String>,
)

@HiltViewModel
class DebtsViewModel @Inject constructor(
    accountDao: AccountDao,
    balanceDao: BalanceDao,
    counterpartyDao: CounterpartyDao,
    transactionDao: TransactionDao,
    debtAdjustmentDao: DebtAdjustmentDao,
    currencyDao: CurrencyDao,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _sort = MutableStateFlow(DebtsSort.NAME)
    private val _sideFilter = MutableStateFlow(DebtsSideFilter.ALL)

    fun selectSort(sort: DebtsSort) {
        _sort.value = sort
    }

    fun selectSideFilter(filter: DebtsSideFilter) {
        _sideFilter.value = filter
    }

    /**
     * Вклады всех долговых операций и корректировок в остатки. Валюта
     * операции резолвится по счёту, корректировка несёт валюту сама; здесь
     * же считаются идентификаторы просроченных контрагентов — даты возврата
     * живут на долговых операциях.
     */
    private val ledger = combine(
        transactionDao.observeActive(),
        debtAdjustmentDao.observeAll(),
        accountDao.observeActive(),
        balanceDao.observeAll(),
    ) { transactions, adjustments, accounts, balances ->
        val balancesById = balances.associateBy { it.id }
        val currencyByAccount = accounts.associate { it.id to currencyCodeOfAccount(it, balancesById) }
        val deltas = DebtCalculator.transactionDeltas(transactions, { currencyByAccount[it].orEmpty() }) +
            DebtCalculator.adjustmentDeltas(adjustments)
        DebtLedger(
            deltas = deltas,
            overdueCounterpartyIds = overdueCounterpartyIds(
                transactions = transactions,
                balances = DebtCalculator.balances(deltas),
                todayStartMillis = ProfitAndLoss.todayStartMillis(zone),
                currencyCodeOfAccount = { accountId -> currencyByAccount[accountId].orEmpty() },
            ),
        )
    }

    val uiState: StateFlow<DebtsUiState> = combine(
        counterpartyDao.observeAll(),
        ledger,
        currencyDao.observeAll(),
        _sort,
        _sideFilter,
    ) { counterparties, ledger, currencies, sort, sideFilter ->
        val balancesByCounterparty = DebtCalculator.balances(ledger.deltas).groupBy { it.counterpartyId }
        val currencySymbols = currencies.associate { it.code to it.symbol }
        val rows = counterparties
            .filterNot { it.deleted }
            .map { counterparty ->
                DebtsRow(
                    counterparty = counterparty,
                    balances = balancesByCounterparty[counterparty.id].orEmpty()
                        .sortedByDescending { abs(it.balanceMinor) },
                    overdue = counterparty.id in ledger.overdueCounterpartyIds,
                )
            }
        val active = rows.filterNot { it.counterparty.archived }
        val (open, settled) = active.partition { !debtsRowDisplay(it.balances).settled }
        // Фильтр стороны применяется к открытым строкам: секции «Закрытые»
        // и «Архив» остаются как есть. Сортировка — внутри каждой секции.
        DebtsUiState(
            rows = sortDebtsRows(filterDebtsRows(open, sideFilter), sort),
            settled = sortDebtsRows(settled, sort),
            archived = sortDebtsRows(rows.filter { it.counterparty.archived }, sort),
            totals = debtsTotals(rows),
            currencySymbols = currencySymbols,
            sideFilter = sideFilter,
            sort = sort,
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
}
