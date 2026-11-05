package com.expeysi.app.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.CurrencyDao
import com.expeysi.app.data.db.CurrencyEntity
import com.expeysi.app.domain.Currency
import com.expeysi.app.domain.Money
import com.expeysi.app.ui.icons.IconCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EditAccountViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountDao: AccountDao,
    currencyDao: CurrencyDao,
) : ViewModel() {

    private val editId: String? = savedStateHandle["accountId"]
    private var original: AccountEntity? = null

    val isEdit: Boolean = editId != null

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _iconKey = MutableStateFlow("payments")
    val iconKey: StateFlow<String> = _iconKey.asStateFlow()

    private val _openingBalanceText = MutableStateFlow("0")
    val openingBalanceText: StateFlow<String> = _openingBalanceText.asStateFlow()

    private val _currencyCode = MutableStateFlow(Currency.DEFAULT)
    val currencyCode: StateFlow<String> = _currencyCode.asStateFlow()

    val currencies: StateFlow<List<CurrencyEntity>> = currencyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val availableIconKeys: List<String> = IconCatalog.availableKeys

    val canSave: StateFlow<Boolean> = combine(
        _name,
        _openingBalanceText,
    ) { name, opening ->
        name.isNotBlank() && Money.parseToMinor(opening) != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        if (editId != null) {
            viewModelScope.launch {
                accountDao.byId(editId)?.let { account ->
                    original = account
                    _name.value = account.name
                    _iconKey.value = account.iconKey
                    _openingBalanceText.value = Money.format(account.openingBalanceMinor)
                    _currencyCode.value = account.currencyCode
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _name.value = value
    }

    fun onIconSelect(key: String) {
        _iconKey.value = key
    }

    fun onOpeningBalanceChange(value: String) {
        _openingBalanceText.value = value
    }

    fun onCurrencySelect(code: String) {
        _currencyCode.value = code
    }

    fun save() {
        if (!canSave.value) return
        val opening = Money.parseToMinor(_openingBalanceText.value) ?: return
        viewModelScope.launch {
            accountDao.upsert(
                AccountEntity(
                    id = editId ?: UUID.randomUUID().toString(),
                    name = _name.value.trim(),
                    iconKey = _iconKey.value,
                    openingBalanceMinor = opening,
                    currencyCode = _currencyCode.value,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            _saved.value = true
        }
    }

    fun delete() {
        val account = original ?: return
        viewModelScope.launch {
            accountDao.upsert(account.copy(deleted = true, updatedAt = System.currentTimeMillis()))
            _saved.value = true
        }
    }
}
