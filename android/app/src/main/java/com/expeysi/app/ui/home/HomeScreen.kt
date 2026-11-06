package com.expeysi.app.ui.home

import android.content.ClipData
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.Money
import com.expeysi.app.domain.TxType
import com.expeysi.app.domain.WindowSums
import com.expeysi.app.ui.icons.CategoryIcon
import com.expeysi.app.ui.theme.ExpeysiTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

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
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    actions: HomeActions,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showScrollToTop by remember { mutableStateOf(false) }
    var balanceMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        var previousPosition = 0 to 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { position ->
                showScrollToTop = listState.canScrollBackward &&
                    (position.first < previousPosition.first ||
                        position.first == previousPosition.first && position.second < previousPosition.second)
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
                    TextButton(onClick = { balanceMenuOpen = true }) {
                        Text(
                            state.selectedBalance?.let { "${it.name} (${state.currencySymbol})" }
                                ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = stringResource(R.string.home_select_balance),
                        )
                        DropdownMenu(
                            expanded = balanceMenuOpen,
                            onDismissRequest = { balanceMenuOpen = false },
                        ) {
                            state.balances.forEach { balance ->
                                DropdownMenuItem(
                                    text = { Text(balance.name) },
                                    onClick = {
                                        actions.onBalanceSelected(balance.id)
                                        balanceMenuOpen = false
                                    },
                                )
                            }
                            HorizontalDivider()
                            state.selectedBalance?.let { balance ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.home_edit_balance)) },
                                    onClick = {
                                        balanceMenuOpen = false
                                        actions.onEditBalanceClick(balance.id)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_new_balance)) },
                                onClick = {
                                    balanceMenuOpen = false
                                    actions.onAddBalanceClick()
                                },
                            )
                            state.familyId?.let {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.home_family_code, it)) },
                                    onClick = {
                                        coroutineScope.launch {
                                            clipboard.setClipEntry(
                                                ClipEntry(ClipData.newPlainText("family code", it)),
                                            )
                                            balanceMenuOpen = false
                                            Toast.makeText(
                                                context,
                                                R.string.home_family_code_copied,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                )
                            }
                        }
                    }
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary),
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            CardHeader(
                                title = stringResource(R.string.home_accounts),
                                currencyCode = state.selectedBalance?.currencyCode ?: "",
                                accent = MaterialTheme.colorScheme.tertiary,
                                centerText = "${Money.format(state.totalBalanceMinor)} ${state.currencySymbol}",
                                centerColor = if (state.totalBalanceMinor < 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                            state.accounts.forEachIndexed { index, balance ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                ListItem(
                                    modifier = Modifier.clickable {
                                        actions.onAccountHistoryClick(balance.account.id)
                                    },
                                    leadingContent = {
                                        CategoryIcon(balance.account.iconKey, balance.account.colorKey)
                                    },
                                    headlineContent = { Text(balance.account.name) },
                                    trailingContent = {
                                        Text(
                                            Money.format(balance.balanceMinor),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (balance.balanceMinor < 0) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    SumsCard(
                        title = stringResource(R.string.home_expenses),
                        sums = state.expense,
                        currencyCode = state.selectedBalance?.currencyCode ?: "",
                        accent = MaterialTheme.colorScheme.error,
                        onPeriodClick = { days ->
                            state.selectedBalance?.let { actions.onSummaryClick(it.id, TxType.EXPENSE, days) }
                        },
                    )
                }
                item {
                    SumsCard(
                        title = stringResource(R.string.home_income),
                        sums = state.income,
                        currencyCode = state.selectedBalance?.currencyCode ?: "",
                        accent = MaterialTheme.colorScheme.primary,
                        onPeriodClick = { days ->
                            state.selectedBalance?.let { actions.onSummaryClick(it.id, TxType.INCOME, days) }
                        },
                    )
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
                                                item.accountName.ifBlank { null },
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
}

@Composable
private fun SumsCard(
    title: String,
    sums: WindowSums,
    currencyCode: String,
    accent: androidx.compose.ui.graphics.Color,
    onPeriodClick: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, accent),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            CardHeader(title = title, currencyCode = currencyCode, accent = accent)
            SumRow(stringResource(R.string.home_today), sums.dayMinor) { onPeriodClick(1) }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SumRow(stringResource(R.string.home_seven_days), sums.weekMinor) { onPeriodClick(7) }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SumRow(stringResource(R.string.home_thirty_days), sums.monthMinor) { onPeriodClick(30) }
        }
    }
}

@Composable
private fun CardHeader(
    title: String,
    currencyCode: String,
    accent: androidx.compose.ui.graphics.Color,
    centerText: String? = null,
    centerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = accent)
        Spacer(modifier = Modifier.weight(1f))
        if (centerText != null) {
            Text(centerText, style = MaterialTheme.typography.titleMedium, color = centerColor)
            Spacer(modifier = Modifier.weight(1f))
        }
        Text(
            currencyCode,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SumRow(label: String, amountMinor: Long, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(label) },
        trailingContent = {
            Text(
                Money.format(amountMinor),
                style = MaterialTheme.typography.titleMedium,
            )
        },
    )
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
)

@Preview(name = "Light", showBackground = true)
@Composable
private fun HomeContentLightPreview() {
    ExpeysiTheme(darkTheme = false) {
        HomeContent(state = previewHomeState(), actions = previewHomeActions)
    }
}

@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeContentDarkPreview() {
    ExpeysiTheme(darkTheme = true) {
        HomeContent(state = previewHomeState(), actions = previewHomeActions)
    }
}

private fun previewHomeState(): HomeUiState {
    val balance = BalanceEntity(
        id = "balance",
        name = "Семейный бюджет",
        currencyCode = "RUB",
        updatedAt = 0,
    )
    val account = AccountEntity(
        id = "account",
        name = "Карта",
        iconKey = "card",
        openingBalanceMinor = 50_000,
        balanceId = balance.id,
        updatedAt = 0,
    )
    return HomeUiState(
        balances = listOf(balance),
        selectedBalance = balance,
        currencySymbol = "₽",
        accounts = listOf(AccountBalance(account = account, balanceMinor = 48_750, currencySymbol = "₽")),
        totalBalanceMinor = 48_750,
        expense = WindowSums(dayMinor = 1_250, weekMinor = 8_400, monthMinor = 32_000),
        income = WindowSums(dayMinor = 0, weekMinor = 25_000, monthMinor = 75_000),
        transactions = listOf(
            TransactionItem(
                transaction = TransactionEntity(
                    id = "transaction",
                    type = TxType.EXPENSE.wire,
                    amountMinor = 1_250,
                    accountId = account.id,
                    categoryId = "groceries",
                    date = System.currentTimeMillis(),
                    note = "Продукты на ужин",
                    updatedAt = 0,
                ),
                categoryName = "Продукты",
                categoryIconKey = "groceries",
                categoryColorKey = "green",
                accountName = account.name,
                currencySymbol = "₽",
            ),
        ),
    )
}
