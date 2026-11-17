package com.expeysi.app.ui.debts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Block
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.data.db.CounterpartyEntity
import com.expeysi.app.ui.common.AmountFormat
import com.expeysi.app.ui.common.DayGroupHeader
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtCurrencySummary
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtDueStatus
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtHistoryRow
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtHistorySign
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtHistoryTone
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtOp
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.DebtOpKind
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.debtKindLabelRes
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.debtRowSemantics
import com.expeysi.app.ui.debts.CounterpartyDebtHistory.fold
import com.expeysi.app.ui.theme.ExpeysiTheme
import com.expeysi.app.ui.theme.FinanceColors
import com.expeysi.app.ui.theme.LocalFinanceColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val dueDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Деньги пришли на счёт: погашение получено или долг взят. */
private val cashInKinds = setOf(DebtOpKind.REPAY_IN, DebtOpKind.BORROW)

/** Деньги ушли со счёта: долг выдан или погашение отправлено. */
private val cashOutKinds = setOf(DebtOpKind.LEND, DebtOpKind.REPAY_OUT)

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
    val debtClosed by viewModel.debtClosed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val debtClosedMessage = stringResource(R.string.counterparty_debt_closed)
    val undoLabel = stringResource(R.string.common_undo)

    LaunchedEffect(debtClosed) {
        val event = debtClosed ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = debtClosedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            // Отмена: удаляем ровно ту операцию, что обнулила остаток.
            viewModel.undoDebtClose()
        }
        viewModel.consumeDebtClosed()
    }

    CounterpartyContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEditClick = onEditClick,
        onOperationClick = onOperationClick,
        onAdjustmentClick = onAdjustmentClick,
        onLendClick = onLendClick,
        onRepayClick = onRepayClick,
        onWriteOffClick = onWriteOffClick,
        onOpeningBalanceClick = onOpeningBalanceClick,
        onToggleArchive = viewModel::toggleArchive,
        onRequestDelete = viewModel::requestDelete,
    )

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CounterpartyContent(
    state: CounterpartyUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEditClick: (String) -> Unit,
    onOperationClick: (String) -> Unit,
    onAdjustmentClick: (String) -> Unit,
    onLendClick: (lend: Boolean) -> Unit,
    onRepayClick: (direction: DebtDirection, currency: String, amount: Long) -> Unit,
    onWriteOffClick: (currency: String, remaining: Long) -> Unit,
    onOpeningBalanceClick: () -> Unit,
    onToggleArchive: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val financeColors = LocalFinanceColors.current
    val counterparty = state.counterparty
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CounterpartyTopBar(
                state = state,
                menuOpen = menuOpen,
                onBack = onBack,
                onEditClick = onEditClick,
                onOpeningBalanceClick = onOpeningBalanceClick,
                onToggleArchive = onToggleArchive,
                onRequestDelete = onRequestDelete,
                onMenuOpen = { menuOpen = it },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (counterparty == null) {
            // Контрагент удалён на другом устройстве: экран пуст сразу после синка.
            Box(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (counterparty.archived) {
                item(key = "archived") {
                    Text(
                        stringResource(R.string.counterparty_archived_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            item(key = "summary") {
                SummaryCard(state = state, financeColors = financeColors)
            }

            item(key = "actions") {
                ActionButtons(
                    state = state,
                    onLendClick = onLendClick,
                    onRepayClick = onRepayClick,
                    onWriteOffClick = onWriteOffClick,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            item(key = "history-title") {
                Text(
                    stringResource(R.string.counterparty_history_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
            }

            // Группировка по дню: стик-заголовки как на главном экране.
            state.history.groupBy { row ->
                Instant.ofEpochMilli(row.op.date).atZone(zone).toLocalDate()
            }.forEach { (date, rows) ->
                stickyHeader(key = "date-$date") {
                    // Нетто наличных дня в одной валюте; при смешанных валютах
                    // или дне без наличных — заголовок только с датой.
                    val cashIn = rows.filter { it.op.kind in cashInKinds }.sumOf { abs(it.op.deltaMinor) }
                    val cashOut = rows.filter { it.op.kind in cashOutKinds }.sumOf { abs(it.op.deltaMinor) }
                    val firstSymbol = state.currencySymbols[rows.first().op.currencyCode]
                        ?: rows.first().op.currencyCode
                    DayGroupHeader(
                        dateLabel = when (date) {
                            today -> stringResource(R.string.home_today)
                            today.minusDays(1) -> stringResource(R.string.home_yesterday)
                            else -> date.format(dayFormatter)
                        },
                        incomeMinor = cashIn,
                        expenseMinor = cashOut,
                        currencySymbol = firstSymbol,
                        totalText = if (
                            rows.map { it.op.currencyCode }.distinct().size == 1 &&
                            (cashIn != 0L || cashOut != 0L)
                        ) {
                            null
                        } else {
                            ""
                        },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                rows.forEach { row ->
                    item(key = row.op.id) {
                        HistoryRow(
                            row = row,
                            currencySymbols = state.currencySymbols,
                            incomeColor = financeColors.income,
                            expenseColor = financeColors.expense,
                            modifier = Modifier.padding(vertical = 6.dp),
                            onClick = {
                                if (row.op.isAdjustment) {
                                    onAdjustmentClick(row.op.id)
                                } else {
                                    onOperationClick(row.op.id)
                                }
                            },
                        )
                    }
                }
            }
            if (state.history.isEmpty()) {
                item(key = "empty") {
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
    }
}

/** Верхняя панель: имя с подсказкой «Долг с…» и меню «Ещё» с начальным остатком. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CounterpartyTopBar(
    state: CounterpartyUiState,
    menuOpen: Boolean,
    onBack: () -> Unit,
    onEditClick: (String) -> Unit,
    onOpeningBalanceClick: () -> Unit,
    onToggleArchive: () -> Unit,
    onRequestDelete: () -> Unit,
    onMenuOpen: (Boolean) -> Unit,
) {
    val counterparty = state.counterparty
    val zone = ZoneId.systemDefault()
    TopAppBar(
        title = {
            Column {
                Text(
                    counterparty?.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Подсказка под именем: только когда операции уже есть.
                val firstDate = state.firstDate
                if (firstDate != null) {
                    val since = Instant.ofEpochMilli(firstDate).atZone(zone).toLocalDate().format(dueDateFormatter)
                    val closed = state.closingDate
                        ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(dueDateFormatter) }
                    Text(
                        text = if (closed != null) {
                            stringResource(R.string.counterparty_subtitle_closed, since, closed)
                        } else {
                            stringResource(R.string.counterparty_subtitle_since, since)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
            if (counterparty != null) {
                Box {
                    IconButton(onClick = { onMenuOpen(true) }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.counterparty_menu_cd),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpen(false) }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_edit)) },
                            onClick = {
                                onMenuOpen(false)
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
                                onMenuOpen(false)
                                onToggleArchive()
                            },
                        )
                        // Начальный остаток: из кнопок переехал в меню «Ещё».
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.debts_action_opening_balance)) },
                            onClick = {
                                onMenuOpen(false)
                                onOpeningBalanceClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_delete)) },
                            onClick = {
                                onMenuOpen(false)
                                onRequestDelete()
                            },
                        )
                    }
                }
            }
        },
    )
}

/** Карточка сводки: остаток с направлением и итоги потоков по каждой валюте. */
@Composable
private fun SummaryCard(
    state: CounterpartyUiState,
    financeColors: FinanceColors,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.summaries.isEmpty()) {
                // Операций нет вовсе: долга и нет, прежняя строка «Расчёты закрыты».
                Text(
                    stringResource(R.string.debts_row_settled),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.summaries.forEach { summary ->
                    val symbol = state.currencySymbols[summary.currencyCode] ?: summary.currencyCode
                    when {
                        summary.balanceMinor > 0L -> Text(
                            text = stringResource(
                                R.string.debts_row_owed_to_me,
                                AmountFormat.withCurrency(summary.balanceMinor, symbol),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = financeColors.income,
                        )
                        summary.balanceMinor < 0L -> Text(
                            text = stringResource(
                                R.string.debts_row_owed_by_me,
                                AmountFormat.withCurrency(abs(summary.balanceMinor), symbol),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = financeColors.expense,
                        )
                        else -> {
                            Text(
                                stringResource(R.string.counterparty_settled_paid),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            summary.closingDate
                                ?.let {
                                    Instant.ofEpochMilli(it)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                        .format(dueDateFormatter)
                                }
                                ?.let { closed ->
                                    Text(
                                        stringResource(R.string.counterparty_settled_date, closed),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                        }
                    }
                    summaryTotals(summary, symbol)?.let { totals ->
                        Text(
                            text = totals,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Итоги потоков сводки: «выдано/погашено», «взято/погашено» или «выдано/возвращено». */
@Composable
private fun summaryTotals(summary: DebtCurrencySummary, symbol: String): String? = when {
    summary.balanceMinor > 0L && (summary.lentMinor > 0 || summary.repaidInMinor > 0) ->
        stringResource(
            R.string.counterparty_summary_given_repaid,
            AmountFormat.withCurrency(summary.lentMinor, symbol),
            AmountFormat.withCurrency(summary.repaidInMinor, symbol),
        )
    summary.balanceMinor < 0L && (summary.borrowedMinor > 0 || summary.repaidOutMinor > 0) ->
        stringResource(
            R.string.counterparty_summary_taken_repaid,
            AmountFormat.withCurrency(summary.borrowedMinor, symbol),
            AmountFormat.withCurrency(summary.repaidOutMinor, symbol),
        )
    summary.isSettled && (summary.lentMinor > 0 || summary.repaidInMinor > 0) ->
        stringResource(
            R.string.counterparty_summary_given_returned,
            AmountFormat.withCurrency(summary.lentMinor, symbol),
            AmountFormat.withCurrency(summary.repaidInMinor, symbol),
        )
    summary.isSettled && (summary.borrowedMinor > 0 || summary.repaidOutMinor > 0) ->
        stringResource(
            R.string.counterparty_summary_taken_repaid,
            AmountFormat.withCurrency(summary.borrowedMinor, symbol),
            AmountFormat.withCurrency(summary.repaidOutMinor, symbol),
        )
    else -> null
}

/**
 * Кнопки действий сеткой 2×2: «Дать» и «Взять» сверху, «Погасить» и
 * «Списание» снизу. Списания без остатка нет — «Погасить» забирает всю
 * ширину ряда. Начальный остаток живёт в меню «Ещё» верхней панели.
 */
@Composable
private fun ActionButtons(
    state: CounterpartyUiState,
    onLendClick: (lend: Boolean) -> Unit,
    onRepayClick: (direction: DebtDirection, currency: String, amount: Long) -> Unit,
    onWriteOffClick: (currency: String, remaining: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nonZero = state.summaries.firstOrNull { it.balanceMinor != 0L }
    val writeOffVisible = nonZero != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = { onLendClick(true) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.debts_action_lend), maxLines = 1)
            }
            FilledTonalButton(onClick = { onLendClick(false) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.debts_action_borrow), maxLines = 1)
            }
        }
        if (writeOffVisible) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = {
                        onRepayClick(
                            if (nonZero.balanceMinor > 0) DebtDirection.IN else DebtDirection.OUT,
                            nonZero.currencyCode,
                            abs(nonZero.balanceMinor),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.debts_action_repay), maxLines = 1)
                }
                // Кнопка видна, только когда есть что списывать: всегда включена.
                OutlinedButton(
                    onClick = { onWriteOffClick(nonZero.currencyCode, abs(nonZero.balanceMinor)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.debts_action_write_off), maxLines = 1)
                }
            }
        } else {
            FilledTonalButton(
                onClick = { onRepayClick(DebtDirection.OUT, "", 0L) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.debts_action_repay), maxLines = 1)
            }
        }
    }
}

/** Строка истории: смысловая иконка, время с заметкой и знаковая сумма с бегущим остатком. */
@Composable
private fun HistoryRow(
    row: DebtHistoryRow,
    currencySymbols: Map<String, String>,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val semantics = debtRowSemantics(row.op.kind, row.op.deltaMinor)
    val color = when (semantics.tone) {
        DebtHistoryTone.POSITIVE -> incomeColor
        DebtHistoryTone.NEGATIVE -> expenseColor
        DebtHistoryTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        DebtIcon(icon = historyIcon(row.op.kind), tint = color)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = stringResource(debtKindLabelRes(row.op.kind)),
                style = MaterialTheme.typography.bodyLarge,
            )
            // Только время: дата уже в стик-заголовке дня.
            val timeText = Instant.ofEpochMilli(row.op.date)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(timeFormatter)
            val subtitle = listOf(timeText, row.op.note.trim().takeIf { it.isNotBlank() })
                .filterNotNull()
                .joinToString(separator = " · ")
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.dueStatus?.let { status ->
                DueLine(status = status, dueDate = row.op.dueDate)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val symbol = currencySymbols[row.op.currencyCode] ?: row.op.currencyCode
            Text(
                text = amountText(semantics.sign, abs(row.op.deltaMinor), symbol),
                style = MaterialTheme.typography.bodyLarge,
                color = color,
            )
            Text(
                text = stringResource(
                    R.string.counterparty_balance_label,
                    balanceText(row.runningBalanceMinor),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Строки срока возврата: открыт долг — «Срок…» с просрочкой, закрыт — оценка закрытия. */
@Composable
private fun DueLine(
    status: DebtDueStatus,
    dueDate: Long?,
) {
    val dueText = dueDate
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dueDateFormatter) }
        ?.let { stringResource(R.string.debts_due_label, it) } ?: return
    when (status) {
        is DebtDueStatus.Open -> {
            val overdue = status.overdueDays > 0
            Text(
                text = if (overdue) {
                    "$dueText · " + pluralStringResource(
                        R.plurals.counterparty_overdue_days,
                        status.overdueDays,
                        status.overdueDays,
                    )
                } else {
                    dueText
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        is DebtDueStatus.Closed -> {
            val early = abs(if (status.daysDelta < 0) status.daysDelta else 0)
            val late = abs(if (status.daysDelta > 0) status.daysDelta else 0)
            Text(
                text = when {
                    status.daysDelta == 0 -> stringResource(R.string.counterparty_closed_on_time)
                    status.daysDelta < 0 -> pluralStringResource(
                        R.plurals.counterparty_closed_early,
                        early,
                        early,
                    )
                    else -> pluralStringResource(
                        R.plurals.counterparty_closed_late,
                        late,
                        late,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun historyIcon(kind: DebtOpKind): ImageVector = when (kind) {
    DebtOpKind.WRITE_OFF -> Icons.Filled.Block
    DebtOpKind.OPENING_BALANCE -> Icons.Filled.Flag
    DebtOpKind.REPAY_IN -> Icons.Filled.CallReceived
    DebtOpKind.REPAY_OUT -> Icons.AutoMirrored.Filled.CallMade
    DebtOpKind.LEND, DebtOpKind.BORROW -> Icons.Filled.Handshake
}

/** Знаковая сумма: «+», «−» или без знака, когда наличных нет. */
private fun amountText(sign: DebtHistorySign?, minor: Long, symbol: String): String = when (sign) {
    DebtHistorySign.PLUS -> "+"
    DebtHistorySign.MINUS -> "\u2212"
    null -> ""
} + AmountFormat.withCurrency(minor, symbol)

/** Бегущий остаток: минус у долга «я должен», знак плюса опускается. */
private fun balanceText(minor: Long): String =
    if (minor < 0) "\u2212" + AmountFormat.amount(abs(minor)) else AmountFormat.amount(minor)

/* ----------------------------------- Previews ----------------------------------- */

private const val PreviewCurrency = "RUB"
private val PreviewSymbols = mapOf(PreviewCurrency to "₽")

/** Момент превью: daysAgo дней назад (отрицательное — в будущем) в час:минуту. */
private fun previewInstant(daysAgo: Long, hour: Int, minute: Int): Long =
    LocalDate.now().minusDays(daysAgo).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun previewOp(
    id: String,
    date: Long,
    kind: DebtOpKind,
    deltaMinor: Long,
    note: String = "",
    dueDate: Long? = null,
) = DebtOp(
    id = id,
    isAdjustment = kind == DebtOpKind.WRITE_OFF || kind == DebtOpKind.OPENING_BALANCE,
    date = date,
    updatedAt = date,
    currencyCode = PreviewCurrency,
    kind = kind,
    deltaMinor = deltaMinor,
    note = note,
    dueDate = dueDate,
)

private fun previewCounterparty(name: String): CounterpartyEntity = CounterpartyEntity(
    id = "preview",
    name = name,
    type = "person",
    updatedAt = 0L,
)

/** Состояние экрана из набора операций: свёртка считает сводки и остатки. */
private fun previewState(
    name: String,
    ops: List<DebtOp>,
): CounterpartyUiState {
    val foldResult = fold(ops, LocalDate.now(), ZoneId.systemDefault())
    return CounterpartyUiState(
        counterparty = previewCounterparty(name),
        summaries = foldResult.summaries,
        history = foldResult.rows,
        currencySymbols = PreviewSymbols,
        firstDate = foldResult.firstDate,
        closingDate = foldResult.closingDate,
    )
}

@Composable
private fun PreviewCounterpartyContent(state: CounterpartyUiState) {
    CounterpartyContent(
        state = state,
        snackbarHostState = remember { SnackbarHostState() },
        onBack = {},
        onEditClick = {},
        onOperationClick = {},
        onAdjustmentClick = {},
        onLendClick = {},
        onRepayClick = { _, _, _ -> },
        onWriteOffClick = { _, _ -> },
        onOpeningBalanceClick = {},
        onToggleArchive = {},
        onRequestDelete = {},
    )
}

@Preview(name = "Долг открыт / light", showBackground = true)
@Composable
private fun PreviewOpenDebt() {
    val state = previewState(
        name = "Аня Петрова",
        ops = listOf(
            previewOp(
                id = "lend",
                date = previewInstant(2, 12, 30),
                kind = DebtOpKind.LEND,
                deltaMinor = 100_000_00,
                note = "Заняла до зарплаты",
                dueDate = previewInstant(-5, 0, 0),
            ),
            previewOp(
                id = "repay",
                date = previewInstant(1, 10, 12),
                kind = DebtOpKind.REPAY_IN,
                deltaMinor = -50_000_00,
            ),
        ),
    )
    ExpeysiTheme { PreviewCounterpartyContent(state) }
}

@Preview(name = "Долг закрыт / light", showBackground = true)
@Composable
private fun PreviewSettledDebt() {
    val state = previewState(
        name = "Аня Петрова",
        ops = listOf(
            previewOp(
                id = "lend",
                date = previewInstant(30, 18, 5),
                kind = DebtOpKind.LEND,
                deltaMinor = 100_000_00,
                dueDate = previewInstant(10, 0, 0),
            ),
            previewOp(
                id = "repay",
                date = previewInstant(12, 9, 45),
                kind = DebtOpKind.REPAY_IN,
                deltaMinor = -100_000_00,
            ),
        ),
    )
    ExpeysiTheme { PreviewCounterpartyContent(state) }
}

@Preview(name = "Просрочен / light", showBackground = true)
@Composable
private fun PreviewOverdueDebt() {
    val state = previewState(
        name = "Аня Петрова",
        ops = listOf(
            previewOp(
                id = "lend",
                date = previewInstant(14, 11, 0),
                kind = DebtOpKind.LEND,
                deltaMinor = 250_000_00,
                note = "На ремонт машины",
                dueDate = previewInstant(3, 0, 0),
            ),
        ),
    )
    ExpeysiTheme { PreviewCounterpartyContent(state) }
}

@Preview(name = "Длинные названия / light", showBackground = true)
@Composable
private fun PreviewLongNames() {
    val state = previewState(
        name = "Илья Петрович Самозванов-Крутоярынский",
        ops = listOf(
            previewOp(
                id = "lend",
                date = previewInstant(2, 12, 30),
                kind = DebtOpKind.LEND,
                deltaMinor = 100_000_00,
                note = "Вернуть часть долга за аренду гаража на улице Лермонтова",
            ),
            previewOp(
                id = "repay",
                date = previewInstant(2, 12, 31),
                kind = DebtOpKind.REPAY_IN,
                deltaMinor = -50_000_00,
                note = "Перевод с карты Сбера",
            ),
        ),
    )
    ExpeysiTheme { PreviewCounterpartyContent(state) }
}

@Preview(name = "Долг открыт / крупный шрифт", showBackground = true, fontScale = 2f)
@Composable
private fun PreviewLargeFontScale() {
    val state = previewState(
        name = "Аня Петрова",
        ops = listOf(
            previewOp(
                id = "lend",
                date = previewInstant(2, 12, 30),
                kind = DebtOpKind.LEND,
                deltaMinor = 100_000_00,
                note = "Заняла до зарплаты",
            ),
            previewOp(
                id = "repay",
                date = previewInstant(1, 10, 12),
                kind = DebtOpKind.REPAY_IN,
                deltaMinor = -50_000_00,
            ),
        ),
    )
    ExpeysiTheme { PreviewCounterpartyContent(state) }
}
