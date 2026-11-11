package com.expeysi.app.ui.balances

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AppDatabase
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.CurrencyEntity
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.sync.SyncEngine
import com.expeysi.app.domain.Currency
import com.expeysi.app.ui.icons.CategoryPalette
import com.expeysi.app.ui.icons.IconCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EditBalanceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val database: AppDatabase,
    private val balanceDao: BalanceDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    currencyDao: CurrencyDao,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val editId: String? = savedStateHandle["balanceId"]
    private var original: BalanceEntity? = null

    val isEdit: Boolean = editId != null

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _currencyCode = MutableStateFlow(Currency.DEFAULT)
    val currencyCode: StateFlow<String> = _currencyCode.asStateFlow()

    /** Ключ иконки баланса; пустой — круг с первой буквой названия. */
    private val _iconKey = MutableStateFlow(DEFAULT_BALANCE_ICON)
    val iconKey: StateFlow<String> = _iconKey.asStateFlow()

    private val _colorKey = MutableStateFlow(DEFAULT_BALANCE_COLOR)
    val colorKey: StateFlow<String> = _colorKey.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** Доступные иконки баланса; «три точки» — ключ категорий, здесь не показываем. */
    val availableIconKeys: List<String> = IconCatalog.availableKeys.filterNot { it == "more_horiz" }

    val canSave: StateFlow<Boolean> = _name
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Все валюты справочника: для резолва кода текущего баланса. */
    val currencies: StateFlow<List<CurrencyEntity>> = currencyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Коды валют активных балансов: секция «Используемые» пикера. */
    val usedCurrencyCodes: StateFlow<List<String>> = balanceDao.observeActive()
        .map { balances ->
            balances.sortedByDescending { it.updatedAt }
                .map { it.currencyCode }
                .distinct()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Валюты, уже используемые в балансах: последние по времени изменения, до трёх. */
    val recentCurrencyCodes: StateFlow<List<String>> = balanceDao.observeActive()
        .map { balances ->
            balances.sortedByDescending { it.updatedAt }
                .map { it.currencyCode }
                .distinct()
                .take(RECENT_CURRENCY_LIMIT)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Запрос поиска в пикере валют; в VM — переживает поворот экрана. */
    private val _currencyQuery = MutableStateFlow("")
    val currencyQuery: StateFlow<String> = _currencyQuery.asStateFlow()

    /** Флаг «Все валюты» пикера: показывать остальной список. */
    private val _showAllCurrencies = MutableStateFlow(false)
    val showAllCurrencies: StateFlow<Boolean> = _showAllCurrencies.asStateFlow()

    fun onCurrencyQueryChange(value: String) {
        _currencyQuery.value = value
    }

    fun onShowAllCurrencies() {
        _showAllCurrencies.value = true
    }

    /** Сброс состояния пикера при закрытии диалога. */
    fun resetCurrencyPicker() {
        _currencyQuery.value = ""
        _showAllCurrencies.value = false
    }

    init {
        if (editId != null) {
            viewModelScope.launch {
                balanceDao.byId(editId)?.let { balance ->
                    original = balance
                    _name.value = balance.name
                    _currencyCode.value = balance.currencyCode
                    _iconKey.value = balance.iconKey
                    _colorKey.value = balance.colorKey
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _name.value = value
    }

    fun onCurrencySelect(code: String) {
        _currencyCode.value = code
    }

    fun onIconSelect(key: String) {
        _iconKey.value = key
    }

    fun onColorSelect(key: String) {
        _colorKey.value = key
    }

    fun save() {
        if (!canSave.value) return
        viewModelScope.launch {
            val balance = BalanceEntity(
                id = editId ?: UUID.randomUUID().toString(),
                name = _name.value.trim(),
                currencyCode = _currencyCode.value,
                iconKey = _iconKey.value,
                colorKey = _colorKey.value,
                updatedAt = System.currentTimeMillis(),
            )
            balanceDao.upsert(balance)
            syncEngine.uploadBalance(balance)
            _saved.value = true
        }
    }

    fun delete() {
        val balance = original ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val deletedBalance = balance.copy(deleted = true, updatedAt = now)
            val accounts = accountDao.byBalanceId(balance.id)
            val transactions = transactionDao.byAccountIds(accounts.map { it.id })
            val deletedAccounts = accounts.filterNot { it.deleted }
                .map { it.copy(deleted = true, updatedAt = now) }
            val deletedTransactions = transactions.filterNot { it.deleted }
                .map { it.copy(deleted = true, updatedAt = now) }

            database.withTransaction {
                balanceDao.upsert(deletedBalance)
                deletedAccounts.forEach { accountDao.upsert(it) }
                deletedTransactions.forEach { transactionDao.upsert(it) }
            }

            syncEngine.uploadBalance(deletedBalance)
            deletedAccounts.forEach(syncEngine::uploadAccount)
            deletedTransactions.forEach(syncEngine::uploadTransaction)
            _saved.value = true
        }
    }

    private companion object {
        /** Иконка нового баланса по умолчанию — кошелёк. */
        const val DEFAULT_BALANCE_ICON = "wallet"

        /** Цвет нового баланса: первый в сетке цветов на экране. */
        val DEFAULT_BALANCE_COLOR = CategoryPalette.availableKeys.first()

        /** Валют в секции «Недавние» пикера. */
        const val RECENT_CURRENCY_LIMIT = 3
    }
}
