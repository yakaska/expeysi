package com.expeysi.app.ui.categoryreport

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.domain.Money
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.history.PeriodSelector
import com.expeysi.app.ui.home.TransactionItem
import com.expeysi.app.ui.icons.CategoryIcon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val reportDayFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))
private val reportTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun CategoryReportScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: CategoryReportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val customRange by viewModel.customRange.collectAsStateWithLifecycle()
    var showSelection by remember { mutableStateOf(true) }

    if (showSelection) {
        CategorySelectionContent(
            categories = state.categories,
            selectedCategoryIds = state.selectedCategoryIds,
            onBack = onBack,
            onToggle = viewModel::toggleCategory,
            onConfirm = { showSelection = false },
        )
    } else {
        CategoryReportContent(
            state = state,
            period = period,
            customRange = customRange,
            onBack = onBack,
            onItemClick = onItemClick,
            onPeriodSelected = viewModel::selectPeriod,
            onRangeSelected = viewModel::selectRange,
            onEditCategories = { showSelection = true },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelectionContent(
    categories: List<CategoryEntity>,
    selectedCategoryIds: Set<String>,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выберите категории") },
                navigationIcon = { BackButton(onBack) },
            )
        },
        bottomBar = {
            Button(
                onClick = onConfirm,
                enabled = selectedCategoryIds.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Показать операции")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            listOf(TxType.EXPENSE, TxType.INCOME).forEach { type ->
                val typedCategories = categories.filter { TxType.fromWire(it.type) == type }
                if (typedCategories.isNotEmpty()) {
                    item(key = "header-${type.wire}") {
                        Text(
                            if (type == TxType.EXPENSE) "Расходы" else "Доходы",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    items(typedCategories, key = { it.id }) { category ->
                        ListItem(
                            modifier = Modifier.clickable { onToggle(category.id) },
                            leadingContent = {
                                CategoryIcon(category.iconKey, category.colorKey)
                            },
                            headlineContent = { Text(category.name) },
                            trailingContent = {
                                Checkbox(
                                    checked = category.id in selectedCategoryIds,
                                    onCheckedChange = { onToggle(category.id) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CategoryReportContent(
    state: CategoryReportUiState,
    period: com.expeysi.app.domain.HistoryPeriod,
    customRange: com.expeysi.app.domain.DateRangeFilter?,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    onPeriodSelected: (com.expeysi.app.domain.HistoryPeriod) -> Unit,
    onRangeSelected: (LocalDate, LocalDate) -> Unit,
    onEditCategories: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Операции по категориям") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = onEditCategories) {
                        Icon(Icons.Filled.Category, contentDescription = "Изменить категории")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PeriodSelector(
                period = period,
                customRange = customRange,
                onPeriodSelected = onPeriodSelected,
                onRangeSelected = onRangeSelected,
            )
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "balance") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Баланс",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                "${Money.formatAuto(state.balanceMinor)} ${state.currencySymbol}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (state.balanceMinor < 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                state.currencyCode,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.items.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Нет операций за выбранный период.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else {
                    val zone = ZoneId.systemDefault()
                    val today = LocalDate.now(zone)
                    state.items.groupBy { item ->
                        Instant.ofEpochMilli(item.transaction.date).atZone(zone).toLocalDate()
                    }.forEach { (date, transactions) ->
                        stickyHeader(key = "date-$date") {
                            Text(
                                text = when (date) {
                                    today -> stringResource(R.string.home_today)
                                    today.minusDays(1) -> stringResource(R.string.home_yesterday)
                                    else -> date.format(reportDayFormatter)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(vertical = 8.dp),
                            )
                        }
                        transactions.forEachIndexed { index, item ->
                            item(key = item.transaction.id) {
                                Column(modifier = Modifier.animateItem()) {
                                    if (index > 0) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                    ReportTransactionRow(item, zone, onItemClick)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportTransactionRow(
    item: TransactionItem,
    zone: ZoneId,
    onItemClick: (String) -> Unit,
) {
    val isIncome = TxType.fromWire(item.transaction.type) == TxType.INCOME
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick(item.transaction.id) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(item.categoryIconKey, item.categoryColorKey)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(item.categoryName, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(
                    item.accountName.ifBlank { null },
                    Instant.ofEpochMilli(item.transaction.date)
                        .atZone(zone)
                        .format(reportTimeFormatter),
                    item.transaction.note.ifBlank { null },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = (if (isIncome) "+" else "−") +
                "${Money.formatAuto(item.transaction.amountMinor)} ${item.currencySymbol}",
            style = MaterialTheme.typography.titleMedium,
            color = if (isIncome) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
    }
}
