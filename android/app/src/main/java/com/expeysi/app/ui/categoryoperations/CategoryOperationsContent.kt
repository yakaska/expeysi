package com.expeysi.app.ui.categoryoperations

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.expeysi.app.R
import com.expeysi.app.data.analysis.Operation
import com.expeysi.app.ui.analysis.ErrorState
import com.expeysi.app.ui.analysis.SkeletonBar
import com.expeysi.app.ui.common.AmountFormat
import com.expeysi.app.ui.common.RuDates
import com.expeysi.app.ui.icons.CategoryIcon
import com.expeysi.app.ui.theme.ExpeysiTheme
import com.expeysi.app.ui.theme.LocalFinanceColors
import java.time.LocalDate
import java.time.ZoneId

/** События экрана операций категории. */
data class CategoryOperationsActions(
    val onBack: () -> Unit = {},
    val onOperationClick: (Operation) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLoadNext: () -> Unit = {},
)

/** Готовое состояние + события; отрисовка без логики и доступа к данным. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryOperationsContent(
    state: CategoryOperationsUiState,
    actions: CategoryOperationsActions,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.categoryName.ifBlank {
                                stringResource(R.string.report_uncategorized)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.categoryops_subtitle,
                                state.periodLabel,
                                AmountFormat.withCurrency(state.totalMinor, state.currencySymbol),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isError && state.groups.isEmpty() -> ErrorState(onRetry = actions.onRetry)
                state.isLoading -> OperationsSkeleton()
                state.groups.isEmpty() -> Text(
                    text = stringResource(R.string.categoryops_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 16.dp),
                )
                else -> OperationsList(
                    state = state,
                    onOperationClick = actions.onOperationClick,
                    onLoadNext = actions.onLoadNext,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OperationsList(
    state: CategoryOperationsUiState,
    onOperationClick: (Operation) -> Unit,
    onLoadNext: () -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadNext by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(shouldLoadNext) {
        if (shouldLoadNext) onLoadNext()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        state.groups.forEach { group ->
            stickyHeader(key = "date-${group.date}") {
                Text(
                    text = RuDates.dayHeader(group.date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(group.operations, key = { it.id }) { operation ->
                OperationRow(
                    operation = operation,
                    currencySymbol = state.currencySymbol,
                    onClick = { onOperationClick(operation) },
                )
            }
        }
        if (state.isLoadingMore) {
            item(key = "loading-more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationRow(
    operation: Operation,
    currencySymbol: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(iconKey = operation.iconKey, colorKey = operation.colorKey)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = operation.title.ifBlank { stringResource(R.string.report_uncategorized) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = operation.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = (if (operation.isIncome) "+" else "") +
                AmountFormat.withCurrency(operation.amountMinor, currencySymbol),
            style = MaterialTheme.typography.titleMedium,
            color = if (operation.isIncome) {
                LocalFinanceColors.current.income
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            softWrap = false,
        )
    }
}

@Composable
private fun OperationsSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(2) {
            SkeletonBar(width = 120.dp, height = 14.dp, modifier = Modifier.padding(start = 16.dp))
            repeat(3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        SkeletonBar(width = 160.dp, height = 14.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        SkeletonBar(width = 100.dp, height = 12.dp)
                    }
                    SkeletonBar(width = 72.dp, height = 16.dp)
                }
            }
        }
    }
}

private fun operationMillis(year: Int, month: Int, day: Int, hour: Int = 12): Long = LocalDate.of(year, month, day)
    .atTime(hour, 0)
    .atZone(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()

private fun previewState(
    groups: List<OperationDateGroup>,
    hasMore: Boolean = false,
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    isError: Boolean = false,
): CategoryOperationsUiState = CategoryOperationsUiState(
    categoryName = "Еда",
    periodLabel = "Апрель 2026",
    totalMinor = 7_844_900,
    currencySymbol = "₽",
    groups = groups,
    hasMore = hasMore,
    isLoading = isLoading,
    isLoadingMore = isLoadingMore,
    isError = isError,
)

private val previewGroups = listOf(
    OperationDateGroup(
        date = LocalDate.of(2026, 4, 10),
        operations = listOf(
            Operation(
                id = "t1",
                dateMillis = operationMillis(2026, 4, 10, 14),
                title = "Еда",
                subtitle = "Пятёрочка",
                amountMinor = 128_600,
                iconKey = "restaurant",
                colorKey = "orange",
                isIncome = false,
            ),
            Operation(
                id = "t2",
                dateMillis = operationMillis(2026, 4, 10, 9),
                title = "Еда",
                subtitle = "Кофе с собой",
                amountMinor = 26_000,
                iconKey = "restaurant",
                colorKey = "orange",
                isIncome = false,
            ),
        ),
    ),
    OperationDateGroup(
        date = LocalDate.of(2026, 4, 9),
        operations = listOf(
            Operation(
                id = "t3",
                dateMillis = operationMillis(2026, 4, 9, 19),
                title = "Еда",
                subtitle = "Пиццерия",
                amountMinor = 214_000,
                iconKey = "restaurant",
                colorKey = "orange",
                isIncome = false,
            ),
        ),
    ),
)

private val previewIncomeGroups = listOf(
    OperationDateGroup(
        date = LocalDate.of(2026, 4, 9),
        operations = listOf(
            Operation(
                id = "t4",
                dateMillis = operationMillis(2026, 4, 9, 11),
                title = "Зарплата",
                subtitle = "Апрель",
                amountMinor = 12_000_000,
                iconKey = "work",
                colorKey = "green",
                isIncome = true,
            ),
        ),
    ),
)

@Preview(name = "Операции", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoryOperationsContentPreview() {
    ExpeysiTheme(darkTheme = true) {
        CategoryOperationsContent(state = previewState(previewGroups), actions = CategoryOperationsActions())
    }
}

@Preview(name = "Доходы", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoryOperationsIncomePreview() {
    ExpeysiTheme(darkTheme = true) {
        CategoryOperationsContent(
            state = CategoryOperationsUiState(
                categoryName = "Зарплата",
                periodLabel = "Апрель 2026",
                totalMinor = 12_000_000,
                currencySymbol = "₽",
                groups = previewIncomeGroups,
            ),
            actions = CategoryOperationsActions(),
        )
    }
}

@Preview(name = "Загрузка", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoryOperationsLoadingPreview() {
    ExpeysiTheme(darkTheme = true) {
        CategoryOperationsContent(
            state = previewState(groups = emptyList(), isLoading = true),
            actions = CategoryOperationsActions(),
        )
    }
}

@Preview(name = "Пусто", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoryOperationsEmptyPreview() {
    ExpeysiTheme(darkTheme = true) {
        CategoryOperationsContent(
            state = previewState(groups = emptyList()),
            actions = CategoryOperationsActions(),
        )
    }
}

@Preview(name = "Ошибка", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoryOperationsErrorPreview() {
    ExpeysiTheme(darkTheme = true) {
        CategoryOperationsContent(
            state = previewState(groups = emptyList(), isError = true),
            actions = CategoryOperationsActions(),
        )
    }
}
