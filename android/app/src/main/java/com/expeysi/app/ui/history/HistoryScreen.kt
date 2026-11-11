package com.expeysi.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.domain.Money
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.home.TransactionItem
import com.expeysi.app.ui.icons.CategoryIcon
import com.expeysi.app.ui.theme.LocalFinanceColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
private val dayHeaderFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onAddClick: (TxType?) -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val customRange by viewModel.customRange.collectAsStateWithLifecycle()
    val selectedTypes by viewModel.selectedTypes.collectAsStateWithLifecycle()
    var showPeriodSheet by remember { mutableStateOf(false) }
    var showChips by remember { mutableStateOf(true) }
    var showScrollToTop by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val historyListState = rememberLazyListState()
    val categoriesListState = rememberLazyListState()
    val accountsListState = rememberLazyListState()
    val detailListState = rememberLazyListState()

    BackHandler(enabled = state.detail != null) { viewModel.closeDetail() }

    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -1f) {
                    showChips = false
                } else if (available.y > 1f) {
                    showChips = true
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(state.detail) {
        if (state.detail == null) showChips = true
    }

    val modes = remember(state.isAccountMode) {
        buildList {
            add(HistoryViewMode.LIST to R.string.history_mode_transactions)
            add(HistoryViewMode.CATEGORIES to R.string.history_mode_categories)
            if (!state.isAccountMode) add(HistoryViewMode.ACCOUNTS to R.string.history_mode_accounts)
        }
    }
    var pageCount by remember { mutableStateOf(modes.size) }
    LaunchedEffect(state.isAccountMode) {
        pageCount = modes.size
    }

    val pagerState = rememberPagerState(
        initialPage = modes.indexOfFirst { it.first == state.mode }.coerceAtLeast(0),
        pageCount = { pageCount },
    )
    LaunchedEffect(state.mode) {
        val index = modes.indexOfFirst { it.first == state.mode }
        if (index >= 0 && pagerState.settledPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val mode = modes.getOrNull(page)?.first ?: return@collect
            if (mode != state.mode) viewModel.selectMode(mode)
        }
    }

    val activeListState = if (state.detail != null) {
        detailListState
    } else {
        when (modes.getOrNull(pagerState.currentPage)?.first) {
            HistoryViewMode.CATEGORIES -> categoriesListState
            HistoryViewMode.ACCOUNTS -> accountsListState
            else -> historyListState
        }
    }
    LaunchedEffect(activeListState) {
        var previousPosition = 0 to 0
        snapshotFlow { activeListState.firstVisibleItemIndex to activeListState.firstVisibleItemScrollOffset }
            .collect { position ->
                showScrollToTop = activeListState.canScrollBackward &&
                    (
                        position.first < previousPosition.first ||
                            position.first == previousPosition.first &&
                            position.second < previousPosition.second
                        )
                previousPosition = position
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val periodLabel = customRange?.let { rangeLabel(it) } ?: stringResource(period.labelRes())
                    Column {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { showPeriodSheet = true },
                        ) {
                            Text(
                                text = periodLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = stringResource(R.string.period_pick_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (showScrollToTop) {
                        coroutineScope.launch {
                            val visibleItems = activeListState.layoutInfo.visibleItemsInfo
                            val averageItemStride = visibleItems.zipWithNext { first, second ->
                                second.offset - first.offset
                            }.average().toFloat().takeIf { it > 0f } ?: visibleItems.first().size.toFloat()
                            val distance = activeListState.firstVisibleItemScrollOffset +
                                activeListState.firstVisibleItemIndex * averageItemStride
                            activeListState.animateScrollBy(
                                -distance,
                                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                            )
                            if (activeListState.canScrollBackward) {
                                activeListState.animateScrollToItem(0)
                            }
                        }
                    } else {
                        onAddClick(selectedTypes.singleOrNull())
                    }
                },
            ) {
                Icon(
                    if (showScrollToTop) Icons.Filled.KeyboardArrowUp else Icons.Filled.Add,
                    contentDescription = stringResource(
                        if (showScrollToTop) R.string.home_scroll_to_top else R.string.home_add_transaction,
                    ),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollConnection),
        ) {
            AnimatedVisibility(visible = showChips && state.detail == null) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    modes.forEachIndexed { index, (mode, labelRes) ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { viewModel.selectMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        ) {
                            Text(stringResource(labelRes))
                        }
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
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.history_back_groups_cd),
                            )
                        }
                        Text(state.detailTitle, style = MaterialTheme.typography.titleMedium)
                    }
                    TransactionList(state.items, onItemClick, detailListState)
                }
                else -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (modes.getOrNull(page)?.first) {
                        HistoryViewMode.CATEGORIES -> CategoriesView(
                            state = state,
                            onCategoryClick = { viewModel.openDetail(it) },
                            listState = categoriesListState,
                        )
                        HistoryViewMode.ACCOUNTS -> AccountsView(
                            state = state,
                            onCategoryClick = { categoryId, accountId ->
                                viewModel.openDetail(categoryId, accountId)
                            },
                            listState = accountsListState,
                        )
                        else -> TransactionList(state.items, onItemClick, historyListState)
                    }
                }
            }
        }
    }
    if (showPeriodSheet) {
        PeriodPickerSheet(
            period = period,
            customRange = customRange,
            onPeriodSelected = viewModel::selectPeriod,
            onRangeSelected = viewModel::selectRange,
            onDismiss = { showPeriodSheet = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionList(
    items: List<TransactionItem>,
    onItemClick: (String) -> Unit,
    listState: LazyListState,
) {
    if (items.isEmpty()) {
        EmptyHistoryText()
        return
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val grouped = items.groupBy { item ->
        Instant.ofEpochMilli(item.transaction.date).atZone(zone).toLocalDate()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        grouped.forEach { (date, groupItems) ->
            stickyHeader(key = "date-$date") {
                Text(
                    text = when (date) {
                        today -> stringResource(R.string.home_today)
                        today.minusDays(1) -> stringResource(R.string.home_yesterday)
                        else -> date.format(dayHeaderFormatter)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            groupItems.forEachIndexed { index, item ->
                item(key = item.transaction.id) {
                    Column(modifier = Modifier.animateItem()) {
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        HistoryRow(
                            item = item,
                            onClick = { onItemClick(item.transaction.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: TransactionItem,
    onClick: () -> Unit,
) {
    val isIncome = TxType.fromWire(item.transaction.type) == TxType.INCOME
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                "${Money.formatAuto(item.transaction.amountMinor)} ${item.currencySymbol}",
            style = MaterialTheme.typography.titleMedium,
            color = if (isIncome) {
                LocalFinanceColors.current.income
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun CategoriesView(
    state: HistoryUiState,
    onCategoryClick: (String) -> Unit,
    listState: LazyListState,
) {
    if (state.categoryTotals.isEmpty()) {
        EmptyHistoryText()
        return
    }
    val categoryById = state.categories.associateBy { it.id }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item(key = "total") {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(
                        if (state.isIncomeOnly) R.string.report_total_income else R.string.report_total_expense,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${Money.formatAuto(if (state.isIncomeOnly) state.totalIncomeMinor else state.totalExpenseMinor)} ${state.currencySymbol}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (state.isIncomeOnly) {
                        LocalFinanceColors.current.income
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        items(state.categoryTotals, key = { it.categoryId }) { total ->
            val category = categoryById[total.categoryId]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable { onCategoryClick(total.categoryId) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(category?.iconKey ?: "", category?.colorKey ?: "default")
                Text(
                    text = category?.name ?: stringResource(R.string.report_uncategorized),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                )
                Text(
                    text = "${Money.formatAuto(if (state.isIncomeOnly) total.incomeMinor else total.expenseMinor)} ${state.currencySymbol}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun AccountsView(
    state: HistoryUiState,
    onCategoryClick: (categoryId: String, accountId: String) -> Unit,
    listState: LazyListState,
) {
    if (state.accountTotals.isEmpty()) {
        EmptyHistoryText()
        return
    }
    var expandedAccounts by remember { mutableStateOf(setOf<String>()) }
    val accountById = state.accounts.associateBy { it.id }
    val categoryById = state.categories.associateBy { it.id }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(state.accountTotals, key = { it.accountId }) { total ->
            val account = accountById[total.accountId]
            val expanded = total.accountId in expandedAccounts
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable {
                            expandedAccounts = if (expanded) {
                                expandedAccounts - total.accountId
                            } else {
                                expandedAccounts + total.accountId
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryIcon(account?.iconKey ?: "", account?.colorKey ?: "default")
                    Text(
                        text = account?.name.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    )
                    Text(
                        text = "${Money.formatAuto(if (total.expenseMinor == 0L) total.incomeMinor else total.expenseMinor)} ${state.currencySymbol}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.analysis_collapse else R.string.history_expand_cd,
                        ),
                    )
                }
                if (expanded) {
                    total.categories.forEach { categoryTotal ->
                        val category = categoryById[categoryTotal.categoryId]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable {
                                    onCategoryClick(categoryTotal.categoryId, total.accountId)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIcon(category?.iconKey ?: "", category?.colorKey ?: "default")
                            Text(
                                text = category?.name ?: stringResource(R.string.report_uncategorized),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp),
                            )
                            Text(
                                text = "${Money.formatAuto(if (categoryTotal.expenseMinor == 0L) categoryTotal.incomeMinor else categoryTotal.expenseMinor)} ${state.currencySymbol}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun EmptyHistoryText() {
    Text(
        text = stringResource(R.string.report_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}
