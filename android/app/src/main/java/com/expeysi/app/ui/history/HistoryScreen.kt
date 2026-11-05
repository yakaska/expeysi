package com.expeysi.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.domain.AccountTotal
import com.expeysi.app.domain.CategoryTotal
import com.expeysi.app.domain.DateRangeFilter
import com.expeysi.app.domain.HistoryPeriod
import com.expeysi.app.domain.Money
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.home.TransactionItem
import com.expeysi.app.ui.icons.CategoryIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")

private val periodLabels = linkedMapOf(
    HistoryPeriod.DAY to "День",
    HistoryPeriod.WEEK to "Неделя",
    HistoryPeriod.MONTH to "Месяц",
    HistoryPeriod.QUARTER to "Квартал",
    HistoryPeriod.HALF_YEAR to "Полгода",
    HistoryPeriod.YEAR to "Год",
    HistoryPeriod.ALL to "Всё время",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val customRange by viewModel.customRange.collectAsStateWithLifecycle()
    val selectedAccountIds by viewModel.selectedAccountIds.collectAsStateWithLifecycle()

    var showRangePicker by remember { mutableStateOf(false) }
    var showPeriodDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = state.detail != null) { viewModel.closeDetail() }

    if (showPeriodDialog) {
        ModalBottomSheet(
            onDismissRequest = { showPeriodDialog = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Text(
                "Период",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                periodLabels.forEach { (value, label) ->
                    val selected = customRange == null && period == value
                    ListItem(
                        headlineContent = {
                            Text(
                                label,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        trailingContent = {
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = "Выбрано")
                            }
                        },
                        modifier = Modifier.clickable {
                            viewModel.selectPeriod(value)
                            showPeriodDialog = false
                        },
                    )
                }
                ListItem(
                    headlineContent = { Text("Диапазон дат…") },
                    trailingContent = {
                        if (customRange != null) {
                            Icon(Icons.Filled.Check, contentDescription = "Выбрано")
                        }
                    },
                    modifier = Modifier.clickable {
                        showPeriodDialog = false
                        showRangePicker = true
                    },
                )
            }
        }
    }

    if (showRangePicker) {
        val rangeState = rememberDateRangePickerState()
        Dialog(onDismissRequest = { showRangePicker = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    DateRangePicker(
                        state = rangeState,
                        modifier = Modifier.weight(1f, fill = false),
                        title = null,
                        headline = null,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showRangePicker = false }) { Text("Отмена") }
                        TextButton(
                            onClick = {
                                val start = rangeState.selectedStartDateMillis
                                val end = rangeState.selectedEndDateMillis
                                if (start != null && end != null) {
                                    val startDay = Instant.ofEpochMilli(start).atZone(ZoneId.of("UTC")).toLocalDate()
                                    val endDay = Instant.ofEpochMilli(end).atZone(ZoneId.of("UTC")).toLocalDate()
                                    viewModel.selectRange(startDay, endDay)
                                }
                                showRangePicker = false
                            },
                        ) { Text("ОК") }
                    }
                }
            }
        }
    }

    val modes = buildList {
        add(HistoryViewMode.LIST to "Операции")
        add(HistoryViewMode.CATEGORIES to "Категории")
        if (!state.isAccountMode) add(HistoryViewMode.ACCOUNTS to "Счета")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { viewModel.selectMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) {
                        Text(label)
                    }
                }
            }

            OutlinedCard(
                onClick = { showPeriodDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        "Период",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        customRange?.let { rangeLabel(it) } ?: periodLabels.getValue(period),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Выбрать период")
                }
            }
            if (!state.isAccountMode && state.accounts.size > 1 && state.mode != HistoryViewMode.ACCOUNTS) {
                ChipRow {
                    state.accounts.forEach { account ->
                        FilterChip(
                            selected = selectedAccountIds?.contains(account.id) != false,
                            onClick = { viewModel.toggleAccount(account.id) },
                            label = { Text(account.name) },
                        )
                    }
                }
            }

            when {
                state.detail != null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        IconButton(onClick = viewModel::closeDetail) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К группам")
                        }
                        Text(state.detailTitle, style = MaterialTheme.typography.titleMedium)
                    }
                    TransactionList(state.items, onItemClick)
                }
                state.mode == HistoryViewMode.LIST -> TransactionList(state.items, onItemClick)
                state.mode == HistoryViewMode.CATEGORIES -> CategoriesView(
                    state = state,
                    onCategoryClick = { viewModel.openDetail(it) },
                )
                else -> AccountsView(
                    state = state,
                    onCategoryClick = { categoryId, accountId -> viewModel.openDetail(categoryId, accountId) },
                )
            }
        }
    }
}

@Composable
private fun TransactionList(items: List<TransactionItem>, onItemClick: (String) -> Unit) {
    if (items.isEmpty()) {
        EmptyHistoryText()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.transaction.id }) { item ->
                HistoryItemCard(item = item, onClick = { onItemClick(item.transaction.id) })
            }
        }
    }
}

@Composable
private fun CategoriesView(
    state: HistoryUiState,
    onCategoryClick: (String) -> Unit,
) {
    if (state.categoryTotals.isEmpty()) {
        EmptyHistoryText()
        return
    }
    val categoryById = state.categories.associateBy { it.id }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Всего потрачено",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${Money.format(state.totalExpenseMinor)} ${state.currencySymbol}",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }
        items(state.categoryTotals, key = { it.categoryId }) { total ->
            val category = categoryById[total.categoryId]
            Card(
                onClick = { onCategoryClick(total.categoryId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                ListItem(
                    leadingContent = {
                        CategoryIcon(category?.iconKey ?: "", category?.colorKey ?: "default")
                    },
                    headlineContent = { Text(category?.name ?: "Без категории") },
                    trailingContent = {
                        Text(
                            "${Money.format(total.expenseMinor)} ${state.currencySymbol}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountsView(
    state: HistoryUiState,
    onCategoryClick: (categoryId: String, accountId: String) -> Unit,
) {
    if (state.accountTotals.isEmpty()) {
        EmptyHistoryText()
        return
    }
    var expandedAccounts by remember { mutableStateOf(setOf<String>()) }
    val accountById = state.accounts.associateBy { it.id }
    val categoryById = state.categories.associateBy { it.id }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.accountTotals, key = { it.accountId }) { total ->
            AccountCard(
                total = total,
                accountName = accountById[total.accountId]?.name ?: "Счёт",
                accountIconKey = accountById[total.accountId]?.iconKey ?: "",
                accountColorKey = accountById[total.accountId]?.colorKey ?: "default",
                currencySymbol = state.currencySymbol,
                expanded = total.accountId in expandedAccounts,
                onToggle = {
                    expandedAccounts = if (total.accountId in expandedAccounts) {
                        expandedAccounts - total.accountId
                    } else {
                        expandedAccounts + total.accountId
                    }
                },
                categories = total.categories.map { categoryTotal ->
                    AccountCategoryRow(
                        total = categoryTotal,
                        name = categoryById[categoryTotal.categoryId]?.name ?: "Без категории",
                        iconKey = categoryById[categoryTotal.categoryId]?.iconKey ?: "",
                        colorKey = categoryById[categoryTotal.categoryId]?.colorKey ?: "default",
                    )
                },
                onCategoryClick = onCategoryClick,
            )
        }
    }
}

private data class AccountCategoryRow(
    val total: CategoryTotal,
    val name: String,
    val iconKey: String,
    val colorKey: String,
)

@Composable
private fun AccountCard(
    total: AccountTotal,
    accountName: String,
    accountIconKey: String,
    accountColorKey: String,
    currencySymbol: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    categories: List<AccountCategoryRow>,
    onCategoryClick: (categoryId: String, accountId: String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.clickable(onClick = onToggle),
            leadingContent = {
                CategoryIcon(accountIconKey, accountColorKey)
            },
            headlineContent = { Text(accountName) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${Money.format(total.expenseMinor)} $currencySymbol",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    )
                }
            },
        )
        if (expanded) {
            categories.forEach { row ->
                ListItem(
                    modifier = Modifier.clickable { onCategoryClick(row.total.categoryId, total.accountId) },
                    leadingContent = {
                        CategoryIcon(
                            row.iconKey,
                            row.colorKey,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    },
                    headlineContent = { Text(row.name) },
                    trailingContent = {
                        Text(
                            "${Money.format(row.total.expenseMinor)} $currencySymbol",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryText() {
    Text(
        "Нет операций за выбранный период.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

@Composable
private fun HistoryItemCard(item: TransactionItem, onClick: () -> Unit) {
    val isIncome = TxType.fromWire(item.transaction.type) == TxType.INCOME
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIcon(item.categoryIconKey, item.categoryColorKey)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(item.categoryName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(
                        item.accountName.ifBlank { null },
                        Instant.ofEpochMilli(item.transaction.date)
                            .atZone(ZoneId.systemDefault())
                            .format(dateFormatter),
                        item.transaction.note.ifBlank { null },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = (if (isIncome) "+" else "−") +
                    "${Money.format(item.transaction.amountMinor)} ${item.currencySymbol}",
                style = MaterialTheme.typography.titleMedium,
                color = if (isIncome) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private fun rangeLabel(range: DateRangeFilter): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(range.startMillis).atZone(zone).format(dayFormatter)
    val end = Instant.ofEpochMilli(range.endMillisExclusive - 1).atZone(zone).format(dayFormatter)
    return "$start–$end"
}
