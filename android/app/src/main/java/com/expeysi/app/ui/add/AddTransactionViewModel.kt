package com.expeysi.app.ui.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.AuthRepository
import com.expeysi.app.data.LastTransactionSelectionStore
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
import com.expeysi.app.domain.Money
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
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val editId: String? = savedStateHandle["transactionId"]
    private val userId: String = authRepository.userId ?: "local"
    private var original: TransactionEntity? = null

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
        _fromAmountText.value = out?.let { Money.formatInput(Money.formatAuto(it.amountMinor)) } ?: ""
        _toAmountText.value = inLeg?.let { Money.formatInput(Money.formatAuto(it.amountMinor)) } ?: ""
        // Загруженная сумма зачисления — уже «пользовательские данные»:
        // зеркало при правке списания её не трогает.
        _toAmountTouched.value = inLeg != null
    }

    /** Разные валюты у счетов перевода? Сумма зачисления вводится независимо. */
    private fun sameCurrencyNow(): Boolean {
        val balancesNow = balances.value
        val fromCode = _fromAccountId.value?.let { balancesNow[it]?.currencyCode } ?: return false
        val toCode = _toAccountId.value?.let { balancesNow[it]?.currencyCode } ?: return false
        return fromCode == toCode
    }

    private val regularCanSave: StateFlow<Boolean> = combine(
        _amountText,
        _selectedAccountId,
        _selectedCategoryId,
    ) { amount, accountId, categoryId ->
        val parsed = Money.parseToMinor(amount)
        parsed != null && parsed > 0 && accountId != null && categoryId != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val transferCanSave: StateFlow<Boolean> = combine(
        _fromAccountId,
        _toAccountId,
        _fromAmountText,
        _toAmountText,
    ) { from, to, fromAmount, toAmount ->
        val out = Money.parseToMinor(fromAmount)
        val inMinor = Money.parseToMinor(toAmount)
        from != null &&
            to != null &&
            from != to &&
            out != null &&
            out > 0 &&
            inMinor != null &&
            inMinor > 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val canSave: StateFlow<Boolean> = combine(
        _mode,
        regularCanSave,
        transferCanSave,
    ) { mode, regular, transfer ->
        if (mode == AddMode.TRANSFER) transfer else regular
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onModeChange(value: AddMode) {
        if (_mode.value == value) return
        _mode.value = value
        when {
            value == AddMode.TRANSFER -> {
                _selectedCategoryId.value = null
                // Со счёта — последний использованный счёт привычного потока.
                if (_fromAccountId.value == null) {
                    viewModelScope.launch {
                        _fromAccountId.value = selectionStore.accountId(userId)
                            ?.let { accountDao.byId(it) }
                            ?.selectableId()
                    }
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
        if (sameCurrencyNow() && !_toAmountTouched.value) {
            _toAmountText.value = formatted
        }
    }

    fun onToAmountChange(value: String) {
        _toAmountText.value = Money.formatInput(value)
        _toAmountTouched.value = true
    }

    fun onAccountSelect(id: String) {
        _selectedAccountId.value = id
        selectionStore.selectAccount(userId, id)
    }

    fun onFromAccountSelect(id: String) {
        _fromAccountId.value = id
        selectionStore.selectAccount(userId, id)
        if (sameCurrencyNow() && !_toAmountTouched.value) {
            _toAmountText.value = _fromAmountText.value
        }
    }

    fun onToAccountSelect(id: String) {
        _toAccountId.value = id
        if (sameCurrencyNow() && !_toAmountTouched.value) {
            _toAmountText.value = _fromAmountText.value
        }
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
        val outAmount = Money.parseToMinor(_fromAmountText.value) ?: return
        val inAmount = Money.parseToMinor(_toAmountText.value) ?: return
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
