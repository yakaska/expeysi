package com.expeysi.app.ui.categoryoperations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CategoryOperationsScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit = {},
    viewModel: CategoryOperationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoryOperationsContent(
        state = state,
        actions = CategoryOperationsActions(
            onBack = onBack,
            onOperationClick = { operation -> onItemClick(operation.id) },
            onRetry = viewModel::reload,
            onLoadNext = viewModel::loadNext,
        ),
    )
}
