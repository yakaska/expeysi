package com.expeysi.app.ui.transactionreport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.domain.Money
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.history.PeriodSelector
import com.expeysi.app.ui.icons.CategoryIcon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val reportTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val reportDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReportScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: TransactionReportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val customRange by viewModel.customRange.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val modes = listOf(
        TransactionReportMode.OPERATIONS to stringResource(R.string.report_operations),
        TransactionReportMode.CATEGORIES to stringResource(R.string.report_categories),
        TransactionReportMode.ACCOUNTS to stringResource(R.string.home_accounts),
    )
    val selectedCategory = state.categories.firstOrNull { it.id == state.selectedCategoryId }

    BackHandler(enabled = state.selectedCategoryId != null, onBack = viewModel::closeCategory)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        selectedCategory?.name ?: stringResource(
                            if (viewModel.isIncome) R.string.drawer_incomes_report else R.string.drawer_expenses_report,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.selectedCategoryId == null) onBack() else viewModel.closeCategory()
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(
                            if (viewModel.isIncome) R.string.report_total_income else R.string.report_total_expense,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = reportAmountText(state.totalMinor, state.currencySymbol),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                modes.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = mode == value,
                        onClick = { viewModel.selectMode(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) {
                        Text(label)
                    }
                }
            }
            PeriodSelector(
                period = period,
                customRange = customRange,
                onPeriodSelected = viewModel::selectPeriod,
                onRangeSelected = viewModel::selectRange,
            )
            if (mode == TransactionReportMode.OPERATIONS && state.accounts.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    items(state.accounts, key = { it.id }) { account ->
                        FilterChip(
                            selected = state.selectedAccountIds?.contains(account.id) != false,
                            onClick = { viewModel.toggleAccount(account.id) },
                            label = { Text(account.name) },
                        )
                    }
                }
            }
            when {
                mode == TransactionReportMode.OPERATIONS || state.selectedCategoryId != null -> {
                    OperationsReport(state, onItemClick)
                }
                mode == TransactionReportMode.CATEGORIES -> CategoriesReport(
                    state = state,
                    isIncome = viewModel.isIncome,
                    onCategoryClick = viewModel::openCategory,
                )
                else -> AccountsReport(
                    state = state,
                    isIncome = viewModel.isIncome,
                    onCategoryClick = viewModel::openCategory,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OperationsReport(
    state: TransactionReportUiState,
    onItemClick: (String) -> Unit,
) {
    if (state.items.isEmpty()) {
        EmptyReportText()
        return
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                    val isIncome = TxType.fromWire(item.transaction.type) == TxType.INCOME
                    Column(modifier = Modifier.animateItem()) {
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
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
                                Text(
                                    item.categoryName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
                                    reportAmountText(item.transaction.amountMinor, item.currencySymbol),
                                style = MaterialTheme.typography.titleMedium,
                                softWrap = false,
                                color = if (isIncome) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesReport(
    state: TransactionReportUiState,
    isIncome: Boolean,
    onCategoryClick: (String) -> Unit,
) {
    if (state.categoryTotals.isEmpty()) {
        EmptyReportText()
        return
    }
    val categoryById = state.categories.associateBy { it.id }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.categoryTotals.forEachIndexed { index, total ->
            item(key = total.categoryId) {
                val category = categoryById[total.categoryId]
                val amountMinor = if (isIncome) total.incomeMinor else total.expenseMinor
                Column(modifier = Modifier.animateItem()) {
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategoryClick(total.categoryId) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryIcon(category?.iconKey ?: "", category?.colorKey ?: "default")
                        NameWithShare(
                            name = category?.name ?: stringResource(R.string.report_uncategorized),
                            percent = sharePercent(amountMinor, state.totalMinor),
                            share = shareOf(amountMinor, state.totalMinor),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                        )
                        Text(
                            text = reportAmountText(amountMinor, state.currencySymbol),
                            style = MaterialTheme.typography.titleMedium,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountsReport(
    state: TransactionReportUiState,
    isIncome: Boolean,
    onCategoryClick: (String, String) -> Unit,
) {
    if (state.accountTotals.isEmpty()) {
        EmptyReportText()
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
            val account = accountById[total.accountId]
            val expanded = total.accountId in expandedAccounts
            val accountAmount = if (isIncome) total.incomeMinor else total.expenseMinor
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedAccounts = if (expanded) {
                                expandedAccounts - total.accountId
                            } else {
                                expandedAccounts + total.accountId
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryIcon(account?.iconKey ?: "", account?.colorKey ?: "default")
                    Text(
                        text = account?.name ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                    )
                    Text(
                        text = reportAmountText(accountAmount, state.currencySymbol),
                        style = MaterialTheme.typography.titleMedium,
                        softWrap = false,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
                if (expanded) {
                    total.categories.forEachIndexed { index, categoryTotal ->
                        val category = categoryById[categoryTotal.categoryId]
                        val categoryAmount =
                            if (isIncome) categoryTotal.incomeMinor else categoryTotal.expenseMinor
                        Column {
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCategoryClick(categoryTotal.categoryId, total.accountId) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryIcon(
                                    iconKey = category?.iconKey ?: "",
                                    colorKey = category?.colorKey ?: "default",
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                                NameWithShare(
                                    name = category?.name ?: stringResource(R.string.report_uncategorized),
                                    percent = sharePercent(categoryAmount, accountAmount),
                                    share = shareOf(categoryAmount, accountAmount),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 16.dp),
                                )
                                Text(
                                    text = reportAmountText(categoryAmount, state.currencySymbol),
                                    style = MaterialTheme.typography.bodyLarge,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyReportText() {
    Text(
        text = stringResource(R.string.report_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun reportAmountText(minor: Long, currencySymbol: String): String {
    val compact = Money.compact(minor)
    val suffix = when (compact.scale) {
        Money.Scale.MILLION -> stringResource(R.string.money_suffix_million)
        Money.Scale.BILLION -> stringResource(R.string.money_suffix_billion)
        Money.Scale.NONE -> ""
    }
    val digits = listOf(compact.digits, suffix).filter { it.isNotEmpty() }.joinToString(" ")
    return "$digits $currencySymbol"
}

/** Название с процентом и полосой доли под ним. */
@Composable
private fun NameWithShare(
    name: String,
    percent: Int,
    share: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.report_share_percent, percent),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        LinearProgressIndicator(
            progress = { share },
            modifier = Modifier.fillMaxWidth(),
            strokeCap = StrokeCap.Round,
        )
    }
}

private fun sharePercent(partMinor: Long, wholeMinor: Long): Int {
    if (wholeMinor <= 0L) return 0
    return (partMinor * 100f / wholeMinor).roundToInt().coerceAtLeast(0)
}

private fun shareOf(partMinor: Long, wholeMinor: Long): Float {
    if (wholeMinor <= 0L) return 0f
    return (partMinor.toFloat() / wholeMinor).coerceIn(0f, 1f)
}
