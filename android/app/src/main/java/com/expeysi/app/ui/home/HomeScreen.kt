package com.expeysi.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.data.analysis.CategoryStat
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.CategoryShares
import com.expeysi.app.domain.Money
import com.expeysi.app.domain.TxType
import com.expeysi.app.domain.WindowSums
import com.expeysi.app.ui.common.categoriesLabel
import com.expeysi.app.ui.common.operationsLabel
import com.expeysi.app.ui.icons.CategoryIcon
import com.expeysi.app.ui.icons.CategoryPalette
import com.expeysi.app.ui.theme.ExpeysiTheme
import com.expeysi.app.ui.theme.LocalFinanceColors
import com.expeysi.app.ui.theme.TopCardShape
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

private const val MINUS_SIGN = "\u2212"

private const val TODAY_PERIOD_INDEX = 0
private const val WEEK_PERIOD_INDEX = 1
private const val MONTH_PERIOD_INDEX = 2

private val homePeriodLabels = listOf(R.string.home_today, R.string.home_seven_days, R.string.home_thirty_days)
private val homePeriodDays = listOf(1, 7, 30)

private const val TOP_CATEGORIES_VISIBLE = 3
private const val VISIBLE_ACCOUNT_CHIPS = 3

data class HomeActions(
    val onMenuClick: () -> Unit,
    val onAddClick: () -> Unit,
    val onItemClick: (String) -> Unit,
    val onAddBalanceClick: () -> Unit,
    val onEditBalanceClick: (String) -> Unit,
    val onBalanceHistoryClick: (String) -> Unit,
    val onAccountHistoryClick: (String) -> Unit,
    val onSummaryClick: (String, TxType, Int) -> Unit,
    val onBalanceSelected: (String) -> Unit,
    val onLoadMoreTransactions: () -> Unit,
    val onAnalysisClick: () -> Unit = {},
    val onPeriodSelected: (Int) -> Unit = {},
)

@Composable
fun HomeScreen(
    onMenuClick: () -> Unit,
    onAddClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onAddBalanceClick: () -> Unit,
    onEditBalanceClick: (String) -> Unit,
    onBalanceHistoryClick: (String) -> Unit,
    onAccountHistoryClick: (String) -> Unit,
    onSummaryClick: (String, TxType, Int) -> Unit,
    onAnalysisClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        actions = HomeActions(
            onMenuClick = onMenuClick,
            onAddClick = onAddClick,
            onItemClick = onItemClick,
            onAddBalanceClick = onAddBalanceClick,
            onEditBalanceClick = onEditBalanceClick,
            onBalanceHistoryClick = onBalanceHistoryClick,
            onAccountHistoryClick = onAccountHistoryClick,
            onSummaryClick = onSummaryClick,
            onBalanceSelected = viewModel::selectBalance,
            onLoadMoreTransactions = viewModel::loadMoreTransactions,
            onAnalysisClick = onAnalysisClick,
            onPeriodSelected = viewModel::selectPeriod,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    actions: HomeActions,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showScrollToTop by remember { mutableStateOf(false) }
    var balanceSheetOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(listState) {
        var previousPosition = 0 to 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { position ->
                showScrollToTop = listState.canScrollBackward &&
                    (
                        position.first < previousPosition.first ||
                            position.first == previousPosition.first &&
                            position.second < previousPosition.second
                        )
                previousPosition = position
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            lastVisible >= layout.totalItemsCount - 5
        }.collect { nearEnd ->
            if (nearEnd) actions.onLoadMoreTransactions()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.selectedBalance?.name
                            ?: stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onMenuClick) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.home_open_menu),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            state.selectedBalance?.let { actions.onBalanceHistoryClick(it.id) }
                        },
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = stringResource(R.string.home_transaction_history),
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
                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            val averageItemStride = visibleItems.zipWithNext { first, second ->
                                second.offset - first.offset
                            }.average().toFloat().takeIf { it > 0f } ?: visibleItems.first().size.toFloat()
                            val distance = listState.firstVisibleItemScrollOffset +
                                listState.firstVisibleItemIndex * averageItemStride
                            listState.animateScrollBy(
                                -distance,
                                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                            )
                            if (listState.canScrollBackward) {
                                listState.animateScrollToItem(0)
                            }
                        }
                    } else {
                        actions.onAddClick()
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    HeroCard(
                        selectedBalance = state.selectedBalance,
                        totalBalanceMinor = state.totalBalanceMinor,
                        currencySymbol = state.currencySymbol,
                        accounts = state.accounts,
                        onAccountClick = actions.onAccountHistoryClick,
                        onBalanceSwitcherClick = { balanceSheetOpen = true },
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                item {
                    PeriodSummaryCard(
                        income = state.income,
                        expense = state.expense,
                        periodDays = state.periodDays,
                        onSelectPeriod = actions.onPeriodSelected,
                        currencySymbol = state.currencySymbol,
                        onClick = { type, days ->
                            state.selectedBalance?.let { actions.onSummaryClick(it.id, type, days) }
                        },
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (state.topCategories.isNotEmpty()) {
                    item {
                        TopExpensesCard(
                            categories = state.topCategories,
                            onAnalysisClick = actions.onAnalysisClick,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }

                item {
                    Text(
                        stringResource(R.string.home_transactions),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (state.transactions.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.home_empty_title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = actions.onAddClick) {
                                Text(stringResource(R.string.home_add_transaction))
                            }
                        }
                    }
                }
                val showAccountName = state.accounts.size != 1
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                state.transactions.groupBy { item ->
                    Instant.ofEpochMilli(item.transaction.date).atZone(zone).toLocalDate()
                }.forEach { (date, transactions) ->
                    stickyHeader(key = "date-$date") {
                        Text(
                            text = when (date) {
                                today -> stringResource(R.string.home_today)
                                today.minusDays(1) -> stringResource(R.string.home_yesterday)
                                else -> date.format(dayFormatter)
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
                                        .clickable { actions.onItemClick(item.transaction.id) }
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
                                                if (showAccountName) item.accountName.ifBlank { null } else null,
                                                Instant.ofEpochMilli(item.transaction.date)
                                                    .atZone(zone)
                                                    .format(timeFormatter),
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
                        }
                    }
                }
                if (state.hasMoreTransactions) {
                    item(key = "transactions-loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
    if (balanceSheetOpen) {
        BalanceSwitcherSheet(
            balances = state.balances,
            selectedBalance = state.selectedBalance,
            balanceTotals = state.balanceTotals,
            balanceSymbols = state.balanceSymbols,
            onBalanceSelected = { id ->
                actions.onBalanceSelected(id)
                balanceSheetOpen = false
            },
            onAddBalance = {
                actions.onAddBalanceClick()
                balanceSheetOpen = false
            },
            onEditBalance = {
                state.selectedBalance?.let { actions.onEditBalanceClick(it.id) }
                balanceSheetOpen = false
            },
            onDismiss = { balanceSheetOpen = false },
        )
    }
}

@Composable
private fun HeroCard(
    selectedBalance: BalanceEntity?,
    totalBalanceMinor: Long,
    currencySymbol: String,
    accounts: List<AccountBalance>,
    onAccountClick: (String) -> Unit,
    onBalanceSwitcherClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TopCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                    shape = TopCardShape,
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_balance),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (selectedBalance != null) {
                    selectedBalanceChip(
                        name = selectedBalance.name,
                        currencyCode = selectedBalance.currencyCode,
                        onBalanceSwitcherClick = onBalanceSwitcherClick,
                    )
                }
            }
            Text(
                text = "${Money.formatAuto(totalBalanceMinor)} $currencySymbol",
                style = MaterialTheme.typography.displaySmall,
                color = if (totalBalanceMinor < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            SingleAccountRow(
                account = accounts.singleOrNull(),
                onAccountClick = onAccountClick,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (accounts.size > 1) {
                AccountChipsRow(
                    accounts = accounts,
                    onAccountClick = onAccountClick,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Чип переключателя баланса: имя и код валюты, если его нет в имени. */
@Composable
private fun selectedBalanceChip(
    name: String,
    currencyCode: String,
    onBalanceSwitcherClick: () -> Unit,
) {
    val chipText = balanceChipText(name, currencyCode)
    val chipCd = stringResource(R.string.home_select_balance_current, chipText)
    AssistChip(
        onClick = onBalanceSwitcherClick,
        label = {
            Text(
                text = chipText,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = {
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier
            .widthIn(max = 200.dp)
            .semantics { contentDescription = chipCd },
    )
}

/** Текст чипа: «Название» или «Название · КОД», если кода нет в названии. */
private fun balanceChipText(name: String, currencyCode: String): String {
    val suffix = chipCurrencyCode(name, currencyCode)
    return if (suffix.isBlank()) name else "$name · $suffix"
}

@Composable
private fun SingleAccountRow(
    account: AccountBalance?,
    onAccountClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (account == null) return
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable { onAccountClick(account.account.id) }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(account.account.iconKey, account.account.colorKey, size = 24.dp)
        Text(
            text = account.account.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountChipsRow(
    accounts: List<AccountBalance>,
    onAccountClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val millionUnit = stringResource(R.string.money_suffix_million)
    val billionUnit = stringResource(R.string.money_suffix_billion)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visibleAccounts = if (expanded || accounts.size <= VISIBLE_ACCOUNT_CHIPS) {
        accounts
    } else {
        accounts.take(VISIBLE_ACCOUNT_CHIPS)
    }
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(visibleAccounts, key = { it.account.id }) { balance ->
            AccountChip(balance = balance, onAccountClick = onAccountClick, millionUnit = millionUnit, billionUnit = billionUnit)
        }
        if (!expanded && accounts.size > VISIBLE_ACCOUNT_CHIPS) {
            item(key = "more-accounts") {
                AssistChip(
                    onClick = { expanded = true },
                    label = {
                        Text(stringResource(R.string.home_more_accounts, accounts.size - VISIBLE_ACCOUNT_CHIPS))
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountChip(
    balance: AccountBalance,
    onAccountClick: (String) -> Unit,
    millionUnit: String,
    billionUnit: String,
) {
    AssistChip(
        modifier = Modifier.widthIn(max = 200.dp),
        onClick = { onAccountClick(balance.account.id) },
        leadingIcon = {
            CategoryIcon(
                balance.account.iconKey,
                balance.account.colorKey,
                size = 20.dp,
            )
        },
        label = {
            Text(
                text = "${balance.account.name} " +
                    Money.formatCompact(balance.balanceMinor, millionUnit, billionUnit),
                style = MaterialTheme.typography.labelLarge,
                color = if (balance.balanceMinor < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color.Unspecified
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun PeriodSummaryCard(
    income: WindowSums,
    expense: WindowSums,
    periodDays: Int,
    onSelectPeriod: (Int) -> Unit,
    currencySymbol: String,
    onClick: (TxType, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val financeColors = LocalFinanceColors.current
    val millionUnit = stringResource(R.string.money_suffix_million)
    val billionUnit = stringResource(R.string.money_suffix_billion)
    val periodIndex = when (periodDays) {
        1 -> TODAY_PERIOD_INDEX
        7 -> WEEK_PERIOD_INDEX
        else -> MONTH_PERIOD_INDEX
    }
    val days = homePeriodDays[periodIndex]
    val incomeMinor = when (periodIndex) {
        TODAY_PERIOD_INDEX -> income.dayMinor
        WEEK_PERIOD_INDEX -> income.weekMinor
        else -> income.monthMinor
    }
    val expenseMinor = when (periodIndex) {
        TODAY_PERIOD_INDEX -> expense.dayMinor
        WEEK_PERIOD_INDEX -> expense.weekMinor
        else -> expense.monthMinor
    }
    val totalMinor = incomeMinor - expenseMinor
    val totalColor = when {
        totalMinor == 0L -> MaterialTheme.colorScheme.onSurface
        totalMinor > 0L -> financeColors.income
        else -> financeColors.expense
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TopCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                homePeriodLabels.forEachIndexed { index, labelRes ->
                    SegmentedButton(
                        selected = periodIndex == index,
                        onClick = { onSelectPeriod(homePeriodDays[index]) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = homePeriodLabels.size),
                        icon = {},
                    ) {
                        Text(stringResource(labelRes))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryColumn(
                    title = stringResource(R.string.home_income),
                    amountMinor = incomeMinor,
                    text = { amount -> "+${Money.formatCompact(amount, millionUnit, billionUnit)}" },
                    amountColor = financeColors.income,
                    onPeriodClick = { onClick(TxType.INCOME, days) },
                    modifier = Modifier.weight(1f),
                )
                SummaryColumn(
                    title = stringResource(R.string.home_expenses),
                    amountMinor = expenseMinor,
                    text = { amount -> "$MINUS_SIGN${Money.formatCompact(amount, millionUnit, billionUnit)}" },
                    amountColor = financeColors.expense,
                    onPeriodClick = { onClick(TxType.EXPENSE, days) },
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_total),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                AnimatedAmountText(
                    amountMinor = totalMinor,
                    style = MaterialTheme.typography.titleMedium,
                    color = totalColor,
                ) { amount ->
                    amountSignOf(amount) + Money.formatCompact(abs(amount), millionUnit, billionUnit)
                }
            }
        }
    }
}

@Composable
private fun SummaryColumn(
    title: String,
    amountMinor: Long,
    text: (Long) -> String,
    amountColor: Color,
    onPeriodClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onPeriodClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedAmountText(
            amountMinor = amountMinor,
            style = MaterialTheme.typography.titleLarge,
            color = amountColor,
        ) { amount -> text(amount) }
    }
}

@Composable
private fun AnimatedAmountText(
    amountMinor: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    text: (Long) -> String,
) {
    AnimatedContent(
        targetState = amountMinor,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 }) togetherWith
                (fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 })
        },
        label = "amount-text",
    ) { amount ->
        Text(
            text = text(amount),
            style = style,
            color = color,
        )
    }
}

@Composable
private fun TopExpensesCard(
    categories: List<CategoryStat>,
    onAnalysisClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val millionUnit = stringResource(R.string.money_suffix_million)
    val billionUnit = stringResource(R.string.money_suffix_billion)
    val shares = CategoryShares.of(categories.map { it.amountMinor })
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TopCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onAnalysisClick)
                    .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_top_categories),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                categories.take(TOP_CATEGORIES_VISIBLE).forEachIndexed { index, category ->
                    TopCategoryRow(
                        category = category,
                        share = shares.getOrElse(index) { 0f },
                        millionUnit = millionUnit,
                        billionUnit = billionUnit,
                    )
                }
            }
            val rest = categories.drop(TOP_CATEGORIES_VISIBLE)
            if (rest.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onAnalysisClick)
                        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.analysis_more_categories,
                            categoriesLabel(rest.size),
                            Money.formatCompact(rest.sumOf { it.amountMinor }, millionUnit, billionUnit),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopCategoryRow(
    category: CategoryStat,
    share: Float,
    millionUnit: String,
    billionUnit: String,
) {
    val color = CategoryPalette.color(category.colorKey)
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryIcon(category.iconKey, category.colorKey)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = category.name.ifBlank { stringResource(R.string.report_uncategorized) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = operationsLabel(category.operationCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { share.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .height(4.dp),
                color = color,
                trackColor = color.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
        }
        Text(
            text = Money.formatCompact(category.amountMinor, millionUnit, billionUnit),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun amountSignOf(value: Long): String = when {
    value > 0L -> "+"
    value < 0L -> MINUS_SIGN
    else -> ""
}

private val previewHomeActions = HomeActions(
    onMenuClick = {},
    onAddClick = {},
    onItemClick = {},
    onAddBalanceClick = {},
    onEditBalanceClick = {},
    onBalanceHistoryClick = {},
    onAccountHistoryClick = {},
    onSummaryClick = { _, _, _ -> },
    onBalanceSelected = {},
    onLoadMoreTransactions = {},
    onAnalysisClick = {},
    onPeriodSelected = {},
)

private enum class PreviewCase {
    Single,
    Many,
    Negative,
    Zero,
}

private val previewHeroCardAccount = AccountEntity(
    id = "account-card",
    name = "Карта",
    iconKey = "credit_card",
    colorKey = "blue",
    openingBalanceMinor = 50_000,
    balanceId = "b",
    updatedAt = 0,
)

@Preview(name = "Hero · короткое название", showBackground = true)
@Composable
private fun HeroCardShortNamePreview() {
    HeroCardPreview(name = "Семейный", currencyCode = "RUB", dark = false)
}

@Preview(
    name = "Hero · короткое название · Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HeroCardShortNameDarkPreview() {
    HeroCardPreview(name = "Семейный", currencyCode = "RUB", dark = true)
}

@Preview(name = "Hero · длинное название", showBackground = true)
@Composable
private fun HeroCardLongNamePreview() {
    HeroCardPreview(name = "Семейный баланс на месяц и все запасы", currencyCode = "RUB", dark = false)
}

@Preview(
    name = "Hero · длинное название · Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HeroCardLongNameDarkPreview() {
    HeroCardPreview(name = "Семейный баланс на месяц и все запасы", currencyCode = "RUB", dark = true)
}

@Preview(name = "Hero · код в названии", showBackground = true)
@Composable
private fun HeroCardCurrencyInNamePreview() {
    HeroCardPreview(name = "Вьетнам (VND)", currencyCode = "VND", dark = false)
}

@Preview(
    name = "Hero · код в названии · Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HeroCardCurrencyInNameDarkPreview() {
    HeroCardPreview(name = "Вьетнам (VND)", currencyCode = "VND", dark = true)
}

@Composable
private fun HeroCardPreview(name: String, currencyCode: String, dark: Boolean) {
    ExpeysiTheme(darkTheme = dark) {
        HeroCard(
            selectedBalance = BalanceEntity(
                id = "b",
                name = name,
                currencyCode = currencyCode,
                updatedAt = 0,
            ),
            totalBalanceMinor = 48_750_00,
            currencySymbol = "₽",
            accounts = listOf(
                AccountBalance(
                    account = previewHeroCardAccount,
                    balanceMinor = 48_750,
                    currencySymbol = "₽",
                ),
            ),
            onAccountClick = {},
            onBalanceSwitcherClick = {},
        )
    }
}

@Preview(name = "Light · один счёт", showBackground = true)
@Composable
private fun HomeContentSingleLightPreview() {
    ExpeysiTheme(darkTheme = false) {
        HomeContent(state = previewHomeState(PreviewCase.Single), actions = previewHomeActions)
    }
}

@Preview(
    name = "Dark · один счёт",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeContentSingleDarkPreview() {
    ExpeysiTheme(darkTheme = true) {
        HomeContent(state = previewHomeState(PreviewCase.Single), actions = previewHomeActions)
    }
}

@Preview(name = "Light · много счетов", showBackground = true)
@Composable
private fun HomeContentManyLightPreview() {
    ExpeysiTheme(darkTheme = false) {
        HomeContent(state = previewHomeState(PreviewCase.Many), actions = previewHomeActions)
    }
}

@Preview(
    name = "Dark · много счетов",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeContentManyDarkPreview() {
    ExpeysiTheme(darkTheme = true) {
        HomeContent(state = previewHomeState(PreviewCase.Many), actions = previewHomeActions)
    }
}

@Preview(name = "Light · минус", showBackground = true)
@Composable
private fun HomeContentNegativeLightPreview() {
    ExpeysiTheme(darkTheme = false) {
        HomeContent(state = previewHomeState(PreviewCase.Negative), actions = previewHomeActions)
    }
}

@Preview(
    name = "Dark · минус",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeContentNegativeDarkPreview() {
    ExpeysiTheme(darkTheme = true) {
        HomeContent(state = previewHomeState(PreviewCase.Negative), actions = previewHomeActions)
    }
}

@Preview(name = "Light · нули", showBackground = true)
@Composable
private fun HomeContentZeroLightPreview() {
    ExpeysiTheme(darkTheme = false) {
        HomeContent(state = previewHomeState(PreviewCase.Zero), actions = previewHomeActions)
    }
}

@Preview(
    name = "Dark · нули",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeContentZeroDarkPreview() {
    ExpeysiTheme(darkTheme = true) {
        HomeContent(state = previewHomeState(PreviewCase.Zero), actions = previewHomeActions)
    }
}

private fun previewHomeState(case: PreviewCase): HomeUiState {
    val balance = BalanceEntity(
        id = "balance",
        name = "Семейный баланс",
        currencyCode = "RUB",
        updatedAt = 0,
    )
    val card = AccountEntity(
        id = "account-card",
        name = "Карта",
        iconKey = "credit_card",
        colorKey = "blue",
        openingBalanceMinor = 50_000,
        balanceId = balance.id,
        updatedAt = 0,
    )
    val cash = AccountEntity(
        id = "account-cash",
        name = "Наличные",
        iconKey = "payments",
        colorKey = "green",
        openingBalanceMinor = 20_000,
        balanceId = balance.id,
        updatedAt = 0,
    )
    val savings = AccountEntity(
        id = "account-savings",
        name = "Сберегательный",
        iconKey = "payments",
        colorKey = "teal",
        openingBalanceMinor = 0,
        balanceId = balance.id,
        updatedAt = 0,
    )
    val stash = AccountEntity(
        id = "account-stash",
        name = "Заначка",
        iconKey = "payments",
        colorKey = "amber",
        openingBalanceMinor = 0,
        balanceId = balance.id,
        updatedAt = 0,
    )
    val topCategories = listOf(
        CategoryStat(
            categoryId = "food",
            name = "Продукты",
            iconKey = "shopping_cart",
            colorKey = "orange",
            amountMinor = 9_000,
            operationCount = 18,
        ),
        CategoryStat(
            categoryId = "transport",
            name = "Транспорт",
            iconKey = "directions_bus",
            colorKey = "blue",
            amountMinor = 6_500,
            operationCount = 7,
        ),
        CategoryStat(
            categoryId = "cafe",
            name = "Кафе",
            iconKey = "local_cafe",
            colorKey = "brown",
            amountMinor = 4_200,
            operationCount = 12,
        ),
        CategoryStat(
            categoryId = "leisure",
            name = "Досуг",
            iconKey = "sports_esports",
            colorKey = "purple",
            amountMinor = 2_100,
            operationCount = 3,
        ),
        CategoryStat(
            categoryId = "health",
            name = "Здоровье",
            iconKey = "medical_services",
            colorKey = "red",
            amountMinor = 1_500,
            operationCount = 2,
        ),
    )
    val (income, expense) = when (case) {
        PreviewCase.Single ->
            WindowSums(0, 25_000, 75_000) to WindowSums(1_250, 9_000, 21_800)
        PreviewCase.Many ->
            WindowSums(80_000, 190_000, 640_000) to WindowSums(21_500, 95_000, 23_300)
        PreviewCase.Negative ->
            WindowSums(0, 120_000, 266_000) to WindowSums(50_000, 320_000, 500_000)
        PreviewCase.Zero -> WindowSums(0, 0, 0) to WindowSums(0, 0, 0)
    }
    val accounts = when (case) {
        PreviewCase.Single -> listOf(
            AccountBalance(account = card, balanceMinor = 48_750, currencySymbol = "₽"),
        )
        PreviewCase.Many -> listOf(
            AccountBalance(account = card, balanceMinor = 12_000, currencySymbol = "₽"),
            AccountBalance(account = cash, balanceMinor = 3_400, currencySymbol = "₽"),
            AccountBalance(account = savings, balanceMinor = 200_000, currencySymbol = "₽"),
            AccountBalance(account = stash, balanceMinor = 900, currencySymbol = "₽"),
        )
        PreviewCase.Negative -> listOf(
            AccountBalance(account = card, balanceMinor = -500_000, currencySymbol = "₽"),
            AccountBalance(account = cash, balanceMinor = 266_000, currencySymbol = "₽"),
        )
        PreviewCase.Zero -> listOf(
            AccountBalance(account = card, balanceMinor = 0, currencySymbol = "₽"),
            AccountBalance(account = cash, balanceMinor = 0, currencySymbol = "₽"),
        )
    }
    return HomeUiState(
        balances = listOf(balance),
        selectedBalance = balance,
        currencySymbol = "₽",
        accounts = accounts,
        totalBalanceMinor = accounts.sumOf { it.balanceMinor },
        expense = expense,
        income = income,
        periodDays = 30,
        topCategories = if (case == PreviewCase.Zero) {
            emptyList()
        } else {
            topCategories.take(if (case == PreviewCase.Single) 4 else TOP_CATEGORIES_VISIBLE + 2)
        },
        transactions = if (case == PreviewCase.Zero) {
            emptyList()
        } else {
            listOf(
                TransactionItem(
                    transaction = TransactionEntity(
                        id = "transaction",
                        type = TxType.EXPENSE.wire,
                        amountMinor = 1_250,
                        accountId = card.id,
                        categoryId = "groceries",
                        date = System.currentTimeMillis(),
                        note = "Продукты на ужин",
                        updatedAt = 0,
                    ),
                    categoryName = "Продукты",
                    categoryIconKey = "shopping_cart",
                    categoryColorKey = "green",
                    accountName = card.name,
                    currencySymbol = "₽",
                ),
            )
        },
    )
}
