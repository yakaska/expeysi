package com.expeysi.app.ui.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.SheetValue
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.domain.Currency
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.MoneyTextField
import com.expeysi.app.ui.icons.CategoryIcon
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onClose: () -> Unit,
    onAddCategoryClick: (TxType) -> Unit,
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
    val noteSuggestions by viewModel.noteSuggestions.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()
    val noteBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    var noteFieldValue by remember {
        mutableStateOf(TextFieldValue(note, selection = TextRange(note.length)))
    }
    var showNoteSuggestions by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    val showCategorySheet by viewModel.showCategorySheet.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCalculator by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(note) {
        if (noteFieldValue.text != note) {
            noteFieldValue = TextFieldValue(note, selection = TextRange(note.length))
        }
    }

    LaunchedEffect(saved) {
        if (saved) onClose()
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
            LazyColumn {
                items(accounts, key = { it.id }) { account ->
                    ListItem(
                        leadingContent = {
                            CategoryIcon(account.iconKey, account.colorKey)
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
        val categorySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.setCategorySheetVisible(false) },
            sheetState = categorySheetState,
            sheetGesturesEnabled = categorySheetState.currentValue == SheetValue.Expanded,
        ) {
            Text(
                if (type == TxType.EXPENSE) {
                    stringResource(R.string.add_category_expense)
                } else {
                    stringResource(R.string.add_category_income)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(categories, key = { it.id }) { category ->
                    ListItem(
                        leadingContent = {
                            CategoryIcon(category.iconKey, category.colorKey)
                        },
                        headlineContent = { Text(category.name) },
                        modifier = Modifier.clickable {
                            viewModel.onCategorySelect(category.id)
                            viewModel.setCategorySheetVisible(false)
                        },
                    )
                }
                item(key = "add") {
                    ListItem(
                        leadingContent = {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        },
                        headlineContent = { Text(stringResource(R.string.add_add_category_row)) },
                        modifier = Modifier.clickable { onAddCategoryClick(type) },
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isEdit) R.string.add_title_edit else R.string.add_title_new)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
            Button(
                onClick = viewModel::save,
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.common_save))
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
                        Text(
                            if (txType == TxType.EXPENSE) {
                                stringResource(R.string.operation_type_expense)
                            } else {
                                stringResource(R.string.operation_type_income)
                            },
                        )
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
                                .clickable { showTimePicker = true }
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

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.common_account)) },
                        supportingContent = {
                            Text(selectedAccount?.name ?: stringResource(R.string.common_select))
                        },
                        leadingContent = {
                            selectedAccount?.let {
                                CategoryIcon(it.iconKey, it.colorKey)
                            }
                        },
                        trailingContent = {
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { showAccountSheet = true },
                    )

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.add_category)) },
                        supportingContent = {
                            Text(selectedCategory?.name ?: stringResource(R.string.common_select))
                        },
                        leadingContent = {
                            selectedCategory?.let {
                                CategoryIcon(it.iconKey, it.colorKey)
                            }
                        },
                        trailingContent = {
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { viewModel.setCategorySheetVisible(true) },
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
                    MoneyTextField(
                        value = amountText,
                        onValueChange = viewModel::onAmountChange,
                        modifier = Modifier.fillMaxWidth(),
                        // %1$s — символ валюты.
                        label = { Text(stringResource(R.string.add_amount_label, amountSymbol)) },
                        trailingIcon = {
                            IconButton(onClick = { showCalculator = true }) {
                                Icon(Icons.Filled.Calculate, contentDescription = stringResource(R.string.calc_title))
                            }
                        },
                    )
                    Box {
                        OutlinedTextField(
                            value = noteFieldValue,
                            onValueChange = {
                                noteFieldValue = it
                                viewModel.onNoteChange(it.text)
                                showNoteSuggestions = true
                                coroutineScope.launch {
                                    noteBringIntoViewRequester.bringIntoView()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(noteBringIntoViewRequester),
                            label = { Text(stringResource(R.string.add_note_label)) },
                        )
                        DropdownMenu(
                            expanded = showNoteSuggestions && noteSuggestions.isNotEmpty(),
                            onDismissRequest = { showNoteSuggestions = false },
                            properties = PopupProperties(focusable = false),
                        ) {
                            noteSuggestions.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion) },
                                    onClick = {
                                        viewModel.onNoteChange(suggestion)
                                        showNoteSuggestions = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
