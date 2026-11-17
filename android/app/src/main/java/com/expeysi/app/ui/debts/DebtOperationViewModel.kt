package com.expeysi.app.ui.debts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.AuthRepository
import com.expeysi.app.data.LastTransactionSelectionStore
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CounterpartyDao
import com.expeysi.app.data.db.CounterpartyEntity
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.DebtAdjustmentDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.data.sync.SyncEngine
import com.expeysi.app.domain.BalanceCalculator
import com.expeysi.app.domain.DebtCalculator
import com.expeysi.app.domain.Money
import com.expeysi.app.domain.TxSum
import com.expeysi.app.domain.TxType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * Единая форма долговых операций: выдать долг, взять в долг, погасить.
 * Kind записи выводится из направления денег и текущего знака остатка
 * ([DebtCalculator.kindFor]); правка сохраняет исходный kind, пока
 * направление не переведено. Перепогашение, уводящее остаток через ноль
 * в другую сторону, подтверждается предупреждением ([FlipPreview]).
 */
@HiltViewModel
class DebtOperationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountDao: AccountDao,
    private val balanceDao: BalanceDao,
    currencyDao: CurrencyDao,
    counterpartyDao: CounterpartyDao,
    private val transactionDao: TransactionDao,
    debtAdjustmentDao: DebtAdjustmentDao,
    private val selectionStore: LastTransactionSelectionStore,
    private val syncEngine: SyncEngine,
    authRepository: AuthRepository,
) : ViewModel() {

    /** Правка записи: null — создание. */
    private val editId: String? = savedStateHandle["transactionId"]

    /** Контрагент маршрута: пустой — выбор в форме. */
    private val routeCounterpartyId: String = savedStateHandle.get<String>("counterpartyId").orEmpty()

    /** Направление маршрута: применяется при создании. */
    private val routeDirection: DebtDirection = DebtDirection.fromWire(savedStateHandle.get<String>("direction"))

    /** Фиксированная валюта («Погасить»): счета правятся только в ней. */
    private val fixedCurrency: String = savedStateHandle.get<String>("currency").orEmpty()

    /** Сумма маршрута: префил погашения или пустая строка. */
    private val routeAmount: String = savedStateHandle.get<String>("amount").orEmpty()

    private val userId: String = authRepository.userId ?: "local"

    /** Исходная запись при правке: kind и срок живут до смены направления. */
    private var original: TransactionEntity? = null
    private var originalDirection: DebtDirection? = null

    val isEdit: Boolean = editId != null

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _amountText = MutableStateFlow("")
    val amountText: StateFlow<String> = _amountText.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<String?>(null)
    val selectedAccountId: StateFlow<String?> = _selectedAccountId.asStateFlow()

    /** Выбранный контрагент: null — ещё не выбран. */
    private val _counterpartyId = MutableStateFlow<String?>(null)
    val counterpartyId: StateFlow<String?> = _counterpartyId.asStateFlow()

    private val _direction = MutableStateFlow(DebtDirection.OUT)
    val direction: StateFlow<DebtDirection> = _direction.asStateFlow()

    private val _dueDate = MutableStateFlow<LocalDate?>(null)
    val dueDate: StateFlow<LocalDate?> = _dueDate.asStateFlow()

    private val _date = MutableStateFlow(LocalDate.now(zone))
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _time = MutableStateFlow(LocalTime.now(zone).withSecond(0).withNano(0))
    val time: StateFlow<LocalTime> = _time.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** Контрагент задан маршрутом или правится: строка выбора скрыта. */
    val counterpartyLocked: Boolean = isEdit || routeCounterpartyId.isNotEmpty()

    /** Тексты предупреждения о перепогашении: было и станет, в минорных единицах. */
    data class FlipPreview(val before: Long, val after: Long)

    private val _showFlipWarning = MutableStateFlow<FlipPreview?>(null)
    val showFlipWarning: StateFlow<FlipPreview?> = _showFlipWarning.asStateFlow()

    /** Сумма и kind к сохранению; собирается при клике «Сохранить». */
    private data class PendingSave(
        val type: TxType,
        val amountMinor: Long,
        val counterpartyId: String,
        val accountId: String,
    )

    private var pending: PendingSave? = null

    /** Активные счета для шторки выбора. */
    val accounts: StateFlow<List<AccountEntity>> = accountDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Валюты счёта — валюта его баланса; карта балансов по id. */
    val balances: StateFlow<Map<String, BalanceEntity>> = balanceDao.observeAll()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Активные контрагенты для шторки выбора. */
    val counterparties: StateFlow<List<CounterpartyEntity>> = counterpartyDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Символы валют справочника: код → символ. */
    val currencySymbols: StateFlow<Map<String, String>> = currencyDao.observeAll()
        .map { list -> list.associate { it.code to it.symbol } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Текущие остатки счетов для подписей строк выбора счёта. */
    val accountBalances: StateFlow<Map<String, Long>> = combine(
        accountDao.observeActive(),
        transactionDao.observeActive(),
    ) { accounts, transactions ->
        accounts.associate { account ->
            val sums = transactions
                .filter { it.accountId == account.id }
                .map { TxSum(TxType.fromWire(it.type), it.amountMinor) }
            account.id to BalanceCalculator.balanceMinor(account.openingBalanceMinor, sums)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Счёта и карта балансов: валюты операций резолвятся по их счетам. */
    private val reference = combine(
        accountDao.observeActive(),
        balanceDao.observeAll(),
    ) { accounts, balances -> accounts to balances.associateBy { it.id } }

    /**
     * Остаток пары «выбранный контрагент × валюта выбранного счёта» без
     * правимой записи: kind, срок и предупреждение перепогашения считаются
     * по нему. Сюда входят и корректировки контрагента. Без контрагента
     * или счёта — ноль: форма ещё собирается.
     */
    val liveBalance: StateFlow<Long> = combine(
        reference,
        _counterpartyId,
        _selectedAccountId,
        transactionDao.observeActive(),
        debtAdjustmentDao.observeAll(),
    ) { reference, counterpartyId, selectedAccountId, transactions, adjustments ->
        val (accounts, balancesById) = reference
        val currencyByAccount = accounts.associate { it.id to currencyCodeOfAccount(it, balancesById) }
        val currencyCode = accounts.firstOrNull { it.id == selectedAccountId }
            ?.let { currencyCodeOfAccount(it, balancesById) }
            .orEmpty()
        if (currencyCode.isEmpty() || counterpartyId.isNullOrEmpty()) {
            0L
        } else {
            DebtCalculator.balanceOf(
                DebtCalculator.transactionDeltas(
                    transactions.filterNot { it.id == editId },
                    { currencyByAccount[it].orEmpty() },
                ) + DebtCalculator.adjustmentDeltas(adjustments),
                counterpartyId,
                currencyCode,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        if (editId != null) {
            viewModelScope.launch {
                transactionDao.byId(editId)?.let { tx ->
                    original = tx
                    val type = TxType.fromWire(tx.type)
                    _direction.value = if (DebtCalculator.isOutgoing(type)) DebtDirection.OUT else DebtDirection.IN
                    originalDirection = _direction.value
                    _amountText.value = Money.formatInput(Money.formatAuto(tx.amountMinor))
                    _selectedAccountId.value = tx.accountId
                    _counterpartyId.value = tx.counterpartyId
                    _date.value = Instant.ofEpochMilli(tx.date).atZone(zone).toLocalDate()
                    _time.value = Instant.ofEpochMilli(tx.date).atZone(zone).toLocalTime().withSecond(0).withNano(0)
                    _note.value = tx.note
                    _dueDate.value = tx.dueDate?.let { due ->
                        Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                    }
                }
            }
        } else {
            _direction.value = routeDirection
            _counterpartyId.value = routeCounterpartyId.ifEmpty { null }
            val amount = Money.parseToMinor(routeAmount)
            if (amount != null && amount > 0) {
                _amountText.value = Money.formatInput(Money.formatAuto(amount))
            }
            viewModelScope.launch {
                // Счёт: фиксированная валюта сужает выбор; «последний
                // использованный» берётся, только если он из нужной валюты.
                val balancesById = balanceDao.observeAll().first().associateBy { it.id }
                val candidates = accountDao.listActive()
                    .filter { fixedCurrency.isEmpty() || currencyCodeOfAccount(it, balancesById) == fixedCurrency }
                val lastUsed = selectionStore.accountId(userId)
                    ?.let { id -> accountDao.byId(id) }
                    ?.takeIf { account -> candidates.any { it.id == account.id } }
                _selectedAccountId.value = lastUsed?.id ?: candidates.firstOrNull()?.id
            }
        }
    }

    /** Счета для шторки: валюта фиксирована — остаются только её счета. */
    fun pickableAccounts(): List<AccountEntity> = if (fixedCurrency.isEmpty()) {
        accounts.value
    } else {
        accounts.value.filter { currencyCodeOfAccount(it, balances.value) == fixedCurrency }
    }

    fun onAccountSelect(id: String) {
        _selectedAccountId.value = id
        if (fixedCurrency.isEmpty()) selectionStore.selectAccount(userId, id)
    }

    fun onCounterpartySelect(id: String) {
        _counterpartyId.value = id
    }

    fun onAmountChange(value: String) {
        _amountText.value = Money.formatInput(value)
    }

    /** Смена направления: счёт, сумма и срок остаются, kind пересчитается. */
    fun onDirectionChange(value: DebtDirection) {
        _direction.value = value
    }

    fun onDatePick(utcMidnightMillis: Long) {
        _date.value = Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    }

    fun onTimePick(hour: Int, minute: Int) {
        _time.value = LocalTime.of(hour, minute)
    }

    fun onDueDatePick(utcMidnightMillis: Long) {
        _dueDate.value = Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    }

    fun clearDueDate() {
        _dueDate.value = null
    }

    fun onNoteChange(value: String) {
        _note.value = value
    }

    /**
     * Kind записи к сохранению: правка без смены направления хранит
     * исходный kind, иначе kind выводится из направления и знака остатка.
     */
    fun operationKind(liveBalanceMinor: Long): TxType {
        val original = original
        if (isEdit && original != null && _direction.value == originalDirection) {
            return TxType.fromWire(original.type)
        }
        return DebtCalculator.kindFor(_direction.value == DebtDirection.OUT, liveBalanceMinor)
    }

    /** Строка «Срок возврата» видна: запись углубляет долг или правка с уже заданным сроком. */
    fun dueDateVisible(liveBalanceMinor: Long): Boolean {
        val kind = operationKind(liveBalanceMinor)
        if (kind == TxType.DEBT_LEND || kind == TxType.DEBT_BORROW) return true
        // Правка без смены направления не должна терять сохранённый срок.
        return isEdit && original?.dueDate != null && _direction.value == originalDirection
    }

    /** Разрешено сохранение: сумма, счёт и контрагент заданы, валюта не нарушена. */
    fun canSave(liveBalanceMinor: Long): Boolean {
        val amount = Money.parseToMinor(_amountText.value) ?: return false
        if (amount <= 0) return false
        val account = accounts.value.firstOrNull { it.id == _selectedAccountId.value } ?: return false
        if (_counterpartyId.value.isNullOrEmpty()) return false
        if (fixedCurrency.isNotEmpty() && currencyCodeOfAccount(account, balances.value) != fixedCurrency) return false
        // kind и видимость срока считаются той же функцией, что и сохранение.
        operationKind(liveBalanceMinor)
        return true
    }

    /**
     * Проверка перепогашения перед записью: после операции остаток уходит
     * через ноль в другую сторону — сначала предупреждение.
     */
    fun save() {
        val before = liveBalance.value
        if (!canSave(before)) return
        val amount = Money.parseToMinor(_amountText.value) ?: return
        val counterpartyId = _counterpartyId.value ?: return
        val accountId = _selectedAccountId.value ?: return
        val type = operationKind(before)
        val delta = DebtCalculator.transactionDelta(type, amount) ?: 0L
        val after = before + delta
        if (DebtCalculator.flipsSign(before, after)) {
            _showFlipWarning.value = FlipPreview(before, after)
            pending = PendingSave(type, amount, counterpartyId, accountId)
            return
        }
        persist(type, amount, counterpartyId, accountId)
    }

    /** Нажали «Сохранить всё равно»: записываем отложенную операцию. */
    fun confirmFlip() {
        val confirmed = pending
        _showFlipWarning.value = null
        if (confirmed != null) {
            persist(confirmed.type, confirmed.amountMinor, confirmed.counterpartyId, confirmed.accountId)
        }
    }

    fun dismissFlip() {
        _showFlipWarning.value = null
        pending = null
    }

    private fun persist(type: TxType, amountMinor: Long, counterpartyId: String, accountId: String) {
        viewModelScope.launch {
            // Долговая запись: категория и ноги перевода ей не нужны, срок —
            // только у выданного и взятого долга; у погашений его нет.
            val deepensDebt = type == TxType.DEBT_LEND || type == TxType.DEBT_BORROW
            val dueMillis = if (deepensDebt) {
                _dueDate.value?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
            } else {
                null
            }
            val transaction = TransactionEntity(
                id = editId ?: UUID.randomUUID().toString(),
                type = type.wire,
                amountMinor = amountMinor,
                accountId = accountId,
                categoryId = "",
                date = dateMillis(),
                note = _note.value.trim(),
                counterpartyId = counterpartyId,
                dueDate = dueMillis,
                updatedAt = System.currentTimeMillis(),
            )
            transactionDao.upsert(transaction)
            syncEngine.uploadTransaction(transaction)
            _saved.value = true
        }
    }

    private fun dateMillis(): Long = LocalDateTime.of(_date.value, _time.value)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    fun delete() {
        viewModelScope.launch {
            val tx = original ?: return@launch
            val deleted = tx.copy(deleted = true, updatedAt = System.currentTimeMillis())
            transactionDao.upsert(deleted)
            syncEngine.uploadTransaction(deleted)
            _saved.value = true
        }
    }
}
