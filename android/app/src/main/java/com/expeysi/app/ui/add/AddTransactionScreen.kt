package com.expeysi.app.ui.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.domain.Calculator
import com.expeysi.app.domain.Currency
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.icons.IconCatalog
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onClose: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val type by viewModel.type.collectAsStateWithLifecycle()
    val amountText by viewModel.amountText.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedAccountId by viewModel.selectedAccountId.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val currencySymbols by viewModel.currencySymbols.collectAsStateWithLifecycle()
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val time by viewModel.time.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCalculator by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) onClose()
    }

    if (showCalculator) {
        CalculatorDialog(
            initialValue = amountText,
            onDismiss = { showCalculator = false },
            onApply = { result ->
                viewModel.onAmountChange(Calculator.groupDigits(result))
                showCalculator = false
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить операцию?") },
            text = { Text("Запись исчезнет из списка и перестанет влиять на баланс.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.delete()
                    },
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") }
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
                ) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
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
            title = { Text("Время") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onTimePick(pickerState.hour, pickerState.minute)
                        showTimePicker = false
                    },
                ) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Отмена") }
            },
        )
    }

    if (showAccountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Text(
                "Счёт",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(accounts, key = { it.id }) { account ->
                    ListItem(
                        leadingContent = {
                            Icon(IconCatalog.icon(account.iconKey), contentDescription = null)
                        },
                        headlineContent = { Text(account.name) },
                        supportingContent = {
                            balances[account.balanceId]?.let { Text(it.name) }
                        },
                        modifier = Modifier.clickable {
                            viewModel.onAccountSelect(account.id)
                            showAccountSheet = false
                        },
                    )
                }
            }
        }
    }

    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Text(
                if (type == TxType.EXPENSE) "Категория расхода" else "Категория дохода",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(categories, key = { it.id }) { category ->
                    ListItem(
                        leadingContent = {
                            Icon(IconCatalog.icon(category.iconKey), contentDescription = null)
                        },
                        headlineContent = { Text(category.name) },
                        modifier = Modifier.clickable {
                            viewModel.onCategorySelect(category.id)
                            showCategorySheet = false
                        },
                    )
                }
            }
        }
    }

    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }
    val amountSymbol = selectedAccount
        ?.let { balances[it.balanceId]?.currencyCode }
        ?.let { currencySymbols[it] ?: it }
        ?: Currency.DEFAULT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEdit) "Изменить операцию" else "Новая операция") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (viewModel.isEdit) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = viewModel::save,
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp),
            ) {
                Text("Сохранить")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TxType.entries.forEachIndexed { index, txType ->
                    SegmentedButton(
                        selected = type == txType,
                        onClick = { viewModel.onTypeChange(txType) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TxType.entries.size),
                    ) {
                        Text(if (txType == TxType.EXPENSE) "Расход" else "Доход")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDatePicker = true }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                "Дата",
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
                                .clickable { showTimePicker = true }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                "Время",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                time.format(timeFormatter),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }

                    ListItem(
                        headlineContent = { Text("Счёт") },
                        supportingContent = {
                            Text(selectedAccount?.name ?: "Выбрать…")
                        },
                        leadingContent = {
                            selectedAccount?.let {
                                Icon(IconCatalog.icon(it.iconKey), contentDescription = null)
                            }
                        },
                        trailingContent = {
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { showAccountSheet = true },
                    )

                    ListItem(
                        headlineContent = { Text("Категория") },
                        supportingContent = {
                            Text(selectedCategory?.name ?: "Выбрать…")
                        },
                        leadingContent = {
                            selectedCategory?.let {
                                Icon(IconCatalog.icon(it.iconKey), contentDescription = null)
                            }
                        },
                        trailingContent = {
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { showCategorySheet = true },
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = viewModel::onAmountChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Сумма, $amountSymbol") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showCalculator = true }) {
                                Icon(Icons.Filled.Calculate, contentDescription = "Калькулятор")
                            }
                        },
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = viewModel::onNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Комментарий (необязательно)") },
                        singleLine = true,
                    )
                }
            }
        }
    }
}
