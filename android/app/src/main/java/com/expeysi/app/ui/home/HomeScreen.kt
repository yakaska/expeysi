package com.expeysi.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.domain.Money
import com.expeysi.app.domain.TxType
import com.expeysi.app.domain.WindowSums
import com.expeysi.app.ui.icons.CategoryIcon
import com.expeysi.app.ui.icons.CategoryPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onAccountsClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onAddBalanceClick: () -> Unit,
    onBalanceHistoryClick: (String) -> Unit,
    onAccountHistoryClick: (String) -> Unit,
    onSummaryClick: (String, TxType, Int) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var balanceMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextButton(onClick = { balanceMenuOpen = true }) {
                        Text(
                            state.selectedBalance?.let { "${it.name} (${state.currencySymbol})" }
                                ?: "Expeysi",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Выбрать баланс")
                        DropdownMenu(
                            expanded = balanceMenuOpen,
                            onDismissRequest = { balanceMenuOpen = false },
                        ) {
                            state.balances.forEach { balance ->
                                DropdownMenuItem(
                                    text = { Text(balance.name) },
                                    onClick = {
                                        viewModel.selectBalance(balance.id)
                                        balanceMenuOpen = false
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Новый баланс…") },
                                onClick = {
                                    balanceMenuOpen = false
                                    onAddBalanceClick()
                                },
                            )
                            state.familyId?.let {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Код семьи: $it") },
                                    onClick = {
                                        clipboard.setText(AnnotatedString(it))
                                        balanceMenuOpen = false
                                        Toast.makeText(context, "Код семьи скопирован", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            state.selectedBalance?.let { onBalanceHistoryClick(it.id) }
                        },
                    ) {
                        Icon(Icons.Filled.History, contentDescription = "История операций")
                    }
                    IconButton(onClick = onCategoriesClick) {
                        Icon(Icons.Filled.Category, contentDescription = "Категории")
                    }
                    IconButton(onClick = onAccountsClick) {
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Счета")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить операцию")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                            title = "Счета",
                            currencyCode = state.selectedBalance?.currencyCode ?: "",
                            accent = MaterialTheme.colorScheme.tertiary,
                            centerText = "${Money.format(state.totalBalanceMinor)} ${state.currencySymbol}",
                            centerColor = if (state.totalBalanceMinor < 0) {
                                CategoryPalette.color("red")
                            } else {
                                CategoryPalette.color("green")
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
                                    onAccountHistoryClick(balance.account.id)
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
                    title = "Расходы",
                    sums = state.expense,
                    currencyCode = state.selectedBalance?.currencyCode ?: "",
                    accent = MaterialTheme.colorScheme.error,
                    onPeriodClick = { days ->
                        state.selectedBalance?.let { onSummaryClick(it.id, TxType.EXPENSE, days) }
                    },
                )
            }
            item {
                SumsCard(
                    title = "Доходы",
                    sums = state.income,
                    currencyCode = state.selectedBalance?.currencyCode ?: "",
                    accent = MaterialTheme.colorScheme.primary,
                    onPeriodClick = { days ->
                        state.selectedBalance?.let { onSummaryClick(it.id, TxType.INCOME, days) }
                    },
                )
            }

            item {
                Text(
                    "Операции",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.recent.isEmpty()) {
                item {
                    Text(
                        "Пока пусто. Нажми «+», чтобы добавить первую операцию.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.recent, key = { it.transaction.id }) { item ->
                val isIncome = TxType.fromWire(item.transaction.type) == TxType.INCOME
                Card(
                    onClick = { onItemClick(item.transaction.id) },
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
            SumRow("Сегодня", sums.dayMinor) { onPeriodClick(1) }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SumRow("7 дней", sums.weekMinor) { onPeriodClick(7) }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SumRow("30 дней", sums.monthMinor) { onPeriodClick(30) }
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
