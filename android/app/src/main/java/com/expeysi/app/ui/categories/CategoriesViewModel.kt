package com.expeysi.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.domain.TxType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CategoriesUiState(
    val expense: List<CategoryEntity> = emptyList(),
    val income: List<CategoryEntity> = emptyList(),
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    categoryDao: CategoryDao,
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = categoryDao.observeAll()
        .map { categories ->
            val active = categories.filter { !it.deleted }
            CategoriesUiState(
                expense = active.filter { it.type == TxType.EXPENSE.wire }.sortedBy { it.name },
                income = active.filter { it.type == TxType.INCOME.wire }.sortedBy { it.name },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())
}
