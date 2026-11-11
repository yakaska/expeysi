package com.expeysi.app.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.sync.SyncEngine
import com.expeysi.app.domain.Money
import com.expeysi.app.ui.icons.CategoryPalette
import com.expeysi.app.ui.icons.IconCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EditAccountViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountDao: AccountDao,
    balanceDao: BalanceDao,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val editId: String? = savedStateHandle["accountId"]

    /** Баланс, выбранный до перехода (например, из hero-карточки); null — без предвыбора. */
    private val prefilledBalanceId: String? = savedStateHandle["balanceId"]
    private var original: AccountEntity? = null

    val isEdit: Boolean = editId != null

    val balances: StateFlow<List<BalanceEntity>> = balanceDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _iconKey = MutableStateFlow("payments")
    val iconKey: StateFlow<String> = _iconKey.asStateFlow()

    private val _colorKey = MutableStateFlow(CategoryPalette.DEFAULT_KEY)
    val colorKey: StateFlow<String> = _colorKey.asStateFlow()

    private val _openingBalanceText = MutableStateFlow("0")
    val openingBalanceText: StateFlow<String> = _openingBalanceText.asStateFlow()

    private val _balanceId = MutableStateFlow<String?>(null)
    val balanceId: StateFlow<String?> = _balanceId.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val availableIconKeys: List<String> = IconCatalog.availableKeys

    val canSave: StateFlow<Boolean> = combine(
        _name,
        _openingBalanceText,
        _balanceId,
    ) { name, opening, balanceId ->
        name.isNotBlank() && Money.parseToMinor(opening) != null && balanceId != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        if (editId != null) {
            viewModelScope.launch {
                accountDao.byId(editId)?.let { account ->
                    original = account
                    _name.value = account.name
                    _iconKey.value = account.iconKey
                    _colorKey.value = account.colorKey
                    _openingBalanceText.value = Money.formatInput(Money.formatAuto(account.openingBalanceMinor))
                    _balanceId.value = account.balanceId
                }
            }
        } else {
            // Новый счёт: предвыбранный баланс, если он есть среди активных, иначе первый активный.
            viewModelScope.launch {
                val active = balances.first { it.isNotEmpty() }
                _balanceId.value = active.firstOrNull { it.id == prefilledBalanceId }?.id
                    ?: active.first().id
            }
        }
    }

    fun onNameChange(value: String) {
        _name.value = value
    }

    fun onIconSelect(key: String) {
        _iconKey.value = key
    }

    fun onColorSelect(key: String) {
        _colorKey.value = key
    }

    fun onOpeningBalanceChange(value: String) {
        _openingBalanceText.value = Money.formatInput(value)
    }

    fun onBalanceSelect(id: String) {
        _balanceId.value = id
    }

    fun save() {
        if (!canSave.value) return
        val opening = Money.parseToMinor(_openingBalanceText.value) ?: return
        val balanceId = _balanceId.value ?: return
        viewModelScope.launch {
            val account = AccountEntity(
                id = editId ?: UUID.randomUUID().toString(),
                name = _name.value.trim(),
                iconKey = _iconKey.value,
                colorKey = _colorKey.value,
                openingBalanceMinor = opening,
                balanceId = balanceId,
                updatedAt = System.currentTimeMillis(),
            )
            accountDao.upsert(account)
            syncEngine.uploadAccount(account)
            _saved.value = true
        }
    }

    fun delete() {
        val account = original ?: return
        viewModelScope.launch {
            val deleted = account.copy(deleted = true, updatedAt = System.currentTimeMillis())
            accountDao.upsert(deleted)
            syncEngine.uploadAccount(deleted)
            _saved.value = true
        }
    }
}
