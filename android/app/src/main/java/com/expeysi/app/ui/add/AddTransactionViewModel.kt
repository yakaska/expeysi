package com.expeysi.app.ui.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.expeysi.app.data.remote.AuthProvider
import com.expeysi.app.data.sync.SyncTrigger
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
    authRepository: AuthProvider,
    private val selectionStore: LastTransactionSelectionStore,
    private val syncTrigger: SyncTrigger,
) : ViewModel() {

    private val editId: String? = savedStateHandle["transactionId"]
    private val userId: String = authRepository.userId ?: "local"
    private var original: TransactionEntity? = null

    val isEdit: Boolean = editId != null

    private val zone: ZoneId = ZoneId.systemDefault()

    val accounts: StateFlow<List<AccountEntity>> = accountDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currencySymbols: StateFlow<Map<String, String>> = currencyDao.observeAll()
        .map { currencies -> currencies.associate { it.code to it.symbol } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val balances: StateFlow<Map<String, BalanceEntity>> = balanceDao.observeAll()
        .map { balances -> balances.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Подставленный тип операции (из истории доходов/расходов); по умолчанию — расход.
    private val initialType: TxType =
        savedStateHandle.get<String>("type")?.let { TxType.fromWire(it) } ?: TxType.EXPENSE

    private val _type = MutableStateFlow(initialType)
    val type: StateFlow<TxType> = _type.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = combine(
        _type.flatMapLatest { categoryDao.observeActive(it.wire) },
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
                    _type.value = TxType.fromWire(tx.type)
                    _amountText.value = Money.formatInput(Money.formatAuto(tx.amountMinor))
                    _selectedAccountId.value = tx.accountId
                    _selectedCategoryId.value = tx.categoryId
                    val dateTime = Instant.ofEpochMilli(tx.date).atZone(zone)
                    _date.value = dateTime.toLocalDate()
                    _time.value = dateTime.toLocalTime().withSecond(0).withNano(0)
                    _note.value = tx.note
                }
            } else {
                _selectedAccountId.value = selectionStore.accountId(userId)
                    ?.let { accountDao.byId(it) }
                    ?.selectableId()
                restoreLastCategory(initialType)
            }
        }
    }

    val canSave: StateFlow<Boolean> = combine(
        _amountText,
        _selectedAccountId,
        _selectedCategoryId,
    ) { amount, accountId, categoryId ->
        val parsed = Money.parseToMinor(amount)
        parsed != null && parsed > 0 && accountId != null && categoryId != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onTypeChange(value: TxType) {
        if (_type.value == value) return
        _type.value = value
        _selectedCategoryId.value = null
        viewModelScope.launch { restoreLastCategory(value) }
    }

    fun onAmountChange(value: String) {
        _amountText.value = Money.formatInput(value)
    }

    fun onAccountSelect(id: String) {
        _selectedAccountId.value = id
        selectionStore.selectAccount(userId, id)
    }

    fun onCategorySelect(id: String) {
        _selectedCategoryId.value = id
        selectionStore.selectCategory(userId, _type.value, id)
    }

    private suspend fun restoreLastCategory(type: TxType) {
        val id = selectionStore.categoryId(userId, type)
            ?.let { categoryDao.byId(it) }
            ?.selectableId(type)
        if (_type.value == type) _selectedCategoryId.value = id
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
        val amount = Money.parseToMinor(_amountText.value) ?: return
        val accountId = _selectedAccountId.value ?: return
        val categoryId = _selectedCategoryId.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val transaction = TransactionEntity(
                id = editId ?: UUID.randomUUID().toString(),
                type = _type.value.wire,
                amountMinor = amount,
                accountId = accountId,
                categoryId = categoryId,
                date = LocalDateTime.of(_date.value, _time.value)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli(),
                note = _note.value.trim(),
                updatedAt = now,
            )
            transactionDao.upsert(transaction.copy(dirty = true))
            syncTrigger.trigger()
            _saved.value = true
        }
    }

    fun delete() {
        val tx = original ?: return
        viewModelScope.launch {
            val deleted = tx.copy(deleted = true, updatedAt = System.currentTimeMillis(), dirty = true)
            transactionDao.upsert(deleted)
            syncTrigger.trigger()
            _saved.value = true
        }
    }
}
