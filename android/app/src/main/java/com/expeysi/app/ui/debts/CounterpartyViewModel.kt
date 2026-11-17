package com.expeysi.app.ui.debts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.R
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.CounterpartyDao
import com.expeysi.app.data.db.CounterpartyEntity
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.DebtAdjustmentDao
import com.expeysi.app.data.db.DebtAdjustmentEntity
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.data.sync.SyncEngine
import com.expeysi.app.domain.DebtAdjustmentKind
import com.expeysi.app.domain.DebtCalculator
import com.expeysi.app.domain.TxType
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
import kotlin.math.abs

/** Строка истории контрагента: долговая операция или корректировка. */
data class CounterpartyHistoryRow(
    /** Id записи: транзакции или корректировки. */
    val id: String,
    val isAdjustment: Boolean,
    val date: Long,
    /** Валюта суммы: у операции — валюта её счёта, у корректировки — своя. */
    val currencyCode: String,
    /** Знак вклада в остаток долга: цвет строки. */
    val deltaMinor: Long,
    /** Ресурс строк вида «Долг выдан», «Списание». */
    val labelRes: Int,
    val note: String,
    /** Срок возврата у выданного и взятого долга. */
    val dueDate: Long? = null,
    /** Срок прошёл раньше начала дня: чип «Просрочено». */
    val overdue: Boolean = false,
)

data class CounterpartyUiState(
    val counterparty: CounterpartyEntity? = null,
    /** Остатки «контрагент × валюта» сортированы по модулю по убыванию. */
    val balances: List<DebtCalculator.Balance> = emptyList(),
    val history: List<CounterpartyHistoryRow> = emptyList(),
    val currencySymbols: Map<String, String> = emptyMap(),
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

    /** Счёта и карта балансов: валюта операции резолвится по её счёту. */
    private val reference = combine(
        accountDao.observeActive(),
        balanceDao.observeAll(),
    ) { accounts, balances -> accounts to balances.associateBy { it.id } }

    val uiState: StateFlow<CounterpartyUiState> = combine(
        counterpartyDao.observeAll(),
        reference,
        currencyDao.observeAll(),
        transactionDao.observeActiveForCounterparty(counterpartyId),
        debtAdjustmentDao.observeActiveForCounterparty(counterpartyId),
    ) { counterparties, reference, currencies, transactions, adjustments ->
        val (accounts, balancesById) = reference
        val currencyByAccount = accounts.associate { it.id to currencyCodeOfAccount(it, balancesById) }
        // Начало дня сегодня: срок до его начала просрочен, сам день — нет.
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        CounterpartyUiState(
            counterparty = counterparties.firstOrNull { it.id == counterpartyId && !it.deleted },
            balances = DebtCalculator
                .balances(
                    DebtCalculator.transactionDeltas(transactions, { currencyByAccount[it].orEmpty() }) +
                        DebtCalculator.adjustmentDeltas(adjustments),
                )
                .sortedByDescending { abs(it.balanceMinor) },
            history = (
                buildTransactionRows(transactions, currencyByAccount, todayStart) +
                    buildAdjustmentRows(adjustments)
                ).sortedByDescending { it.date },
            currencySymbols = currencies.associate { it.code to it.symbol },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CounterpartyUiState(),
    )

    private fun buildTransactionRows(
        transactions: List<TransactionEntity>,
        currencyByAccount: Map<String, String>,
        todayStart: Long,
    ): List<CounterpartyHistoryRow> = transactions.mapNotNull { transaction ->
        val type = TxType.fromWire(transaction.type)
        val delta = DebtCalculator.transactionDelta(type, transaction.amountMinor) ?: return@mapNotNull null
        CounterpartyHistoryRow(
            id = transaction.id,
            isAdjustment = false,
            date = transaction.date,
            currencyCode = currencyByAccount[transaction.accountId].orEmpty(),
            deltaMinor = delta,
            labelRes = when (type) {
                TxType.DEBT_LEND -> R.string.debts_kind_lend
                TxType.DEBT_BORROW -> R.string.debts_kind_borrow
                TxType.DEBT_REPAY_IN -> R.string.debts_kind_repay_in
                TxType.DEBT_REPAY_OUT -> R.string.debts_kind_repay_out
                else -> return@mapNotNull null
            },
            note = transaction.note,
            dueDate = transaction.dueDate,
            overdue = transaction.dueDate != null &&
                (type == TxType.DEBT_LEND || type == TxType.DEBT_BORROW) &&
                DebtCalculator.isOverdue(transaction.dueDate, todayStart),
        )
    }

    private fun buildAdjustmentRows(
        adjustments: List<DebtAdjustmentEntity>,
    ): List<CounterpartyHistoryRow> = adjustments.map { adjustment ->
        CounterpartyHistoryRow(
            id = adjustment.id,
            isAdjustment = true,
            date = adjustment.date,
            currencyCode = adjustment.currencyCode,
            deltaMinor = adjustment.amountMinor,
            labelRes = if (DebtAdjustmentKind.fromWire(adjustment.kind) == DebtAdjustmentKind.WRITE_OFF) {
                R.string.debts_kind_write_off
            } else {
                R.string.debts_action_opening_balance
            },
            note = adjustment.note,
        )
    }

    private val _deleteState = MutableStateFlow<DeleteRequest?>(null)
    val deleteState: StateFlow<DeleteRequest?> = _deleteState.asStateFlow()

    /** Клик по «Удалить»: сначала проверка — история или остаток запрещают. */
    fun requestDelete() {
        viewModelScope.launch {
            val blocked = transactionDao.hasActiveForCounterparty(counterpartyId) ||
                debtAdjustmentDao.hasActiveForCounterparty(counterpartyId) ||
                uiState.value.balances.any { it.balanceMinor != 0L }
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
