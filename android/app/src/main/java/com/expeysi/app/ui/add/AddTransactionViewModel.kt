package com.expeysi.app.ui.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.AuthRepository
import com.expeysi.app.data.LastTransactionSelectionStore
import com.expeysi.app.data.TransferRateStore
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.data.sync.SyncEngine
import com.expeysi.app.domain.BalanceCalculator
import com.expeysi.app.domain.Money
import com.expeysi.app.domain.TransferCalc
import com.expeysi.app.domain.TxSum
import com.expeysi.app.domain.TxType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    currencyDao: CurrencyDao,
    balanceDao: BalanceDao,
    authRepository: AuthRepository,
    private val selectionStore: LastTransactionSelectionStore,
    private val rateStore: TransferRateStore,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val editId: String? = savedStateHandle["transactionId"]
    private val userId: String = authRepository.userId ?: "local"
    private var original: TransactionEntity? = null

    /** Дата и время записи на момент открытия правки: эталон для dirty-сравнения. */
    private var originalDate: LocalDate? = null
    private var originalTime: LocalTime? = null

    /** Открытая нога перевода при редактировании пары; null — доход, расход или создание. */
    private var editLinkedId: String? = null
    private var editOutLeg: TransactionEntity? = null
    private var editInLeg: TransactionEntity? = null

    val isEdit: Boolean = editId != null

    private val zone: ZoneId = ZoneId.systemDefault()

    /** Подставленный тип операции (из истории доходов/расходов); по умолчанию — расход. */
    private val initialMode: AddMode = AddMode.fromWire(savedStateHandle.get<String>("type"))

    private val _mode = MutableStateFlow(initialMode)
    val mode: StateFlow<AddMode> = _mode.asStateFlow()

    /** Ноги пары, существующие локально на момент открытия редактирования. */
    val isTransferOutLegPresent: Boolean get() = _mode.value != AddMode.TRANSFER || editOutLeg != null || !isEdit
    val isTransferInLegPresent: Boolean get() = _mode.value != AddMode.TRANSFER || editInLeg != null || !isEdit

    val accounts: StateFlow<List<AccountEntity>> = accountDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currencySymbols: StateFlow<Map<String, String>> = currencyDao.observeAll()
        .map { currencies -> currencies.associate { it.code to it.symbol } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val balances: StateFlow<Map<String, BalanceEntity>> = balanceDao.observeAll()
        .map { balances -> balances.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Текущие остатки счетов для подписей в карточке перевода. */
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

    val categories: StateFlow<List<CategoryEntity>> = combine(
        _mode.flatMapLatest { mode ->
            when (val txType = mode.txType) {
                null -> flowOf(emptyList())
                else -> categoryDao.observeActive(txType.wire)
            }
        },
        transactionDao.observeActive(),
    ) { categories, transactions ->
        categories.sortedByUsage(transactions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _amountText = MutableStateFlow("")
    val amountText: StateFlow<String> = _amountText.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<String?>(null)
    val selectedAccountId: StateFlow<String?> = _selectedAccountId.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    /** Перевод: счёт-источник и счёт-получатель. */
    private val _fromAccountId = MutableStateFlow<String?>(null)
    val fromAccountId: StateFlow<String?> = _fromAccountId.asStateFlow()

    private val _toAccountId = MutableStateFlow<String?>(null)
    val toAccountId: StateFlow<String?> = _toAccountId.asStateFlow()

    /** Суммы обеих ног: списание в валюте источника, зачисление в валюте получателя. */
    private val _fromAmountText = MutableStateFlow("")
    val fromAmountText: StateFlow<String> = _fromAmountText.asStateFlow()

    private val _toAmountText = MutableStateFlow("")
    val toAmountText: StateFlow<String> = _toAmountText.asStateFlow()

    /**
     * Пользователь правил сумму зачисления вручную: после этого зеркало
     * «списано → зачислено» при одинаковых валютах не подставляется,
     * чтобы не затереть комиссию или собственный ввод.
     */
    private val _toAmountTouched = MutableStateFlow(false)

    /**
     * Курс перевода для разных валют: вводится вручную и сети не загружается.
     * Хранится точным числом [rateExact] (пересчёт туда-обратно сумму не меняет),
     * поле показывает его округлённо — до шести значащих цифр. При смене пары
     * валют подставляется последний использованный курс пары; при правке
     * зачисления курс выводится из сумм.
     */
    private val _rateText = MutableStateFlow("")
    val rateText: StateFlow<String> = _rateText.asStateFlow()

    /** Точный курс «куда/откуда»; null — поле пустое или битое, пересчёт замирает. */
    private var rateExact: BigDecimal? = null

    /**
     * Направление показа курса: false — «1 отправление = N зачисление», true —
     * наоборот. По умолчанию выбирается автоправилом «крупная валюта слева»
     * (курс меньше единицы — показ наоборот); ручной разворот сохраняется,
     * пока пара не сменится.
     */
    private val _rateInverted = MutableStateFlow(false)
    val rateInverted: StateFlow<Boolean> = _rateInverted.asStateFlow()

    /** Направление развёрнуто вручную — до смены пары автоправило не трогает его. */
    private var rateDirectionManual = false

    /**
     * Подсказка или настоящий курс: true — курс подставлен последним
     * использованным для пары, пользователь его не правил, суммы его пока
     * не вывели. Окрашивает подпись курса серым; сбрасывается при правке
     * курса, при выводе курса из сумм и при развороте перевода.
     */
    private val _rateIsSuggested = MutableStateFlow(false)
    val rateIsSuggested: StateFlow<Boolean> = _rateIsSuggested.asStateFlow()

    /** Курс и комиссия правились вручную — inline-ошибки показываются только после этого. */
    private val _rateTouched = MutableStateFlow(false)
    private val _feeTouched = MutableStateFlow(false)

    /** Комиссия в валюте списания; пустая строка — перевода без комиссии. */
    private val _feeText = MutableStateFlow("")
    val feeText: StateFlow<String> = _feeText.asStateFlow()

    /** Поле комиссии видно, когда пользователь его раскрыл или комиссия загружена из записи. */
    private val _feeVisible = MutableStateFlow(false)
    val feeVisible: StateFlow<Boolean> = _feeVisible.asStateFlow()

    /** Пара валют при последнем пересчёте курса; её смена перезаполняет курс. */
    private var transferPair: Pair<String, String>? = null

    private val _date = MutableStateFlow(LocalDate.now(zone))
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _time = MutableStateFlow(LocalTime.now(zone).withSecond(0).withNano(0))
    val time: StateFlow<LocalTime> = _time.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    val noteSuggestions: StateFlow<List<String>> = combine(
        _note,
        transactionDao.observeActive(),
    ) { note, transactions ->
        val query = note.trim()
        if (query.isEmpty()) {
            emptyList()
        } else {
            transactions.asSequence()
                .map { it.note.trim() }
                .filter { it.length > query.length && it.startsWith(query, ignoreCase = true) }
                .distinctBy { it.lowercase() }
                .take(5)
                .toList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val showCategorySheet = savedStateHandle.getStateFlow("showCategorySheet", false)

    fun setCategorySheetVisible(visible: Boolean) {
        savedStateHandle["showCategorySheet"] = visible
    }

    init {
        viewModelScope.launch {
            if (editId != null) {
                transactionDao.byId(editId)?.let { tx ->
                    original = tx
                    val mode = AddMode.fromWire(tx.type)
                    _mode.value = mode
                    val dateTime = Instant.ofEpochMilli(tx.date).atZone(zone)
                    _date.value = dateTime.toLocalDate()
                    _time.value = dateTime.toLocalTime().withSecond(0).withNano(0)
                    _note.value = tx.note
                    when (mode) {
                        AddMode.TRANSFER -> loadTransferLegs(tx)
                        else -> {
                            _amountText.value = Money.formatInput(Money.formatAuto(tx.amountMinor))
                            _selectedAccountId.value = tx.accountId
                            _selectedCategoryId.value = tx.categoryId
                        }
                    }
                    // Дата/время baseline для dirty-сравнения фиксируются после нормализации.
                    originalDate = _date.value
                    originalTime = _time.value
                }
            } else {
                _selectedAccountId.value = selectionStore.accountId(userId)
                    ?.let { accountDao.byId(it) }
                    ?.selectableId()
                restoreLastCategory(initialMode.txType)
            }
        }
    }

    private suspend fun loadTransferLegs(tx: TransactionEntity) {
        editLinkedId = tx.linkedId
        // Связка обязана быть у всякой ноги; запрос с пустой вернул бы всю таблицу.
        val legs = if (tx.linkedId.isEmpty()) {
            listOf(tx)
        } else {
            transactionDao.byLinkedId(tx.linkedId).ifEmpty { listOf(tx) }
        }
        val out = legs.firstOrNull { TxType.fromWire(it.type) == TxType.TRANSFER_OUT }
        val inLeg = legs.firstOrNull { TxType.fromWire(it.type) == TxType.TRANSFER_IN }
        editOutLeg = out
        editInLeg = inLeg
        _fromAccountId.value = out?.accountId ?: tx.counterpartyAccountId.ifEmpty { null }
        _toAccountId.value = inLeg?.accountId ?: tx.counterpartyAccountId.ifEmpty { null }
        // «Отправляете» — сумма без комиссии: она хранится у ноги отдельным полем.
        val feeMinor = out?.feeMinor ?: 0L
        _fromAmountText.value = out?.let { Money.formatInput(Money.formatAuto(it.amountMinor - feeMinor)) } ?: ""
        _toAmountText.value = inLeg?.let { Money.formatInput(Money.formatAuto(it.amountMinor)) } ?: ""
        // Сохранённая комиссия показывается раскрытой.
        if (feeMinor > 0L) {
            _feeText.value = Money.formatInput(Money.formatAuto(feeMinor))
            _feeVisible.value = true
        }
        // Загруженная сумма зачисления — уже «пользовательские данные»:
        // зеркало при правке списания её не трогает.
        _toAmountTouched.value = inLeg != null
        transferPair = currencyPairCodes()
        _rateTouched.value = false
        val codes = transferPair
        if (codes != null && codes.first != codes.second) {
            // Курс выводится из сумм ног в полной точности: пересохранение
            // без правок не меняет суммы. Пока сумм нет — последний
            // использованный курс пары из локального хранилища.
            rateDirectionManual = false
            val send = Money.parseToMinor(_fromAmountText.value)
            val receive = Money.parseToMinor(_toAmountText.value)
            val derived = if (send != null && receive != null) TransferCalc.exactRate(send, receive) else null
            // Курс из сумм ног — настоящий; пока ног нет — последний использованный
            // курс пары из локального хранилища, показывается как подсказка.
            if (derived != null) {
                setDerivedRate(derived, suggested = false)
            } else {
                setDerivedRate(rateStore.rate(codes.first, codes.second)?.let(TransferCalc::parse), suggested = true)
            }
            if (rateExact == null) {
                _rateText.value = ""
                _rateIsSuggested.value = false
            }
        } else {
            rateExact = null
            _rateInverted.value = false
            _rateText.value = ""
            _rateIsSuggested.value = false
        }
    }

    /**
     * Курс выведен из сумм (правка зачисления, загрузка правки, смена пары):
     * хранится точным числом, направление показа выбирается автоправилом
     * «крупная валюта слева», поле показывает до шести значащих цифр.
     * Подставленный из хранилища курс помечается подсказкой ([suggested]),
     * выведенный из сумм — настоящим курсом.
     */
    private fun setDerivedRate(rate: BigDecimal?, suggested: Boolean = false) {
        if (rate == null) return
        rateExact = rate
        _rateIsSuggested.value = suggested
        if (!rateDirectionManual) {
            _rateInverted.value = rate.compareTo(BigDecimal.ONE) < 0
        }
        _rateText.value = displayRateText()
    }

    /** Поле и подпись курса: показ точного курса в текущем направлении. */
    private fun displayRateText(): String {
        val rate = rateExact ?: return ""
        val shown = if (_rateInverted.value) {
            TransferCalc.invertRate(rate) ?: return ""
        } else {
            rate
        }
        return TransferCalc.formatRateDisplay(shown)
    }

    /** Коды валют пары счетов перевода; null, пока какой-то из счетов не выбран. */
    private fun currencyPairCodes(): Pair<String, String>? {
        val balancesNow = balances.value
        val accountsById = accounts.value.associateBy { it.id }
        val fromCode = currencyCodeOf(accountsById[_fromAccountId.value], balancesNow) ?: return null
        val toCode = currencyCodeOf(accountsById[_toAccountId.value], balancesNow) ?: return null
        return fromCode to toCode
    }

    /** Разные валюты у счетов перевода? Сумма зачисления вводится независимо. */
    private fun sameCurrencyNow(): Boolean = currencyPairCodes()?.let { it.first == it.second } == true

    private val regularCanSave: StateFlow<Boolean> = combine(
        _amountText,
        _selectedAccountId,
        _selectedCategoryId,
    ) { amount, accountId, categoryId ->
        val parsed = Money.parseToMinor(amount)
        parsed != null && parsed > 0 && accountId != null && categoryId != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val transferCanSave: StateFlow<Boolean> = combine(
        combine(_fromAccountId, _toAccountId, _fromAmountText) { from, to, send -> Triple(from, to, send) },
        combine(_toAmountText, _rateText, _feeText) { receive, rate, fee -> Triple(receive, rate, fee) },
        accounts,
        balances,
    ) { inputs, texts, accountsNow, balancesNow ->
        val accountsById = accountsNow.associateBy { it.id }
        val fromCode = currencyCodeOf(accountsById[inputs.first], balancesNow)
        val toCode = currencyCodeOf(accountsById[inputs.second], balancesNow)
        val sameCurrency = fromCode != null && toCode != null && fromCode == toCode
        TransferCalc.canSave(
            fromId = inputs.first,
            toId = inputs.second,
            sameCurrency = sameCurrency,
            sendText = inputs.third,
            receiveText = texts.first,
            rateText = texts.second,
            feeText = texts.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Сломанные поля перевода: курс пустой или неположительный (только для
     * пары разных валют) и отрицательная комиссия. Показывается на поле
     * после касания — см. rateErrorVisible/feeErrorVisible.
     */
    private val transferErrors = combine(
        combine(_rateText, _feeText) { rate, fee -> rate to fee },
        accounts,
        balances,
        _fromAccountId,
        _toAccountId,
    ) { texts, accountsNow, balancesNow, fromId, toId ->
        val accountsById = accountsNow.associateBy { it.id }
        val fromCode = currencyCodeOf(accountsById[fromId], balancesNow)
        val toCode = currencyCodeOf(accountsById[toId], balancesNow)
        val cross = fromCode != null && toCode != null && fromCode != toCode
        val rate = TransferCalc.parse(texts.first)
        val feeMinor = if (texts.second.isEmpty()) 0L else Money.parseToMinor(texts.second)
        Pair(
            // Курс имеет смысл только у пары разных валют.
            cross && (rate == null || rate.signum() <= 0),
            feeMinor != null && feeMinor < 0,
        )
    }
    val rateErrorVisible: StateFlow<Boolean> = combine(transferErrors, _rateTouched) { errors, touched ->
        errors.first && touched
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val feeErrorVisible: StateFlow<Boolean> = combine(transferErrors, _feeTouched) { errors, touched ->
        errors.second && touched
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Правка изменена? Доход/расход: всё сохраняемое против записи на открытии. */
    private val regularDirty: StateFlow<Boolean> = combine(
        combine(_amountText, _selectedAccountId, _selectedCategoryId) { amount, account, category ->
            Triple(amount, account, category)
        },
        combine(_date, _time, _note) { date, time, note -> Triple(date, time, note) },
    ) { fields, stamp ->
        val baseline = original
        val baselineDate = originalDate
        val baselineTime = originalTime
        if (!isEdit || baseline == null || baselineDate == null || baselineTime == null || _mode.value == AddMode.TRANSFER) {
            false
        } else {
            regularDirty(
                RegularDirtyInput(fields.second, fields.third, fields.first, stamp.first, stamp.second, stamp.third),
                baseline,
                baselineDate,
                baselineTime,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Правка изменена? Перевод: счета, суммы, комиссия, дата/время, заметка против ног. */
    private val transferDirty: StateFlow<Boolean> = combine(
        combine(_fromAccountId, _toAccountId, _fromAmountText, _feeText) { from, to, send, fee ->
            TransferFields(from, to, send, fee)
        },
        combine(_toAmountText, _date, _time, _note) { receive, date, time, note ->
            TransferStamp(receive, date, time, note)
        },
    ) { fields, stamp ->
        val baseline = original
        val baselineDate = originalDate
        val baselineTime = originalTime
        if (!isEdit || baseline == null || baselineDate == null || baselineTime == null || _mode.value != AddMode.TRANSFER) {
            false
        } else {
            transferDirty(
                TransferDirtyInput(
                    fromAccountId = fields.from,
                    toAccountId = fields.to,
                    sendText = fields.send,
                    feeText = fields.fee,
                    receiveText = stamp.receive,
                    date = stamp.date,
                    time = stamp.time,
                    note = stamp.note,
                ),
                editOutLeg,
                editInLeg,
                baseline,
                baselineDate,
                baselineTime,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val canSave: StateFlow<Boolean> = combine(
        _mode,
        regularCanSave,
        transferCanSave,
        regularDirty,
        transferDirty,
    ) { mode, regularValid, transferValid, regularChanged, transferChanged ->
        val valid = if (mode == AddMode.TRANSFER) transferValid else regularValid
        val changed = if (mode == AddMode.TRANSFER) transferChanged else regularChanged
        // Создание — готово, когда валидно; правка — когда валидно и есть правка.
        valid && (!isEdit || changed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onModeChange(value: AddMode) {
        if (_mode.value == value) return
        _mode.value = value
        when {
            value == AddMode.TRANSFER -> {
                _selectedCategoryId.value = null
                viewModelScope.launch {
                    // Со счёта — последний использованный счёт привычного потока.
                    if (_fromAccountId.value == null) {
                        _fromAccountId.value = selectionStore.accountId(userId)
                            ?.let { accountDao.byId(it) }
                            ?.selectableId()
                    }
                    refreshTransferPair()
                }
            }
            else -> {
                _selectedCategoryId.value = null
                viewModelScope.launch { restoreLastCategory(value.txType) }
            }
        }
    }

    fun onAmountChange(value: String) {
        _amountText.value = Money.formatInput(value)
    }

    fun onFromAmountChange(value: String) {
        val formatted = Money.formatInput(value)
        _fromAmountText.value = formatted
        if (sameCurrencyNow()) {
            // Зеркало одинаковых валют; вручную правленное зачисление не затирается.
            if (!_toAmountTouched.value) _toAmountText.value = formatted
        } else {
            // Разные валюты: зачисление = списание × курс.
            recomputeReceive()
        }
    }

    fun onToAmountChange(value: String) {
        _toAmountText.value = Money.formatInput(value)
        _toAmountTouched.value = true
        // Разные валюты: курс = зачисление / списание.
        if (!sameCurrencyNow()) recomputeRate()
    }

    /** Курс правится вручную: зачисление пересчитывается по списанию и курсу. */
    fun onRateChange(value: String) {
        val text = Money.formatInput(value)
        _rateText.value = text
        _rateTouched.value = true
        // Пользовательский ввод не подсказка, даже если совпал с нею.
        _rateIsSuggested.value = false
        // Точный курс хранится как введён; в перевёрнутом направлении поле
        // редактирует обратный курс, и он пересчитывается в прямой.
        val typed = TransferCalc.parse(text)
        rateExact = if (typed == null) {
            null
        } else if (_rateInverted.value) {
            TransferCalc.invertRate(typed)
        } else {
            typed
        }
        recomputeReceive()
    }

    /** Разворот направления показа курса: «1 USD = 25 000 VND» ↔ «1 VND = 0,0000267 USD». */
    fun toggleRateDirection() {
        val rate = rateExact ?: return
        if (rate.signum() <= 0) return
        _rateInverted.value = !_rateInverted.value
        rateDirectionManual = true
        _rateText.value = displayRateText()
    }

    fun onFeeChange(value: String) {
        _feeText.value = Money.formatInput(value)
        _feeTouched.value = true
    }

    /** Пользователь раскрыл поле комиссии: «Добавить комиссию» или IME-переход. */
    fun showFee() {
        _feeVisible.value = true
    }

    /**
     * Пользователь ушёл из пустого поля комиссии: сворачиваем обратно в
     * «Добавить комиссию». Введённая комиссия поле не сворачивает.
     */
    fun hideFee() {
        _feeVisible.value = false
    }

    /** Зачисление = списание × точный курс; негодный вход поле зачисления не трогает. */
    private fun recomputeReceive() {
        val rate = rateExact ?: return
        val send = Money.parseToMinor(_fromAmountText.value) ?: return
        val receive = TransferCalc.receiveMinor(send, rate) ?: return
        _toAmountText.value = Money.formatInput(Money.formatAuto(receive))
    }

    /** Курс = точное отношение зачисления к списанию; списание нулевое или пустое — курс не трогается. */
    private fun recomputeRate() {
        val send = Money.parseToMinor(_fromAmountText.value) ?: return
        if (send <= 0) return
        val receive = Money.parseToMinor(_toAmountText.value) ?: return
        if (receive < 0) return
        setDerivedRate(TransferCalc.exactRate(send, receive))
    }

    fun onAccountSelect(id: String) {
        _selectedAccountId.value = id
        selectionStore.selectAccount(userId, id)
    }

    fun onFromAccountSelect(id: String) {
        _fromAccountId.value = id
        selectionStore.selectAccount(userId, id)
        refreshTransferPair()
    }

    fun onToAccountSelect(id: String) {
        _toAccountId.value = id
        refreshTransferPair()
    }

    /**
     * Смена пары валют перевода: курс подставляется последним использованным
     * для этой пары (локальное хранилище), зачисление пересчитывается по
     * списанию. Пара та же — ничего не делаем: пользовательский курс при смене
     * счёта той же валюты сохраняется.
     */
    private fun refreshTransferPair() {
        val pair = currencyPairCodes() ?: return
        if (pair == transferPair) return
        transferPair = pair
        if (pair.first == pair.second) {
            rateExact = null
            _rateInverted.value = false
            _rateText.value = ""
            _rateIsSuggested.value = false
            if (!_toAmountTouched.value) _toAmountText.value = _fromAmountText.value
        } else {
            rateDirectionManual = false
            rateExact = null
            _rateTouched.value = false
            setDerivedRate(rateStore.rate(pair.first, pair.second)?.let(TransferCalc::parse), suggested = true)
            if (rateExact == null) {
                _rateText.value = ""
                _rateIsSuggested.value = false
            }
            recomputeReceive()
        }
    }

    /**
     * Разворот перевода: счета-источник и счёт-получатель меняются местами,
     * суммы переставляются, точный курс инвертируется без потери точности.
     * Правка зачисления вручную больше не считается «пользовательскими
     * данными»: направление изменено, все значения берутся заново.
     */
    fun swapTransferAccounts() {
        val fromId = _fromAccountId.value
        _fromAccountId.value = _toAccountId.value
        _toAccountId.value = fromId
        val swapped = TransferCalc.swap(_fromAmountText.value, _toAmountText.value, _rateText.value)
        _fromAmountText.value = swapped.sendText
        _toAmountText.value = swapped.receiveText
        _toAmountTouched.value = false
        _rateTouched.value = false
        rateDirectionManual = false
        rateExact = rateExact?.let { TransferCalc.invertRate(it) }
        // Инвертированный курс вычислен автоматически — не подсказка.
        setDerivedRate(rateExact, suggested = false)
        if (rateExact == null) {
            _rateText.value = ""
            _rateIsSuggested.value = false
        }
        transferPair = currencyPairCodes()
    }

    fun onCategorySelect(id: String) {
        _selectedCategoryId.value = id
        _mode.value.txType?.let { selectionStore.selectCategory(userId, it, id) }
    }

    private suspend fun restoreLastCategory(type: TxType?) {
        if (type == null) return
        val id = selectionStore.categoryId(userId, type)
            ?.let { categoryDao.byId(it) }
            ?.selectableId(type)
        if (_mode.value.txType == type) _selectedCategoryId.value = id
    }

    /** DatePicker отдаёт миллисекунды полуночи в UTC. */
    fun onDatePick(utcMidnightMillis: Long) {
        _date.value = Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    }

    fun onTimePick(hour: Int, minute: Int) {
        _time.value = LocalTime.of(hour, minute)
    }

    fun onNoteChange(value: String) {
        _note.value = value
    }

    fun save() {
        if (!canSave.value) return
        when (_mode.value) {
            AddMode.TRANSFER -> saveTransfer()
            else -> saveRegular()
        }
    }

    private fun saveRegular() {
        val amount = Money.parseToMinor(_amountText.value) ?: return
        val accountId = _selectedAccountId.value ?: return
        val categoryId = _selectedCategoryId.value ?: return
        val type = _mode.value.txType ?: return
        viewModelScope.launch {
            val transaction = TransactionEntity(
                id = editId ?: UUID.randomUUID().toString(),
                type = type.wire,
                amountMinor = amount,
                accountId = accountId,
                categoryId = categoryId,
                date = dateMillis(),
                note = _note.value.trim(),
                updatedAt = System.currentTimeMillis(),
            )
            transactionDao.upsert(transaction)
            syncEngine.uploadTransaction(transaction)
            _saved.value = true
        }
    }

    /**
     * Пара ног одного перевода: списание со счёта-источника и зачисление на
     * счёт-получатель. Обе записи с общим linkedId; суммы — в валюте каждого
     * счёта. Сохранение пишется одним Room-вызовом (атомарно) и уходит в
     * Firestore отдельно по каждой ноге.
     */
    private fun saveTransfer() {
        val fromId = _fromAccountId.value ?: return
        val toId = _toAccountId.value ?: return
        if (fromId == toId) return
        val outSent = Money.parseToMinor(_fromAmountText.value) ?: return
        // Комиссия списывается со счёта-источника сверх отправляемой суммы.
        val feeMinor = if (_feeText.value.isEmpty()) 0L else Money.parseToMinor(_feeText.value) ?: return
        val outAmount = outSent + feeMinor
        val inAmount = when {
            // Одинаковые валюты: нога зачисления зеркалит списание (или хранит свою правку).
            sameCurrencyNow() -> Money.parseToMinor(_toAmountText.value) ?: outSent
            else -> Money.parseToMinor(_toAmountText.value) ?: return
        }
        // Последний курс пары запоминается для следующего перевода: локально,
        // в записи перевода курс не хранится. Пишется точным числом.
        currencyPairCodes()
            ?.takeIf { it.first != it.second }
            ?.let { pair ->
                rateExact?.let { rateStore.saveRate(pair.first, pair.second, it.toPlainString()) }
            }
        val note = _note.value.trim()
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val linkedId = editLinkedId ?: UUID.randomUUID().toString()
            val date = dateMillis()
            val legs = buildList {
                // Нога пишется, только если она существует локально: пара создаётся
                // целиком на одном устройстве, восстановление недостающей ноги
                // новым UUID дублировало бы её после досинхронизации.
                if (editOutLeg != null || !isEdit) {
                    add(
                        TransactionEntity(
                            id = editOutLeg?.id ?: UUID.randomUUID().toString(),
                            type = TxType.TRANSFER_OUT.wire,
                            amountMinor = outAmount,
                            accountId = fromId,
                            categoryId = "",
                            date = date,
                            note = note,
                            linkedId = linkedId,
                            counterpartyAccountId = toId,
                            // amountMinor — всего списано со счёта, комиссия рядом отдельным полем.
                            feeMinor = feeMinor,
                            updatedAt = now,
                        ),
                    )
                }
                if (editInLeg != null || !isEdit) {
                    add(
                        TransactionEntity(
                            id = editInLeg?.id ?: UUID.randomUUID().toString(),
                            type = TxType.TRANSFER_IN.wire,
                            amountMinor = inAmount,
                            accountId = toId,
                            categoryId = "",
                            date = date,
                            note = note,
                            linkedId = linkedId,
                            counterpartyAccountId = fromId,
                            updatedAt = now,
                        ),
                    )
                }
            }
            transactionDao.upsertAll(legs)
            // Ноги уходят по одному set() не атомарно, но недогрузка не теряется:
            // офлайн-очередь Firestore и uploadMissingLocal() следующей сессии добирают.
            legs.forEach { syncEngine.uploadTransaction(it) }
            _saved.value = true
        }
    }

    private fun dateMillis(): Long = LocalDateTime.of(_date.value, _time.value)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    fun delete() {
        viewModelScope.launch {
            when (_mode.value) {
                AddMode.TRANSFER -> deleteTransferPair()
                else -> deleteRegular()
            }
            _saved.value = true
        }
    }

    private suspend fun deleteRegular() {
        val tx = original ?: return
        val deleted = tx.copy(deleted = true, updatedAt = System.currentTimeMillis())
        transactionDao.upsert(deleted)
        syncEngine.uploadTransaction(deleted)
    }

    /** Удаляются известные локально ноги; недостающая приходит по синку и остаётся. */
    private suspend fun deleteTransferPair() {
        val legs = listOfNotNull(editOutLeg, editInLeg)
        if (legs.isEmpty()) return
        val now = System.currentTimeMillis()
        val deleted = legs.map { it.copy(deleted = true, updatedAt = now) }
        transactionDao.upsertAll(deleted)
        // См. saveTransfer: недогруженное надгробие добирают офлайн-очередь Firestore и uploadMissingLocal().
        deleted.forEach { syncEngine.uploadTransaction(it) }
    }
}

/** Поля перевода для dirty-сравнения: счета-источник, списание и комиссия. */
private data class TransferFields(
    val from: String?,
    val to: String?,
    val send: String,
    val fee: String,
)

/** Дата/время и суммы правки перевода для dirty-сравнения. */
private data class TransferStamp(
    val receive: String,
    val date: LocalDate,
    val time: LocalTime,
    val note: String,
)
