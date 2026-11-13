package com.expeysi.app.ui.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.csv.CsvFormat
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.sync.SyncEngine
import com.expeysi.app.domain.TxType
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
class EditCategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val categoryDao: CategoryDao,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val editId: String? = savedStateHandle["categoryId"]
    private var original: CategoryEntity? = null

    val isEdit: Boolean = editId != null

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _type = MutableStateFlow(
        TxType.fromWire(savedStateHandle["type"] ?: TxType.EXPENSE.wire),
    )
    val type: StateFlow<TxType> = _type.asStateFlow()

    private val _iconKey = MutableStateFlow(IconCatalog.DEFAULT_KEY)
    val iconKey: StateFlow<String> = _iconKey.asStateFlow()

    /** Цвет новой категории: первая ячейка сетки цветов на экране. */
    private val _colorKey = MutableStateFlow(CategoryPalette.availableKeys.first())
    val colorKey: StateFlow<String> = _colorKey.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val availableIconKeys: List<String> = IconCatalog.availableKeys

    /** Служебное имя «Начальный остаток» занято CSV-экспортом, категории с ним быть не может. */
    val canSave: StateFlow<Boolean> = _name
        .map { it.isNotBlank() && !CsvFormat.isOpeningRowCategory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        if (editId != null) {
            viewModelScope.launch {
                categoryDao.byId(editId)?.let { category ->
                    original = category
                    _name.value = category.name
                    _type.value = TxType.fromWire(category.type)
                    _iconKey.value = category.iconKey
                    _colorKey.value = category.colorKey
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _name.value = value
    }

    fun onTypeChange(value: TxType) {
        _type.value = value
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
            val category = CategoryEntity(
                id = editId ?: UUID.randomUUID().toString(),
                name = _name.value.trim(),
                iconKey = _iconKey.value,
                colorKey = _colorKey.value,
                type = _type.value.wire,
                updatedAt = System.currentTimeMillis(),
            )
            categoryDao.upsert(category)
            syncEngine.uploadCategory(category)
            _saved.value = true
        }
    }

    fun delete() {
        val category = original ?: return
        viewModelScope.launch {
            val deleted = category.copy(deleted = true, updatedAt = System.currentTimeMillis())
            categoryDao.upsert(deleted)
            syncEngine.uploadCategory(deleted)
            _saved.value = true
        }
    }
}
