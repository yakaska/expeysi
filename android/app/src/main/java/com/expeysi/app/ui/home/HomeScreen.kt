package com.expeysi.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.domain.Money
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.icons.IconCatalog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onAccountsClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expeysi") },
                actions = {
                    IconButton(onClick = onCategoriesClick) {
                        Icon(
                            Icons.Filled.Category,
                            contentDescription = "Категории",
                        )
                    }
                    IconButton(onClick = onAccountsClick) {
                        Icon(
                            Icons.Filled.AccountBalanceWallet,
                            contentDescription = "Счета",
                        )
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Счета", style = MaterialTheme.typography.titleMedium)
            }
            items(state.accounts, key = { it.account.id }) { balance ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        leadingContent = {
                            Icon(IconCatalog.icon(balance.account.iconKey), contentDescription = null)
                        },
                        headlineContent = { Text(balance.account.name) },
                        trailingContent = {
                            Text(
                                text = "${Money.format(balance.balanceMinor)} ₽",
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
                        Icon(IconCatalog.icon(item.categoryIconKey), contentDescription = null)
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
                                        .toLocalDate()
                                        .format(dateFormatter),
                                    item.transaction.note.ifBlank { null },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = (if (isIncome) "+" else "−") + "${Money.format(item.transaction.amountMinor)} ₽",
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
