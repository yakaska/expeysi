package com.expeysi.app.ui.debts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.CounterpartyDao
import com.expeysi.app.data.db.CounterpartyEntity
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.DebtAdjustmentDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.sync.SyncEngine
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtCurrencySummary
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtHistoryRow
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtOp
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtOpKind
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.adjustmentOp
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.fold
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.transactionOp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Суммарное UI-состояние экрана контрагента. */
data class CounterpartyUiState(
    val counterparty: CounterpartyEntity? = null,
    /** Сводки «контрагент × валюта» сортированы по модулю по убыванию. */
    val summaries: List<DebtCurrencySummary> = emptyList(),
    /** История: по убыванию даты, с бегущим остатком после каждой операции. */
    val history: List<DebtHistoryRow> = emptyList(),
    val currencySymbols: Map<String, String> = emptyMap(),
    /** Дата первой операции: подпись «Долг с…» под именем скрыта без истории. */
    val firstDate: Long? = null,
    /** Дата полного закрытия долга; null — долг открыт или операций нет. */
    val closingDate: Long? = null,
)

/**
 * Событие «долг закрыт»: последняя обнулившая остаток операция,
 * которую «Отменить» в снекбаре удаляет, возвращая прежний остаток.
 */
data class DebtClosedEvent(
    val opId: String,
    val isAdjustment: Boolean,
)

@HiltViewModel
class CounterpartyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val counterpartyDao: CounterpartyDao,
    private val transactionDao: TransactionDao,
    private val debtAdjustmentDao: DebtAdjustmentDao,
    accountDao: AccountDao,
    balanceDao: BalanceDao,
    currencyDao: CurrencyDao,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val counterpartyId: String = checkNotNull(savedStateHandle["counterpartyId"])

    private val zone: ZoneId = ZoneId.systemDefault()

    /** Виды операций, способные закрыть долг: для снекбара «Долг закрыт». */
    private val closingKinds = setOf(
        DebtOpKind.REPAY_IN,
        DebtOpKind.REPAY_OUT,
        DebtOpKind.WRITE_OFF,
    )

    /** Счёта и карта балансов: валюта операции резолвится по её счёту. */
    private val reference = combine(
        accountDao.observeActive(),
        balanceDao.observeAll(),
    ) { accounts, balances -> accounts to balances.associateBy { it.id } }

    /** Остатки пар в предыдущей свёртке: переход «не ноль → ноль» даёт событие. */
    private var previousBalances: Map<String, Long>? = null

    private val _debtClosed = MutableStateFlow<DebtClosedEvent?>(null)
    val debtClosed: StateFlow<DebtClosedEvent?> = _debtClosed.asStateFlow()

    val uiState: StateFlow<CounterpartyUiState> = combine(
        counterpartyDao.observeAll(),
        reference,
        currencyDao.observeAll(),
        transactionDao.observeActiveForCounterparty(counterpartyId),
        debtAdjustmentDao.observeActiveForCounterparty(counterpartyId),
    ) { counterparties, reference, currencies, transactions, adjustments ->
        val (accounts, balancesById) = reference
        val currencyByAccount = accounts.associate { it.id to currencyCodeOfAccount(it, balancesById) }
        val ops = transactions.mapNotNull { transactionOp(it, currencyByAccount[it.accountId].orEmpty()) } +
            adjustments.map { adjustmentOp(it) }
        val fold = fold(ops, LocalDate.now(zone), zone)
        // Переход остатка из не-нуля в ноль: последнее событие — по новой сводке.
        val newBalances = fold.summaries.associate { it.currencyCode to it.balanceMinor }
        val closed = previousBalances
            ?.entries
            ?.firstOrNull { (code, before) -> before != 0L && newBalances[code] == 0L }
            ?.let { (code, _) ->
                fold.summaries
                    .firstOrNull { it.currencyCode == code }
                    ?.closingOp
                    ?.takeIf { op -> op.kind in closingKinds }
                    ?.let { DebtClosedEvent(it.id, it.isAdjustment) }
            }
        if (closed != null) _debtClosed.value = closed
        previousBalances = newBalances

        CounterpartyUiState(
            counterparty = counterparties.firstOrNull { it.id == counterpartyId && !it.deleted },
            summaries = fold.summaries,
            history = fold.rows,
            currencySymbols = currencies.associate { it.code to it.symbol },
            firstDate = fold.firstDate,
            closingDate = fold.closingDate,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CounterpartyUiState(),
    )

    fun consumeDebtClosed() {
        _debtClosed.value = null
    }

    /** Отмена закрытия: мягкое удаление обнулившей операции через обычный путь. */
    fun undoDebtClose() {
        val event = _debtClosed.value ?: return
        viewModelScope.launch {
            if (event.isAdjustment) {
                val adjustment = debtAdjustmentDao.byId(event.opId) ?: return@launch
                val deleted = adjustment.copy(deleted = true, updatedAt = System.currentTimeMillis())
                debtAdjustmentDao.upsert(deleted)
                syncEngine.uploadDebtAdjustment(deleted)
            } else {
                val transaction = transactionDao.byId(event.opId) ?: return@launch
                val deleted = transaction.copy(deleted = true, updatedAt = System.currentTimeMillis())
                transactionDao.upsert(deleted)
                syncEngine.uploadTransaction(deleted)
            }
        }
    }

    private val _deleteState = MutableStateFlow<DeleteRequest?>(null)
    val deleteState: StateFlow<DeleteRequest?> = _deleteState.asStateFlow()

    /** Клик по «Удалить»: сначала проверка — история или остаток запрещают. */
    fun requestDelete() {
        viewModelScope.launch {
            val blocked = transactionDao.hasActiveForCounterparty(counterpartyId) ||
                debtAdjustmentDao.hasActiveForCounterparty(counterpartyId) ||
                uiState.value.summaries.any { it.balanceMinor != 0L }
            _deleteState.value = DeleteRequest(blocked = blocked)
        }
    }

    data class DeleteRequest(val blocked: Boolean)

    fun dismissDelete() {
        _deleteState.value = null
    }

    /** Удаление разрешено: мягкое удаление контрагента и синхронизация. */
    fun confirmDelete() {
        _deleteState.value = null
        viewModelScope.launch {
            val current = uiState.value.counterparty ?: return@launch
            val deleted = current.copy(deleted = true, updatedAt = System.currentTimeMillis())
            counterpartyDao.upsert(deleted)
            syncEngine.uploadCounterparty(deleted)
        }
    }

    fun toggleArchive() {
        viewModelScope.launch {
            val current = uiState.value.counterparty ?: return@launch
            val updated = current.copy(archived = !current.archived, updatedAt = System.currentTimeMillis())
            counterpartyDao.upsert(updated)
            syncEngine.uploadCounterparty(updated)
        }
    }
}
