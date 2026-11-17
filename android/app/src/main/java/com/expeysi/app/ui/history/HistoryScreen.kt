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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.material.icons.filled.Handshake
import com.expeysi.app.R
import com.expeysi.app.domain.DebtCalculator
import com.expeysi.app.domain.Money
import com.expeysi.app.domain.DebtWriteOffs
import com.expeysi.app.domain.TransferFees
import com.expeysi.app.domain.Transfers
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.common.DayGroupHeader
import com.expeysi.app.ui.common.asString
import com.expeysi.app.ui.common.categoryDisplayName
import com.expeysi.app.ui.debts.DebtIcon
import com.expeysi.app.ui.debts.debtKindLabelRes
import com.expeysi.app.ui.home.TransactionItem
import com.expeysi.app.ui.icons.CategoryIcon
import com.expeysi.app.ui.theme.LocalFinanceColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
private val dayHeaderFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onAddClick: (TxType?) -> Unit,
    onItemClick: (id: String, isDebt: Boolean) -> Unit,
    /** Правка счёта в режиме истории счёта; тот же экран, что и через «Счета» в drawer. */
    onEditAccountClick: (String) -> Unit = {},
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

    val modes = remember(state.isAccountMode, state.isTransferOnly) {
        buildList {
            add(HistoryViewMode.LIST to R.string.history_mode_transactions)
            if (!state.isTransferOnly) {
                add(HistoryViewMode.CATEGORIES to R.string.history_mode_categories)
                if (!state.isAccountMode) add(HistoryViewMode.ACCOUNTS to R.string.history_mode_accounts)
            }
        }
    }
    var pageCount by remember { mutableStateOf(modes.size) }
    LaunchedEffect(state.isAccountMode, state.isTransferOnly) {
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
    // Подсветка сегментов следует за страницей пейджера, а не за состоянием VM:
    // VM обновляется по settledPage, то есть после анимации заскока, и чипы
    // заметно запаздывают за свайпом.
    val selectedMode = modes.getOrNull(pagerState.currentPage)?.first ?: state.mode

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
                            text = state.title.asString(),
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
                actions = {
                    state.accountId?.let { accountId ->
                        IconButton(onClick = { onEditAccountClick(accountId) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.history_edit_account),
                            )
                        }
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
                        onAddClick(
                            // В отчёте переводов FAB открывает добавление сразу в режиме «Перевод».
                            if (state.isTransferOnly) TxType.TRANSFER_OUT else selectedTypes.singleOrNull(),
                        )
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
            AnimatedVisibility(visible = showChips && state.detail == null && modes.size > 1) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    modes.forEachIndexed { index, (mode, labelRes) ->
                        SegmentedButton(
                            selected = mode == selectedMode,
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
                        Text(
                            state.detailTitle?.asString().orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    TransactionList(
                        state.items,
                        onItemClick,
                        detailListState,
                        accountId = state.accountId,
                    )
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
                        else -> TransactionList(
                            state.items,
                            onItemClick,
                            historyListState,
                            transferOnly = state.isTransferOnly,
                            accountId = state.accountId,
                        )
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
    onItemClick: (id: String, isDebt: Boolean) -> Unit,
    listState: LazyListState,
    /** Отчёт переводов: все операции — переводы, дата видна из стик-заголовка дня. */
    transferOnly: Boolean = false,
    /**
     * Счёт, история которого показана: суммы переводов получают знак и цвет
     * относительно него, серии не сворачиваются — суммировать движение денег
     * разных счетов некорректно.
     */
    accountId: String? = null,
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
    // Раскрытие серий переводов переживает повороты и пересборку состояния.
    val expandedGroups = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { mutableStateListOf<String>().apply { addAll(it) } },
        ),
    ) { mutableStateListOf<String>() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        grouped.forEach { (date, dayItems) ->
            stickyHeader(key = "date-$date") {
                val dateLabel = when (date) {
                    today -> stringResource(R.string.home_today)
                    today.minusDays(1) -> stringResource(R.string.home_yesterday)
                    else -> date.format(dayHeaderFormatter)
                }
                if (!transferOnly) {
                    // Переводы нейтральны, но их комиссия — реальная потеря
                    // денег: входит в расходы дня. Долговая операция — тоже
                    // реальное движение денег счёта: взятое и полученное
                    // погашение — приход, выданный долг и отправленное
                    // погашение — расход.
                    val feeMinor = Transfers.feesMinor(dayItems.map { it.transaction })
                    DayGroupHeader(
                        dateLabel = dateLabel,
                        incomeMinor = dayItems
                            .filter { item ->
                                val type = TxType.fromWire(item.transaction.type)
                                type == TxType.INCOME ||
                                    type.isDebt && !DebtCalculator.isOutgoing(type)
                            }
                            .sumOf { it.transaction.amountMinor },
                        expenseMinor = dayItems
                            .filter { item ->
                                val type = TxType.fromWire(item.transaction.type)
                                type == TxType.EXPENSE ||
                                    type.isDebt && DebtCalculator.isOutgoing(type)
                            }
                            .sumOf { it.transaction.amountMinor } + feeMinor,
                        currencySymbol = dayItems.first().currencySymbol,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    Text(
                        dateLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(vertical = 8.dp),
                    )
                }
            }
            val entries = if (accountId != null) {
                // История счёта: знаки относительно счёта, серии не сворачиваются.
                dayItems.map { HistoryListEntry.Single(it) }
            } else {
                groupItems(dayItems)
            }
            entries.forEachIndexed { index, entry ->
                when (entry) {
                    is HistoryListEntry.Single -> item(key = entry.item.transaction.id) {
                        Column(modifier = Modifier.animateItem()) {
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            HistoryRow(
                                item = entry.item,
                                accountId = accountId,
                                onClick = {
                                    onItemClick(
                                        entry.item.transaction.id,
                                        TxType.fromWire(entry.item.transaction.type).isDebt,
                                    )
                                },
                            )
                        }
                    }
                    is HistoryListEntry.Group -> {
                        item(key = "group-${entry.id}") {
                            Column(modifier = Modifier.animateItem()) {
                                if (index > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                                TransferGroupRow(
                                    items = entry.items,
                                    expanded = entry.id in expandedGroups,
                                    onToggle = {
                                        if (entry.id in expandedGroups) {
                                            expandedGroups.remove(entry.id)
                                        } else {
                                            expandedGroups.add(entry.id)
                                        }
                                    },
                                )
                            }
                        }
                        // Члены раскрытой серии — отдельные элементы списка со своими ключами.
                        if (entry.id in expandedGroups) {
                            entry.items.forEach { member ->
                                item(key = member.transaction.id) {
                                    Column(modifier = Modifier.animateItem()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        // Линия слева визуально связывает карточки членов с серией.
                                        TransferGroupChildRow {
                                            HistoryRow(
                                                item = member,
                                                accountId = accountId,
                                                onClick = {
                                                    // Члены серии — только переводы, долгом быть не может.
                                                    onItemClick(member.transaction.id, false)
                                                },
                                            )
                                        }
                                    }
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
private fun HistoryRow(
    item: TransactionItem,
    accountId: String?,
    onClick: () -> Unit,
) {
    // Перевод рисуется карточкой с обеими сторонами и суммами ног; строка ниже —
    // обычные доходы и расходы и фолбэк для переводов без карточки (ноги без связки).
    if (item.isTransfer) {
        item.transferCard?.let { card ->
            // Свёрнутая пара, обе ноги которой видны, нейтральна даже в истории
            // счёта; одиночная нога показывает движение денег счёта знаком и цветом.
            TransferCardRow(
                card = card,
                signed = accountId != null && !item.isTransferPair,
                onClick = onClick,
            )
            return
        }
    }
    val txType = TxType.fromWire(item.transaction.type)
    // Счётная сторона: доход и долговое поступление денег — приход, остальное — расход.
    val isIncome = txType == TxType.INCOME ||
        txType.isDebt && !DebtCalculator.isOutgoing(txType)
    val debtLabel = debtKindLabelRes(txType)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (txType.isDebt) {
            DebtIcon(Icons.Filled.Handshake, MaterialTheme.colorScheme.primaryContainer)
        } else {
            CategoryIcon(item.categoryIconKey, item.categoryColorKey)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                when {
                    debtLabel != null -> stringResource(debtLabel)
                    else -> item.categoryName.ifBlank { stringResource(R.string.report_uncategorized) }
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                listOfNotNull(
                    item.counterpartyName.ifBlank { null },
                    item.accountName.ifBlank { null },
                    dateTextOf(item.transaction.date),
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

/** Полная дата операции «дд.ММ чч:мм». */
private fun dateTextOf(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(dateFormatter)

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
            // Псевдо-категории комиссий и списаний никуда не ведут: за ними
            // нет записей в БД.
            val isFee = total.categoryId == TransferFees.CATEGORY_ID
            val isWriteOff = total.categoryId == DebtWriteOffs.CATEGORY_ID
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(
                        enabled = !isFee && !isWriteOff,
                    ) { onCategoryClick(total.categoryId) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(
                    when (total.categoryId) {
                        TransferFees.CATEGORY_ID -> TransferFees.ICON_KEY
                        DebtWriteOffs.CATEGORY_ID -> DebtWriteOffs.ICON_KEY
                        else -> category?.iconKey ?: ""
                    },
                    when (total.categoryId) {
                        TransferFees.CATEGORY_ID -> TransferFees.COLOR_KEY
                        DebtWriteOffs.CATEGORY_ID -> DebtWriteOffs.COLOR_KEY
                        else -> category?.colorKey ?: "default"
                    },
                )
                Text(
                    text = categoryDisplayName(total.categoryId, category?.name ?: ""),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                )
                // У списания в смешанном виде показываем непустую сторону
                // (обычно у списания одна сторона), как у строк счетов.
                val rowAmountMinor = when {
                    !isWriteOff -> if (state.isIncomeOnly) total.incomeMinor else total.expenseMinor
                    state.isIncomeOnly || total.expenseMinor == 0L -> total.incomeMinor
                    else -> total.expenseMinor
                }
                Text(
                    text = "${Money.formatAuto(rowAmountMinor)} ${state.currencySymbol}",
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
            // Псевдо-«счёт» списаний долга: у корректировки нет счёта,
            // в разрезке счетов она своя строка без вложенной расшифровки.
            val isWriteOff = total.accountId == DebtWriteOffs.CATEGORY_ID
            val expanded = !isWriteOff && total.accountId in expandedAccounts
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(enabled = !isWriteOff) {
                            expandedAccounts = if (expanded) {
                                expandedAccounts - total.accountId
                            } else {
                                expandedAccounts + total.accountId
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isWriteOff) {
                        CategoryIcon(DebtWriteOffs.ICON_KEY, DebtWriteOffs.COLOR_KEY)
                        Text(
                            text = categoryDisplayName(DebtWriteOffs.CATEGORY_ID, ""),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        )
                    } else {
                        CategoryIcon(account?.iconKey ?: "", account?.colorKey ?: "default")
                        Text(
                            text = account?.name.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        )
                    }
                    Text(
                        text = "${Money.formatAuto(if (total.expenseMinor == 0L) total.incomeMinor else total.expenseMinor)} ${state.currencySymbol}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (isWriteOff) {
                        Spacer(modifier = Modifier.width(24.dp))
                    } else {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = stringResource(
                                if (expanded) R.string.analysis_collapse else R.string.history_expand_cd,
                            ),
                        )
                    }
                }
                if (expanded) {
                    total.categories.forEach { categoryTotal ->
                        val category = categoryById[categoryTotal.categoryId]
                        val isFee = categoryTotal.categoryId == TransferFees.CATEGORY_ID
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                // Псевдо-категория комиссий никуда не ведёт:
                                // за ней нет записи в БД.
                                .clickable(enabled = !isFee) {
                                    onCategoryClick(categoryTotal.categoryId, total.accountId)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIcon(
                                if (isFee) TransferFees.ICON_KEY else category?.iconKey ?: "",
                                if (isFee) TransferFees.COLOR_KEY else category?.colorKey ?: "default",
                            )
                            Text(
                                text = categoryDisplayName(categoryTotal.categoryId, category?.name ?: ""),
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
