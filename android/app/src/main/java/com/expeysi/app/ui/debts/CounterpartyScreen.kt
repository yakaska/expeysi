package com.expeysi.app.ui.debts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.ui.common.AmountFormat
import com.expeysi.app.ui.theme.FinanceColors
import com.expeysi.app.ui.theme.LocalFinanceColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val dueDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
private val historyDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterpartyScreen(
    onBack: () -> Unit,
    onEditClick: (String) -> Unit,
    onOperationClick: (String) -> Unit,
    onAdjustmentClick: (String) -> Unit,
    onLendClick: (lend: Boolean) -> Unit,
    onRepayClick: (direction: DebtDirection, currency: String, amount: Long) -> Unit,
    onWriteOffClick: (currency: String, remaining: Long) -> Unit,
    onOpeningBalanceClick: () -> Unit,
    viewModel: CounterpartyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleteRequest by viewModel.deleteState.collectAsStateWithLifecycle()
    val financeColors = LocalFinanceColors.current
    var menuOpen by remember { mutableStateOf(false) }
    val counterparty = state.counterparty

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(counterparty?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (counterparty != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.counterparty_menu_cd),
                                )
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_edit)) },
                                    onClick = {
                                        menuOpen = false
                                        onEditClick(counterparty.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (counterparty.archived) {
                                                    R.string.counterparty_menu_unarchive
                                                } else {
                                                    R.string.counterparty_menu_archive
                                                },
                                            ),
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.toggleArchive()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_delete)) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.requestDelete()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (counterparty == null) {
            // Контрагент удалён на другом устройстве: экран пуст сразу после синка.
            Box(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .widthIn(max = 600.dp),
        ) {
            if (counterparty.archived) {
                Text(
                    stringResource(R.string.counterparty_archived_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            val repayPair = state.balances.firstOrNull { it.balanceMinor != 0L }
            BalancesCard(state = state, financeColors = financeColors)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                FilledTonalButton(onClick = { onLendClick(true) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.debts_action_lend), maxLines = 1)
                }
                FilledTonalButton(onClick = { onLendClick(false) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.debts_action_borrow), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = {
                        if (repayPair != null) {
                            onRepayClick(
                                if (repayPair.balanceMinor > 0) DebtDirection.IN else DebtDirection.OUT,
                                repayPair.currencyCode,
                                abs(repayPair.balanceMinor),
                            )
                        } else {
                            onRepayClick(DebtDirection.OUT, "", 0L)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.debts_action_repay), maxLines = 1)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        // Кнопка включена, только когда валюта для списания есть.
                        val pair = repayPair ?: return@OutlinedButton
                        onWriteOffClick(pair.currencyCode, abs(pair.balanceMinor))
                    },
                    enabled = repayPair != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.debts_action_write_off), maxLines = 1)
                }
                OutlinedButton(onClick = onOpeningBalanceClick, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.debts_action_opening_balance), maxLines = 1)
                }
            }

            Text(
                stringResource(R.string.counterparty_history_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            state.history.forEach { row ->
                HistoryRow(
                    row = row,
                    currencySymbols = state.currencySymbols,
                    incomeColor = financeColors.income,
                    expenseColor = financeColors.expense,
                    modifier = Modifier.padding(vertical = 6.dp),
                    onClick = {
                        if (row.isAdjustment) {
                            onAdjustmentClick(row.id)
                        } else {
                            onOperationClick(row.id)
                        }
                    },
                )
            }
            if (state.history.isEmpty()) {
                Text(
                    stringResource(R.string.counterparty_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                )
            }
        }
    }

    when (deleteRequest?.blocked) {
        // Удалить нельзя: остаётся архив — предлагаем сразу его.
        true -> AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.counterparty_delete_title)) },
            text = { Text(stringResource(R.string.counterparty_delete_blocked_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissDelete()
                    viewModel.toggleArchive()
                }) { Text(stringResource(R.string.counterparty_menu_archive)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(R.string.common_cancel)) }
            },
        )
        false -> AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.counterparty_delete_title)) },
            text = { Text(stringResource(R.string.counterparty_delete_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(R.string.common_cancel)) }
            },
        )
        null -> Unit
    }
}

/** Карточка остатков: каждая валюта — строка со смыслом знака. */
@Composable
private fun BalancesCard(
    state: CounterpartyUiState,
    financeColors: com.expeysi.app.ui.theme.FinanceColors,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val nonZero = state.balances.filter { it.balanceMinor != 0L }
            if (nonZero.isEmpty()) {
                Text(
                    stringResource(R.string.debts_row_settled),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                nonZero.forEach { balance ->
                    val text = stringResource(
                        if (balance.balanceMinor > 0) {
                            R.string.debts_row_owed_to_me
                        } else {
                            R.string.debts_row_owed_by_me
                        },
                        AmountFormat.withCurrency(
                            abs(balance.balanceMinor),
                            state.currencySymbols[balance.currencyCode] ?: balance.currencyCode,
                        ),
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (balance.balanceMinor > 0) financeColors.income else financeColors.expense,
                    )
                }
            }
        }
    }
}

/** Строка истории: смысловоая иконка, подпись и знаковая сумма справа. */
@Composable
private fun HistoryRow(
    row: CounterpartyHistoryRow,
    currencySymbols: Map<String, String>,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        val color = if (row.deltaMinor >= 0) incomeColor else expenseColor
        DebtIcon(icon = historyIcon(row), tint = color)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = stringResource(row.labelRes),
                style = MaterialTheme.typography.bodyLarge,
            )
            // Подзаголовок: дата и заметка, если она есть.
            val dateText = Instant.ofEpochMilli(row.date)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(historyDateFormatter)
            val subtitle = listOf(dateText, row.note.trim().takeIf { it.isNotBlank() })
                .filterNotNull()
                .joinToString(separator = " · ")
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (row.dueDate != null) {
                val dueText = stringResource(
                    R.string.debts_due_label,
                    Instant.ofEpochMilli(row.dueDate).atZone(ZoneId.systemDefault()).toLocalDate().format(dueDateFormatter),
                )
                Text(
                    text = if (row.overdue) "$dueText · ${stringResource(R.string.debts_overdue)}" else dueText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.takeIf { row.overdue }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val symbol = currencySymbols[row.currencyCode] ?: row.currencyCode
        val amountText = (if (row.deltaMinor >= 0) "+" else "\u2212") +
            AmountFormat.withCurrency(abs(row.deltaMinor), symbol)
        Text(
            text = amountText,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
    }
}

private fun historyIcon(row: CounterpartyHistoryRow): ImageVector = if (row.isAdjustment) {
    if (row.labelRes == R.string.debts_kind_write_off) Icons.Filled.Block else Icons.Filled.Flag
} else {
    when {
        row.labelRes == R.string.debts_kind_repay_in -> Icons.Filled.CallReceived
        row.labelRes == R.string.debts_kind_repay_out -> Icons.AutoMirrored.Filled.CallMade
        else -> Icons.Filled.Handshake
    }
}
