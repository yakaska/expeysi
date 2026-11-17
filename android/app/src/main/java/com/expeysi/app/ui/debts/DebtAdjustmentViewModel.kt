package com.expeysi.app.ui.debts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.DebtAdjustmentDao
import com.expeysi.app.data.db.DebtAdjustmentEntity
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.sync.SyncEngine
import com.expeysi.app.domain.Currency
import com.expeysi.app.domain.DebtAdjustmentKind
import com.expeysi.app.domain.DebtCalculator
import com.expeysi.app.domain.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

/**
 * Мини-форма корректировки долга без движения денег по счёту: списание
 * (в сторону нуля, по умолчанию полное, частичное разрешено, сумма не больше
 * остатка) или начальный остаток (любой знак). Валюта списания — только пары
 * с остатком, начального остатка — любая из справочника.
 */
@HiltViewModel
class DebtAdjustmentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debtAdjustmentDao: DebtAdjustmentDao,
    balanceDao: BalanceDao,
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    currencyDao: CurrencyDao,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    /** Правка корректировки: null — создание. */
    private val editId: String? = savedStateHandle["adjustmentId"]

    /** Контрагент маршрута при создании; правка подставляет из записи. */
    private val counterpartyId = MutableStateFlow(savedStateHandle.get<String>("counterpartyId").orEmpty())

    /** Kind маршрута при создании; правка подставляет из записи. */
    private val kind = MutableStateFlow(DebtAdjustmentKind.fromWire(savedStateHandle.get<String>("kind") ?: ""))

    /** Валюта маршрута: у списания уже выбрана нажатой парой в экране контрагента. */
    private val routeCurrency: String = savedStateHandle.get<String>("currency").orEmpty()

    /** Остаток маршрута: списание по умолчанию полное — поле уже содержит его. */
    private val routeRemaining: Long = savedStateHandle.get<String>("remaining")?.toLongOrNull() ?: 0L

    val isEdit: Boolean = editId != null

    /** Списание — не начальный остаток: форма и правила сумм различаются. */
    val writeOff: Boolean
        get() = kind.value == DebtAdjustmentKind.WRITE_OFF

    private val zone: ZoneId = ZoneId.systemDefault()

    /** Ненулевой остаток контрагента в одной валюте. */
    data class CounterpartyBalance(val currencyCode: String, val balanceMinor: Long)

    /**
     * Остатки контрагента без правимой записи: лимит списания считается
     * «до» неё; для создания это весь остаток пары.
     */
    private val reference = combine(
        accountDao.observeActive(),
        balanceDao.observeAll(),
        transactionDao.observeActive(),
        debtAdjustmentDao.observeAll(),
        counterpartyId,
    ) { accounts, balances, transactions, adjustments, counterpartyId ->
        val balancesById = balances.associateBy { it.id }
        val currencyByAccount = accounts.associate { it.id to currencyCodeOfAccount(it, balancesById) }
        DebtCalculator.balances(
            DebtCalculator.transactionDeltas(
                transactions,
                { currencyByAccount[it].orEmpty() },
            ) + DebtCalculator.adjustmentDeltas(
                adjustments.filterNot { it.id == editId },
            ),
        )
            .filter { it.counterpartyId == counterpartyId && it.balanceMinor != 0L }
            .sortedByDescending { abs(it.balanceMinor) }
            .map { CounterpartyBalance(it.currencyCode, it.balanceMinor) }
    }

    data class AdjustmentUiState(
        /** Варианты выбора валюты: без дубликатов, у списания — только с остатком. */
        val availableCurrencies: List<String> = emptyList(),
        val balances: List<CounterpartyBalance> = emptyList(),
    )

    val uiState: StateFlow<AdjustmentUiState> = combine(
        currencyDao.observeAll(),
        reference,
        kind,
    ) { currencies, balances, kind ->
        AdjustmentUiState(
            // Списанию — только валюты с остатком, иначе списывать нечего;
            // начальному остатку — весь справочник, использованные сверху.
            availableCurrencies = if (kind == DebtAdjustmentKind.WRITE_OFF) {
                balances.map { it.currencyCode }
            } else {
                val used = balances.map { it.currencyCode }.toSet()
                currencies
                    .map { it.code }
                    .sortedBy { code ->
                        when {
                            code in used -> 0
                            code == Currency.DEFAULT -> 1
                            else -> 2
                        }
                    }
            },
            balances = balances,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AdjustmentUiState(),
    )

    private val _amountText = MutableStateFlow("")
    val amountText: StateFlow<String> = _amountText.asStateFlow()

    private val _currencyCode = MutableStateFlow("")
    val currencyCode: StateFlow<String> = _currencyCode.asStateFlow()

    private val _date = MutableStateFlow(LocalDate.now(zone))
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    /**
     * Время исходной корректировки при правке: список историй сортирует
     * записи по точному времени, полночь выбранного дня утопила бы новую
     * корректировку под операциями того же дня. null — создание.
     */
    private var timeOfDay: LocalTime? = null

    /** Показывать списание в отчётах как доход или расход; у начального остатка всегда выкл. */
    val countInStatsAllowed: Boolean
        get() = writeOff

    private val _countInStats = MutableStateFlow(false)
    val countInStats: StateFlow<Boolean> = _countInStats.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** Символы валют справочника для подписи и поля суммы. */
    val currencySymbols: StateFlow<Map<String, String>> = currencyDao.observeAll()
        .map { list -> list.associate { it.code to it.symbol } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Остаток пары выбранной валюты без правимой записи: лимит списания. */
    val remaining: StateFlow<Long> = uiState.combine(_currencyCode) { state, code ->
        state.balances.firstOrNull { it.currencyCode == code }?.balanceMinor ?: 0L
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        if (editId != null) {
            viewModelScope.launch {
                val loaded = debtAdjustmentDao.byId(editId) ?: return@launch
                counterpartyId.value = loaded.counterpartyId
                val loadedKind = DebtAdjustmentKind.fromWire(loaded.kind)
                kind.value = loadedKind
                _amountText.value = Money.formatInput(
                    if (loadedKind == DebtAdjustmentKind.WRITE_OFF) {
                        // Списание хранит дельту в сторону нуля: в поле — её модуль.
                        Money.formatAuto(abs(loaded.amountMinor))
                    } else {
                        // Начальный остаток хранит знак: минус значит мой долг.
                        Money.formatAuto(loaded.amountMinor)
                    },
                )
                _currencyCode.value = loaded.currencyCode
                _date.value = Instant.ofEpochMilli(loaded.date).atZone(zone).toLocalDate()
                timeOfDay = Instant.ofEpochMilli(loaded.date).atZone(zone).toLocalTime().withSecond(0).withNano(0)
                _note.value = loaded.note
                _countInStats.value = loaded.countInStats
            }
        } else if (kind.value == DebtAdjustmentKind.WRITE_OFF) {
            // Списание из экрана контрагента: валюта уже выбрана, поле —
            // полный остаток; пользователь вольён уменьшить сумму.
            _currencyCode.value = routeCurrency
            if (routeRemaining != 0L) {
                _amountText.value = Money.formatInput(Money.formatAuto(abs(routeRemaining)))
            }
        }
        // В остальных случаях поле валюты пустое до выбора: экран вызывает
        // [ensureCurrencySelected], когда варианты загружены.
    }

    /** Подставляет валюту, когда варианты выбора уже в ui-состоянии. */
    fun ensureCurrencySelected() {
        if (_currencyCode.value.isNotEmpty()) return
        _currencyCode.value = uiState.value.availableCurrencies.firstOrNull().orEmpty()
    }

    fun onAmountChange(value: String) {
        _amountText.value = Money.formatInput(value)
    }

    fun onCurrencySelect(code: String) {
        _currencyCode.value = code
    }

    fun onDatePick(utcMidnightMillis: Long) {
        _date.value = Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    }

    fun onNoteChange(value: String) {
        _note.value = value
    }

    fun onCountInStatsChange(value: Boolean) {
        _countInStats.value = value
    }

    /** Сумма больше модуля остатка: списывать нечего, переписать нельзя. */
    val overLimit: StateFlow<Boolean> = combine(
        _amountText,
        remaining,
    ) { amountText, liveRemaining ->
        val amount = Money.parseToMinor(amountText)
        amount != null && amount > abs(liveRemaining)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Разрешено сохранение: сумма валидна и по правилам вида корректировки —
     * у списания она положительна и не превышает остаток.
     */
    val canSave: StateFlow<Boolean> = combine(
        _amountText,
        _currencyCode,
        counterpartyId,
        remaining,
    ) { amountText, currencyCode, counterpartyId, liveRemaining ->
        val amount = Money.parseToMinor(amountText) ?: return@combine false
        if (currencyCode.isEmpty()) return@combine false
        if (counterpartyId.isEmpty()) return@combine false
        if (writeOff) {
            amount > 0 && amount <= abs(liveRemaining)
        } else {
            // Начальный остаток: минус — мой долг; ноль корректировку не создаёт.
            amount != 0L
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun save(liveRemaining: Long) {
        if (!canSave.value) return
        val parsed = Money.parseToMinor(_amountText.value) ?: return
        viewModelScope.launch {
            val deltaMinor = if (writeOff) {
                // Списание хранит дельту в сторону нуля: знак противоположен остатку.
                DebtCalculator.writeOffDelta(liveRemaining, parsed)
            } else {
                parsed
            }
            val adjustment = DebtAdjustmentEntity(
                id = editId ?: UUID.randomUUID().toString(),
                counterpartyId = counterpartyId.value,
                kind = kind.value.wire,
                amountMinor = deltaMinor,
                currencyCode = _currencyCode.value,
                date = _date.value
                    .atTime(timeOfDay ?: LocalTime.now(zone))
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli(),
                note = _note.value.trim(),
                // Начальный остаток в отчётах не отражается никогда.
                countInStats = if (writeOff) _countInStats.value else false,
                updatedAt = System.currentTimeMillis(),
            )
            debtAdjustmentDao.upsert(adjustment)
            syncEngine.uploadDebtAdjustment(adjustment)
            _saved.value = true
        }
    }

    fun delete() {
        viewModelScope.launch {
            val adjustment = debtAdjustmentDao.byId(editId ?: return@launch) ?: return@launch
            val deleted = adjustment.copy(deleted = true, updatedAt = System.currentTimeMillis())
            debtAdjustmentDao.upsert(deleted)
            syncEngine.uploadDebtAdjustment(deleted)
            _saved.value = true
        }
    }
}
