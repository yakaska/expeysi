package com.expeysi.app.ui.categoryoperations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.analysis.AnalysisRepository
import com.expeysi.app.data.analysis.Operation
import com.expeysi.app.data.analysis.OperationPage
import com.expeysi.app.domain.TxType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Группа операций за один день. */
data class OperationDateGroup(
    val date: LocalDate,
    val operations: List<Operation>,
)

/** Готовое состояние экрана операций категории. */
data class CategoryOperationsUiState(
    val categoryName: String = "",
    /** Метка периода из отчёта, например «Апрель 2026» или «Неделя». */
    val periodLabel: String = "",
    val totalMinor: Long = 0,
    val currencySymbol: String = "",
    val groups: List<OperationDateGroup> = emptyList(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isError: Boolean = false,
)

@HiltViewModel
class CategoryOperationsViewModel @Inject constructor(
    private val repository: AnalysisRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val balanceId: String? =
        savedStateHandle.get<String>("balance")?.takeUnless { it.isBlank() }
    private val categoryId: String = savedStateHandle.get<String>("categoryId").orEmpty()
    private val type: TxType =
        TxType.fromWire(savedStateHandle.get<String>("type") ?: TxType.EXPENSE.wire)
    private val startMillis: Long? =
        savedStateHandle.get<String>("start")?.trim()?.takeUnless { it.isBlank() }?.toLongOrNull()
    private val endMillisExclusive: Long? =
        savedStateHandle.get<String>("end")?.trim()?.takeUnless { it.isBlank() }?.toLongOrNull()
    private val periodLabel: String = savedStateHandle.get<String>("label").orEmpty()
    private val accountIds: Set<String>? = savedStateHandle.get<String>("accounts")
        ?.split(',')?.filter { it.isNotBlank() }?.toSet()?.takeIf { it.isNotEmpty() }

    private val zone: ZoneId = ZoneId.systemDefault()

    private var job: Job? = null
    private val loaded = mutableListOf<Operation>()

    private val _uiState = MutableStateFlow(CategoryOperationsUiState(periodLabel = periodLabel))
    val uiState: StateFlow<CategoryOperationsUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        job?.cancel()
        loaded.clear()
        _uiState.update { it.copy(isLoading = true, isLoadingMore = false, isError = false) }
        job = viewModelScope.launch {
            val result = runCatching {
                repository.categoryOperations(
                    balanceId = balanceId,
                    categoryId = categoryId,
                    type = type,
                    startMillis = startMillis,
                    endMillisExclusive = endMillisExclusive,
                    accountIds = accountIds,
                    limit = PAGE_SIZE,
                    offset = 0,
                )
            }
            ensureActive()
            result.fold(
                onSuccess = { page -> applyPage(page) },
                onFailure = { failure ->
                    if (failure is CancellationException) throw failure
                    _uiState.update {
                        it.copy(isLoading = false, isLoadingMore = false, isError = true)
                    }
                },
            )
        }
    }

    fun loadNext() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.isError || !state.hasMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        val offset = loaded.size
        job = viewModelScope.launch {
            val result = runCatching {
                repository.categoryOperations(
                    balanceId = balanceId,
                    categoryId = categoryId,
                    type = type,
                    startMillis = startMillis,
                    endMillisExclusive = endMillisExclusive,
                    accountIds = accountIds,
                    limit = PAGE_SIZE,
                    offset = offset,
                )
            }
            ensureActive()
            result.fold(
                onSuccess = { page -> applyPage(page) },
                onFailure = { failure ->
                    if (failure is CancellationException) throw failure
                    _uiState.update { it.copy(isLoadingMore = false) }
                },
            )
        }
    }

    private fun applyPage(page: OperationPage) {
        loaded += page.operations
        _uiState.update {
            it.copy(
                categoryName = page.categoryName.ifBlank { it.categoryName },
                totalMinor = page.totalMinor,
                currencySymbol = page.currencySymbol,
                groups = groupsOf(loaded),
                hasMore = page.hasMore,
                isLoading = false,
                isLoadingMore = false,
                isError = false,
            )
        }
    }

    private fun groupsOf(operations: List<Operation>): List<OperationDateGroup> = operations
        .groupBy { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() }
        .map { (date, ops) -> OperationDateGroup(date, ops) }
        .sortedByDescending { it.date }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
