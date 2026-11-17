package com.expeysi.app.ui.categoryoperations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CategoryOperationsScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit = {},
    /** Строка списания долга ведёт в редактор корректировки, а не транзакции. */
    onWriteOffClick: (String) -> Unit = {},
    viewModel: CategoryOperationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoryOperationsContent(
        state = state,
        actions = CategoryOperationsActions(
            onBack = onBack,
            onOperationClick = { operation ->
                if (operation.isWriteOff) onWriteOffClick(operation.id) else onItemClick(operation.id)
            },
            onRetry = viewModel::reload,
            onLoadNext = viewModel::loadNext,
        ),
    )
}
