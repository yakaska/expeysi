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
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    val currencies: StateFlow<List<CurrencyEntity>> = currencyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _currencyCode = MutableStateFlow(Currency.DEFAULT)
    val currencyCode: StateFlow<String> = _currencyCode.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val canSave: StateFlow<Boolean> = _name
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        if (editId != null) {
            viewModelScope.launch {
                balanceDao.byId(editId)?.let { balance ->
                    original = balance
                    _name.value = balance.name
                    _currencyCode.value = balance.currencyCode
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

    fun save() {
        if (!canSave.value) return
        viewModelScope.launch {
            val balance = BalanceEntity(
                id = editId ?: UUID.randomUUID().toString(),
                name = _name.value.trim(),
                currencyCode = _currencyCode.value,
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
}
