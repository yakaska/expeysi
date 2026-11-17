package com.expeysi.app.ui.debts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.ui.common.AmountFormat
import com.expeysi.app.ui.theme.LocalFinanceColors
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtsScreen(
    onBack: () -> Unit,
    onCounterpartyClick: (String) -> Unit,
    onEditCounterpartyClick: (String) -> Unit,
    onAddOperationClick: () -> Unit,
    viewModel: DebtsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val financeColors = LocalFinanceColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debts_title)) },
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
            FloatingActionButton(onClick = onAddOperationClick) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.debts_add_cd),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalCard(
                    label = stringResource(R.string.debts_total_owed_to_me),
                    totals = state.totals.owedToMe,
                    currencySymbols = state.currencySymbols,
                    amountColor = financeColors.income,
                    modifier = Modifier.weight(1f),
                )
                TotalCard(
                    label = stringResource(R.string.debts_total_owed_by_me),
                    totals = state.totals.owedByMe,
                    currencySymbols = state.currencySymbols,
                    amountColor = financeColors.expense,
                    modifier = Modifier.weight(1f),
                )
            }

            state.rows.forEach { row ->
                DebtsRowCard(
                    row = row,
                    currencySymbols = state.currencySymbols,
                    onClick = { onCounterpartyClick(row.counterparty.id) },
                    onEditClick = { onEditCounterpartyClick(row.counterparty.id) },
                    tint = financeColors.income,
                )
            }

            if (state.archived.isNotEmpty()) {
                Text(
                    stringResource(R.string.debts_section_archive),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                state.archived.forEach { row ->
                    DebtsRowCard(
                        row = row,
                        currencySymbols = state.currencySymbols,
                        onClick = { onCounterpartyClick(row.counterparty.id) },
                        onEditClick = { onEditCounterpartyClick(row.counterparty.id) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.rows.isEmpty() && state.archived.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp)
                        .widthIn(max = 320.dp),
                ) {
                    Text(
                        stringResource(R.string.debts_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.debts_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** Карточка сводки: «Должны мне» / «Должен я» со строками по валютам. */
@Composable
private fun TotalCard(
    label: String,
    totals: Map<String, Long>,
    currencySymbols: Map<String, String>,
    amountColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (totals.isEmpty()) {
                Text(
                    text = stringResource(R.string.debts_row_settled),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                totals.forEach { (code, minor) ->
                    Text(
                        text = AmountFormat.withCurrency(minor, currencySymbols[code] ?: code),
                        style = MaterialTheme.typography.titleMedium,
                        color = amountColor,
                    )
                }
            }
        }
    }
}

/** Строка контрагента в списке: имя и его остатки по валютам. */
@Composable
private fun DebtsRowCard(
    row: DebtsRow,
    currencySymbols: Map<String, String>,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    tint: Color,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onClick).padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        ) {
            DebtIcon(Icons.Filled.Handshake, tint = tint)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = row.counterparty.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val subtitle = if (row.balances.isEmpty()) {
                    stringResource(R.string.debts_row_settled)
                } else {
                    row.balances
                        .map { balance ->
                            val amount = AmountFormat.withCurrency(
                                abs(balance.balanceMinor),
                                currencySymbols[balance.currencyCode] ?: balance.currencyCode,
                            )
                            stringResource(
                                if (balance.balanceMinor > 0) {
                                    R.string.debts_row_owed_to_me
                                } else {
                                    R.string.debts_row_owed_by_me
                                },
                                amount,
                            )
                        }
                        .joinToString(separator = " · ")
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.balances.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        tint
                    },
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.common_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
