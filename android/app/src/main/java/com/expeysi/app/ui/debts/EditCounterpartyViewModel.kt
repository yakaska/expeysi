package com.expeysi.app.ui.debts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.db.CounterpartyDao
import com.expeysi.app.data.db.CounterpartyEntity
import com.expeysi.app.data.sync.SyncEngine
import com.expeysi.app.domain.CounterpartyType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EditCounterpartyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val counterpartyDao: CounterpartyDao,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val editId: String? = savedStateHandle["counterpartyId"]
    val isEdit: Boolean = editId != null

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _type = MutableStateFlow(CounterpartyType.PERSON)
    val type: StateFlow<CounterpartyType> = _type.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    /** Созданный или изменённый контрагент после сохранения: null — ещё не сохранён. */
    private val _savedCounterpartyId = MutableStateFlow<String?>(null)
    val savedCounterpartyId: StateFlow<String?> = _savedCounterpartyId.asStateFlow()

    init {
        viewModelScope.launch {
            if (editId != null) {
                counterpartyDao.byId(editId)?.let { counterparty ->
                    _name.value = counterparty.name
                    _type.value = CounterpartyType.fromWire(counterparty.type)
                    _note.value = counterparty.note
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _name.value = value
    }

    fun onTypeChange(value: CounterpartyType) {
        _type.value = value
    }

    fun onNoteChange(value: String) {
        _note.value = value
    }

    fun save(nameError: () -> Unit) {
        val trimmed = _name.value.trim()
        if (trimmed.isEmpty()) {
            nameError()
            return
        }
        viewModelScope.launch {
            val counterparty = CounterpartyEntity(
                id = editId ?: UUID.randomUUID().toString(),
                name = trimmed,
                type = _type.value.wire,
                note = _note.value.trim(),
                // Правки сохраняются поверх текущего архивного состояния.
                archived = editId?.let { counterpartyDao.byId(it)?.archived } ?: false,
                updatedAt = System.currentTimeMillis(),
            )
            counterpartyDao.upsert(counterparty)
            syncEngine.uploadCounterparty(counterparty)
            _savedCounterpartyId.value = counterparty.id
        }
    }
}
