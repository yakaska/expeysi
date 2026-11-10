package com.expeysi.app.ui.analysis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.domain.TxType
import java.time.YearMonth

/** Параметры перехода к операциям категории. */
data class OpenCategoryRequest(
    val categoryId: String,
    val type: TxType,
    val month: YearMonth,
    val accountIds: Set<String>?,
)

@Composable
fun AnalysisScreen(
    onBack: () -> Unit,
    onOpenCategory: (OpenCategoryRequest) -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
            onCategoryClick = { category ->
                val snapshot = viewModel.uiState.value
                onOpenCategory(
                    OpenCategoryRequest(
                        categoryId = category.categoryId,
                        type = snapshot.type,
                        month = snapshot.month,
                        accountIds = snapshot.selectedAccountIds,
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
