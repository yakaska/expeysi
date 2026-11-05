package com.expeysi.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.domain.Money
import com.expeysi.app.ui.icons.CategoryIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Счета") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить счёт")
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
            items(accounts, key = { it.balance.account.id }) { row ->
                val balance = row.balance
                Card(
                    onClick = { onItemClick(balance.account.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        leadingContent = {
                            CategoryIcon(balance.account.iconKey, balance.account.colorKey)
                        },
                        headlineContent = { Text(balance.account.name) },
                        supportingContent = {
                            Text(
                                "${row.balanceName} · начальный остаток: " +
                                    "${Money.format(balance.account.openingBalanceMinor)} " +
                                    balance.currencySymbol,
                            )
                        },
                        trailingContent = {
                            Text(
                                text = "${Money.format(balance.balanceMinor)} ${balance.currencySymbol}",
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
}
