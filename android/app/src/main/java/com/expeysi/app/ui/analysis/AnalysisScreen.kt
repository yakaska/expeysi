package com.expeysi.app.ui.analysis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.domain.TxType
import java.time.ZoneId
import java.time.ZonedDateTime

/** Параметры перехода к операциям категории. */
data class OpenCategoryRequest(
    val balanceId: String?,
    val categoryId: String,
    val type: TxType,
    /** Нижняя граница периода включительно; null — без ограничения. */
    val startMillis: Long?,
    /** Верхняя граница периода не включительно; null — без ограничения. */
    val endMillisExclusive: Long?,
    /** Заголовок периода для подзаголовка экрана. */
    val periodLabel: String,
    val accountIds: Set<String>?,
)

@Composable
fun AnalysisScreen(
    onBack: () -> Unit,
    onOpenCategory: (OpenCategoryRequest) -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val context = LocalContext.current
    val getString = { resource: Int -> context.getString(resource) }
    AnalysisContent(
        state = state,
        actions = AnalysisActions(
            onBack = onBack,
            onMenuClick = {},
            onFiltersClick = viewModel::openFilters,
            onFiltersDismiss = viewModel::closeFilters,
            onTypeSelect = viewModel::selectType,
            onPreviousMonth = { viewModel.shiftMonth(-1) },
            onNextMonth = { viewModel.shiftMonth(1) },
            onCenterClick = viewModel::openPeriodPicker,
            onPeriodSelected = viewModel::selectPeriod,
            onRangeSelected = viewModel::selectRange,
            onPeriodPickerDismiss = viewModel::closePeriodPicker,
            onCategoryClick = { category ->
                val (startMillis, endMillisExclusive) = state.period.bounds(ZonedDateTime.now(zone), zone)
                onOpenCategory(
                    OpenCategoryRequest(
                        balanceId = state.balanceId,
                        categoryId = category.categoryId,
                        type = state.type,
                        startMillis = startMillis,
                        endMillisExclusive = endMillisExclusive,
                        periodLabel = state.period.linkLabel(zone, getString),
                        accountIds = state.selectedAccountIds,
                    ),
                )
            },
            onExpandToggle = viewModel::toggleExpanded,
            onDraftAccountToggle = viewModel::toggleDraftAccount,
            onDraftAllAccounts = viewModel::selectAllDraftAccounts,
            onFiltersReset = viewModel::resetDraft,
            onFiltersApply = viewModel::applyFilters,
            onRetry = viewModel::retry,
        ),
    )
}
