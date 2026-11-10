package com.expeysi.app.ui.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.SelectedBalanceStore
import com.expeysi.app.data.analysis.AnalysisAccount
import com.expeysi.app.data.analysis.AnalysisRepository
import com.expeysi.app.data.analysis.CategorySummary
import com.expeysi.app.domain.DateRangeFilter
import com.expeysi.app.domain.HistoryPeriod
import com.expeysi.app.domain.TxType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Готовое состояние экрана «Анализ финансов»; отображение от него зависит,
 * а не от бэкенда. Отчёт всегда в рамках одного выбранного баланса.
 */
data class AnalysisUiState(
    val balanceId: String? = null,
    val type: TxType = TxType.EXPENSE,
    val period: AnalysisPeriod = AnalysisPeriod(month = YearMonth.now()),
    /** Текущий месяц устройства: ограничение навигации вперёд. */
    val currentMonth: YearMonth = YearMonth.now(),
    val accounts: List<AnalysisAccount> = emptyList(),
    /** Применённая фильтрация счетов; null — все счета баланса. */
    val selectedAccountIds: Set<String>? = null,
    /** Черновик фильтрации в открытой шторке. */
    val filterDraft: Set<String>? = null,
    val filtersOpen: Boolean = false,
    val periodPickerOpen: Boolean = false,
    val summary: CategorySummary? = null,
    /** Самая ранняя операция баланса: управляет стрелкой «предыдущий месяц». */
    val earliestMillis: Long? = null,
    val isRefreshing: Boolean = false,
    val isError: Boolean = false,
    val expanded: Boolean = false,
) {
    /** Баланса нет: вместо отчёта — статусный экран. */
    val isBalanceMissing: Boolean
        get() = balanceId == null

    /** Первое открытие без данных: вместо контента — скелетоны. */
    val isFirstLoad: Boolean
        get() = summary == null && !isError && !isBalanceMissing

    /** Стрелка «следующий месяц» видна, пока есть куда листать вперёд. */
    val nextMonthVisible: Boolean
        get() = period.kind == AnalysisPeriodKind.MONTHS && period.month.isBefore(currentMonth)
}

/** Семантика выбора счетов: null означает «все счета». */
object AccountSelection {

    /** Переключение одного счёта; выбор всех счетов вручную сводится к «все счета». */
    fun toggle(current: Set<String>?, allIds: Set<String>, id: String): Set<String>? {
        val base = current ?: allIds
        val updated = if (id in base) base - id else base + id
        return if (updated == allIds) null else updated
    }
}

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val repository: AnalysisRepository,
    private val selectedBalanceStore: SelectedBalanceStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val currentMonth: YearMonth = YearMonth.now(zone)

    private val _uiState = MutableStateFlow(
        AnalysisUiState(
            period = restorePeriod() ?: AnalysisPeriod(month = currentMonth),
            currentMonth = currentMonth,
            selectedAccountIds = parseAccountIds(savedStateHandle.get<String>(KEY_ACCOUNTS)),
            expanded = savedStateHandle.get<Boolean>(KEY_EXPANDED) ?: false,
        ),
    )
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        selectedBalanceStore.selectedId
            .onEach { balanceId -> onBalanceChanged(balanceId) }
            .launchIn(viewModelScope)
    }

    fun selectType(type: TxType) {
        if (_uiState.value.type == type) return
        _uiState.update { it.copy(type = type) }
        persistPeriod()
        reload()
    }

    fun shiftMonth(delta: Int) {
        val state = _uiState.value
        if (state.period.kind != AnalysisPeriodKind.MONTHS) return
        val next = state.period.month.plusMonths(delta.toLong())
        if (next.isAfter(currentMonth)) return
        _uiState.update { it.copy(period = it.period.copy(month = next)) }
        persistPeriod()
        reload()
    }

    fun openPeriodPicker() {
        _uiState.update { it.copy(periodPickerOpen = true) }
    }

    fun closePeriodPicker() {
        _uiState.update { it.copy(periodPickerOpen = false) }
    }

    fun selectPeriod(value: HistoryPeriod) {
        val next = if (value == HistoryPeriod.MONTH) {
            AnalysisPeriod(month = currentMonth)
        } else {
            AnalysisPeriod(kind = AnalysisPeriodKind.SLIDING, sliding = value)
        }
        _uiState.update { it.copy(period = next, periodPickerOpen = false) }
        persistPeriod()
        reload()
    }

    fun selectRange(start: LocalDate, endInclusive: LocalDate) {
        val range = DateRangeFilter.ofDays(start, endInclusive, zone)
        _uiState.update {
            it.copy(
                period = AnalysisPeriod(kind = AnalysisPeriodKind.RANGE, range = range),
                periodPickerOpen = false,
            )
        }
        persistPeriod()
        reload()
    }

    fun toggleExpanded() {
        _uiState.update { it.copy(expanded = !it.expanded) }
        persistPeriod()
    }

    fun openFilters() {
        _uiState.update { it.copy(filtersOpen = true, filterDraft = it.selectedAccountIds) }
    }

    fun closeFilters() {
        _uiState.update { it.copy(filtersOpen = false, filterDraft = null) }
    }

    fun toggleDraftAccount(accountId: String) {
        val allIds = _uiState.value.accounts.map { it.id }.toSet()
        if (allIds.isEmpty()) return
        val updated = AccountSelection.toggle(_uiState.value.filterDraft, allIds, accountId)
        _uiState.update { it.copy(filterDraft = updated) }
    }

    fun selectAllDraftAccounts() {
        _uiState.update { it.copy(filterDraft = null) }
    }

    fun resetDraft() {
        _uiState.update { it.copy(filterDraft = it.selectedAccountIds) }
    }

    fun applyFilters() {
        val current = _uiState.value
        val draft = current.filterDraft
        _uiState.update {
            it.copy(filtersOpen = false, filterDraft = null, selectedAccountIds = draft)
        }
        persistPeriod()
        if (draft != current.selectedAccountIds) reload()
    }

    fun retry() {
        reload()
    }

    /** Смена баланса на главном экране: перезагрузка и сброс выбора счетов. */
    private fun onBalanceChanged(balanceId: String?) {
        if (_uiState.value.balanceId == balanceId) return
        _uiState.update {
            it.copy(
                balanceId = balanceId,
                accounts = emptyList(),
                selectedAccountIds = null,
                filterDraft = null,
                filtersOpen = false,
            )
        }
        savedStateHandle[KEY_ACCOUNTS] = ""
        reload()
    }

    private fun reload() {
        job?.cancel()
        val snapshot = _uiState.value
        if (snapshot.balanceId == null) {
            _uiState.update { it.copy(summary = null, isRefreshing = false, isError = false) }
            return
        }
        val initial = snapshot.summary == null
        if (initial) {
            _uiState.update { it.copy(isError = false) }
        } else {
            _uiState.update { it.copy(isRefreshing = true, isError = false) }
        }
        val params = _uiState.value
        val (startMillis, endMillisExclusive) = params.period.bounds(ZonedDateTime.now(zone), zone)
        job = viewModelScope.launch {
            val result = runCatching {
                val accounts = params.accounts.ifEmpty {
                    try {
                        repository.accounts(params.balanceId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                val summary = repository.categorySummary(
                    balanceId = params.balanceId,
                    type = params.type,
                    startMillis = startMillis,
                    endMillisExclusive = endMillisExclusive,
                    accountIds = params.selectedAccountIds,
                )
                val earliest = repository.earliestOperationMillis(params.balanceId)
                Triple(accounts, summary, earliest)
            }
            ensureActive()
            result.fold(
                onSuccess = { (accounts, summary, earliest) ->
                    _uiState.update {
                        it.copy(
                            accounts = accounts,
                            summary = summary,
                            earliestMillis = earliest,
                            isRefreshing = false,
                            isError = false,
                        )
                    }
                },
                onFailure = { failure ->
                    if (failure is CancellationException) throw failure
                    _uiState.update { it.copy(isRefreshing = false, isError = true) }
                },
            )
        }
    }

    private fun persistPeriod() {
        val snapshot = _uiState.value
        savedStateHandle[KEY_TYPE] = snapshot.type.wire
        savedStateHandle[KEY_KIND] = when (snapshot.period.kind) {
            AnalysisPeriodKind.MONTHS -> "months"
            AnalysisPeriodKind.SLIDING -> "sliding"
            AnalysisPeriodKind.RANGE -> "range"
        }
        savedStateHandle[KEY_SLIDING] = snapshot.period.sliding.name
        savedStateHandle[KEY_MONTH] = snapshot.period.month.toString()
        val range = snapshot.period.range
        savedStateHandle[KEY_RANGE] = range?.let { "${it.startMillis}:${it.endMillisExclusive}" } ?: ""
        savedStateHandle[KEY_ACCOUNTS] = snapshot.selectedAccountIds.orEmpty().joinToString(",")
        savedStateHandle[KEY_EXPANDED] = snapshot.expanded
    }

    private fun restorePeriod(): AnalysisPeriod? {
        val month = savedStateHandle.get<String>(KEY_MONTH)
            ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            ?: return null
        return when (savedStateHandle.get<String>(KEY_KIND)) {
            "months" -> AnalysisPeriod(month = month)
            "sliding" -> {
                val sliding = savedStateHandle.get<String>(KEY_SLIDING)
                    ?.let { name -> HistoryPeriod.entries.firstOrNull { it.name == name } }
                    ?: return null
                AnalysisPeriod(kind = AnalysisPeriodKind.SLIDING, sliding = sliding)
            }
            "range" -> {
                val raw = savedStateHandle.get<String>(KEY_RANGE) ?: return null
                val parts = raw.split(':')
                if (parts.size != 2) return null
                val start = parts[0].toLongOrNull() ?: return null
                val end = parts[1].toLongOrNull() ?: return null
                AnalysisPeriod(kind = AnalysisPeriodKind.RANGE, range = DateRangeFilter(start, end))
            }
            else -> null
        }
    }

    private fun parseAccountIds(value: String?): Set<String>? = value?.split(',')?.filter { it.isNotBlank() }?.toSet()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val KEY_TYPE = "analysis_type"
        const val KEY_KIND = "analysis_period_kind"
        const val KEY_SLIDING = "analysis_period_sliding"
        const val KEY_MONTH = "analysis_period_month"
        const val KEY_RANGE = "analysis_period_range"
        const val KEY_ACCOUNTS = "analysis_accounts"
        const val KEY_EXPANDED = "analysis_expanded"
    }
}
