package com.expeysi.app.ui.debts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.domain.Currency
import com.expeysi.app.ui.MoneyTextField
import com.expeysi.app.ui.add.CalculatorDialog
import com.expeysi.app.ui.add.accountSheetGroups
import com.expeysi.app.ui.common.AmountFormat
import com.expeysi.app.ui.icons.BalanceIcon
import com.expeysi.app.ui.icons.CategoryIcon
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Резерв прокрутки под контентом: высота панели «Сохранить» плюс отступ.
private val SaveBarScrollPadding = 96.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtOperationScreen(
    onClose: () -> Unit,
    onCreateCounterpartyClick: () -> Unit,
    viewModel: DebtOperationViewModel = hiltViewModel(),
) {
    val date by viewModel.date.collectAsStateWithLifecycle()
    val time by viewModel.time.collectAsStateWithLifecycle()
    val amountText by viewModel.amountText.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val counterparties by viewModel.counterparties.collectAsStateWithLifecycle()
    val currencySymbols by viewModel.currencySymbols.collectAsStateWithLifecycle()
    val accountBalances by viewModel.accountBalances.collectAsStateWithLifecycle()
    val selectedAccountId by viewModel.selectedAccountId.collectAsStateWithLifecycle()
    val counterpartyId by viewModel.counterpartyId.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val dueDate by viewModel.dueDate.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val liveBalance by viewModel.liveBalance.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val flipWarning by viewModel.showFlipWarning.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }
    var showCounterpartySheet by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCalculator by rememberSaveable { mutableStateOf(false) }

    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }
    val counterparty = counterparties.firstOrNull { it.id == counterpartyId }
    val selectedCode = selectedAccount
        ?.let { balances[it.balanceId]?.currencyCode ?: it.currencyCode }
        ?: Currency.DEFAULT
    val amountSymbol = currencySymbols[selectedCode] ?: selectedCode
    val canSave = viewModel.canSave(liveBalance)

    LaunchedEffect(saved) {
        if (saved) onClose()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (viewModel.isEdit) R.string.add_title_edit else R.string.add_title_new,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (viewModel.isEdit) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Непрозрачная подложка: прокручиваемое содержимое не просвечивает под панелью.
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = viewModel::save,
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(
                        // В-disabled подпись чуть контрастнее стандартных 38% непрозрачности.
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = SaveBarScrollPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DebtFormCard {
                OperationDateTimeRow(
                    date = date,
                    time = time,
                    onDateClick = { showDatePicker = true },
                    onTimeClick = { showTimePicker = true },
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(DebtDirection.OUT, DebtDirection.IN).forEachIndexed { index, directionItem ->
                        SegmentedButton(
                            selected = direction == directionItem,
                            onClick = { viewModel.onDirectionChange(directionItem) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        ) {
                            Text(
                                stringResource(
                                    if (directionItem == DebtDirection.OUT) {
                                        R.string.debts_direction_out
                                    } else {
                                        R.string.debts_direction_in
                                    },
                                ),
                            )
                        }
                    }
                }
                DebtSelectorRow(
                    label = stringResource(R.string.common_account),
                    value = selectedAccount?.name,
                    // %1$s — код валюты, %2$s — остаток.
                    subtitle = selectedAccount
                        ?.let { accountBalances[it.id] }
                        ?.let { stringResource(R.string.add_transfer_account_subtitle, selectedCode, AmountFormat.amount(it)) },
                    subtitleNegative = (selectedAccount?.let { accountBalances[it.id] } ?: 0L) < 0,
                    icon = selectedAccount?.let { account ->
                        { CategoryIcon(account.iconKey, account.colorKey) }
                    },
                    onClick = { showAccountSheet = true },
                )
                if (!viewModel.counterpartyLocked) {
                    DebtSelectorRow(
                        label = stringResource(R.string.debts_counterparty_label),
                        value = counterparty?.name,
                        icon = counterparty?.let { { DebtIcon(Icons.Filled.Handshake, MaterialTheme.colorScheme.primaryContainer) } },
                        onClick = { showCounterpartySheet = true },
                    )
                }
            }

            DebtFormCard {
                MoneyTextField(
                    value = amountText,
                    onValueChange = viewModel::onAmountChange,
                    // %1$s — символ валюты.
                    label = { Text(stringResource(R.string.add_amount_label, amountSymbol), maxLines = 1) },
                    colors = debtFieldColors(),
                    trailingIcon = {
                        IconButton(onClick = { showCalculator = true }) {
                            Icon(
                                Icons.Filled.Calculate,
                                contentDescription = stringResource(R.string.calc_title),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions.Default,
                )
                if (viewModel.dueDateVisible(liveBalance)) {
                    DueDateRow(
                        dueDate = dueDate,
                        onClick = { showDueDatePicker = true },
                        onClear = viewModel::clearDueDate,
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text(stringResource(R.string.add_note_label)) },
                    colors = debtFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showCalculator) {
        CalculatorDialog(
            initialValue = amountText,
            onDismiss = { showCalculator = false },
            onApply = { result ->
                viewModel.onAmountChange(result)
                showCalculator = false
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.add_delete_title)) },
            text = { Text(stringResource(R.string.add_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.delete()
                    },
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let(viewModel::onDatePick)
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDueDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate
                ?.atStartOfDay(java.time.ZoneId.of("UTC"))?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let(viewModel::onDueDatePick)
                        showDueDatePicker = false
                    },
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDueDatePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.add_time)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onTimePick(pickerState.hour, pickerState.minute)
                        showTimePicker = false
                    },
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showAccountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Text(
                stringResource(R.string.common_account),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                accountSheetGroups(viewModel.pickableAccounts(), balances).forEach { group ->
                    if (group.balanceName.isNotBlank()) {
                        item(key = "group-${group.balanceId}") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, top = 12.dp),
                            ) {
                                BalanceIcon(
                                    name = group.balanceName,
                                    iconKey = group.iconKey,
                                    colorKey = group.colorKey,
                                    size = 24.dp,
                                )
                                Text(
                                    text = group.balanceName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                    items(group.accounts, key = { it.id }) { account ->
                        AccountSheetItem(
                            account = account,
                            balances = balances,
                            accountBalances = accountBalances,
                            onClick = {
                                viewModel.onAccountSelect(account.id)
                                showAccountSheet = false
                            },
                        )
                    }
                }
            }
        }
    }

    if (showCounterpartySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCounterpartySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                stringResource(R.string.debts_counterparty_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                if (counterparties.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.debts_no_counterparties),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(counterparties, key = { it.id }) { counterpartyItem ->
                    ListItem(
                        leadingContent = {
                            DebtIcon(Icons.Filled.Handshake, MaterialTheme.colorScheme.primaryContainer)
                        },
                        headlineContent = { Text(counterpartyItem.name) },
                        modifier = Modifier.clickable {
                            viewModel.onCounterpartySelect(counterpartyItem.id)
                            showCounterpartySheet = false
                        },
                    )
                }
                item(key = "add") {
                    ListItem(
                        leadingContent = {
                            DebtIcon(Icons.Filled.Add, MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text(stringResource(R.string.counterparty_add_title)) },
                        modifier = Modifier.clickable {
                            showCounterpartySheet = false
                            onCreateCounterpartyClick()
                        },
                    )
                }
            }
        }
    }

    flipWarning?.let { preview ->
        val beforeText = signedAmountText(preview.before, amountSymbol)
        val afterText = signedAmountText(preview.after, amountSymbol)
        AlertDialog(
            onDismissRequest = viewModel::dismissFlip,
            title = { Text(stringResource(R.string.debts_flip_title)) },
            text = { Text(stringResource(R.string.debts_flip_text, beforeText, afterText)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmFlip) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissFlip) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/** Остаток со знаком: минус у отрицательного, плюс опускается. */
private fun signedAmountText(minor: Long, symbol: String): String =
    if (minor >= 0) {
        AmountFormat.withCurrency(minor, symbol)
    } else {
        "\u2212" + AmountFormat.withCurrency(abs(minor), symbol)
    }

/** Дата и время операции: два центральных кликабельных столбца. */
@Composable
private fun OperationDateTimeRow(
    date: LocalDate,
    time: LocalTime,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .clickable { onDateClick() }
                .padding(vertical = 10.dp),
        ) {
            Text(
                stringResource(R.string.add_date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                date.format(dateFormatter),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .clickable { onTimeClick() }
                .padding(vertical = 10.dp),
        ) {
            Text(
                stringResource(R.string.add_time),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                time.format(timeFormatter),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** Строка срока возврата: значение или «Выбрать…», при заданном сроке — крестик. */
@Composable
private fun DueDateRow(
    dueDate: LocalDate?,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onClick() }
                .padding(vertical = 6.dp),
        ) {
            Text(
                stringResource(R.string.debts_due_date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = dueDate?.format(dateFormatter) ?: stringResource(R.string.common_select),
                style = MaterialTheme.typography.bodyLarge,
                color = if (dueDate == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (dueDate != null) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.debts_due_clear_cd),
                )
            }
        }
    }
}

/** Строка счёта в шторке: иконка, имя и остаток; код валюты берётся из баланса. */
@Composable
private fun AccountSheetItem(
    account: AccountEntity,
    balances: Map<String, BalanceEntity>,
    accountBalances: Map<String, Long>,
    onClick: () -> Unit,
) {
    val code = account.let { balances[it.balanceId]?.currencyCode ?: it.currencyCode } ?: Currency.DEFAULT
    ListItem(
        leadingContent = {
            CategoryIcon(account.iconKey, account.colorKey)
        },
        headlineContent = { Text(account.name) },
        supportingContent = {
            accountBalances[account.id]?.let { balance ->
                Text(
                    // %1$s — код валюты, %2$s — остаток.
                    stringResource(R.string.add_transfer_account_subtitle, code, AmountFormat.amount(balance)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier
            .padding(start = 20.dp)
            .clickable { onClick() },
    )
}
