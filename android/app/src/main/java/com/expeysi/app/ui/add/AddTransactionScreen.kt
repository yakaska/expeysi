package com.expeysi.app.ui.add

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.domain.Currency
import com.expeysi.app.domain.TransferCalc
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.MoneyTextField
import com.expeysi.app.ui.common.AmountFormat
import com.expeysi.app.ui.icons.BalanceIcon
import com.expeysi.app.ui.icons.CategoryIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Повышенный контраст полей в тёмной теме вместо стандартных 0xFF949496/0xFF8E8E93.
private val DarkFieldBorder = Color(0xFFB5B5BA)
private val DarkFieldLabel = Color(0xFFA6A6AB)

// Резерв прокрутки под контентом: высота панели «Сохранить» (56dp + 12dp × 2) плюс 16dp.
private val SaveBarScrollPadding = 96.dp

/** Поле суммы, для которого вызван калькулятор: обычное/списание или зачисление. */
private const val CALCULATOR_FROM = 0
private const val CALCULATOR_TO = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onClose: () -> Unit,
    onAddCategoryClick: (TxType) -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val amountText by viewModel.amountText.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedAccountId by viewModel.selectedAccountId.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val currencySymbols by viewModel.currencySymbols.collectAsStateWithLifecycle()
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val fromAccountId by viewModel.fromAccountId.collectAsStateWithLifecycle()
    val toAccountId by viewModel.toAccountId.collectAsStateWithLifecycle()
    val fromAmountText by viewModel.fromAmountText.collectAsStateWithLifecycle()
    val toAmountText by viewModel.toAmountText.collectAsStateWithLifecycle()
    val rateText by viewModel.rateText.collectAsStateWithLifecycle()
    val feeText by viewModel.feeText.collectAsStateWithLifecycle()
    val feeVisible by viewModel.feeVisible.collectAsStateWithLifecycle()
    val accountBalances by viewModel.accountBalances.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val time by viewModel.time.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val noteSuggestions by viewModel.noteSuggestions.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()
    val rateInverted by viewModel.rateInverted.collectAsStateWithLifecycle()
    val rateErrorVisible by viewModel.rateErrorVisible.collectAsStateWithLifecycle()
    val feeErrorVisible by viewModel.feeErrorVisible.collectAsStateWithLifecycle()
    val noteBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Клавиатурная цепочка перевода: списание → курс → зачисление → комиссия → заметка.
    val rateFocus = remember { FocusRequester() }
    val receiveFocus = remember { FocusRequester() }
    val feeFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }
    // Каждое поле дотягивается выше клавиатуры собственным requester-ом.
    val amountBringIntoView = remember { BringIntoViewRequester() }
    val rateBringIntoView = remember { BringIntoViewRequester() }
    val receiveBringIntoView = remember { BringIntoViewRequester() }
    val feeBringIntoView = remember { BringIntoViewRequester() }

    var noteFieldValue by remember {
        mutableStateOf(TextFieldValue(note, selection = TextRange(note.length)))
    }
    var showNoteSuggestions by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    // Выбор счёта в шторке: обычный/«со счёта» или «на счёт» в переводе.
    var accountSheetForTo by rememberSaveable { mutableStateOf(false) }
    // Для какого поля суммы открыт калькулятор.
    var calculatorTarget by rememberSaveable { mutableIntStateOf(CALCULATOR_FROM) }
    val showCategorySheet by viewModel.showCategorySheet.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCalculator by rememberSaveable { mutableStateOf(false) }
    // Поле курса свернуто в подпись, пока пользователь её не раскроет.
    var rateFieldVisible by rememberSaveable { mutableStateOf(false) }

    val isTransfer = mode == AddMode.TRANSFER

    LaunchedEffect(note) {
        if (noteFieldValue.text != note) {
            noteFieldValue = TextFieldValue(note, selection = TextRange(note.length))
        }
    }

    LaunchedEffect(saved) {
        if (saved) onClose()
    }

    if (showCalculator) {
        val calculatorValue = when {
            calculatorTarget == CALCULATOR_TO -> toAmountText
            isTransfer -> fromAmountText
            else -> amountText
        }
        CalculatorDialog(
            initialValue = calculatorValue,
            onDismiss = { showCalculator = false },
            onApply = { result ->
                when {
                    calculatorTarget == CALCULATOR_TO -> viewModel.onToAmountChange(result)
                    isTransfer -> viewModel.onFromAmountChange(result)
                    else -> viewModel.onAmountChange(result)
                }
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
                stringResource(
                    when {
                        isTransfer && accountSheetForTo -> R.string.add_transfer_to
                        isTransfer -> R.string.add_transfer_from
                        else -> R.string.common_account
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                // Счета сгруппированы по балансам: заголовок баланса, под ним
                // с отступом его счета; баланс по имени в строке уже не нужен.
                accountSheetGroups(accounts, balances).forEach { group ->
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
                        ListItem(
                            leadingContent = {
                                CategoryIcon(account.iconKey, account.colorKey)
                            },
                            headlineContent = { Text(account.name) },
                            modifier = Modifier
                                .padding(start = 20.dp)
                                .clickable {
                                    when {
                                        isTransfer && accountSheetForTo -> viewModel.onToAccountSelect(account.id)
                                        isTransfer -> viewModel.onFromAccountSelect(account.id)
                                        else -> viewModel.onAccountSelect(account.id)
                                    }
                                    showAccountSheet = false
                                },
                        )
                    }
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
                if (mode == AddMode.EXPENSE) {
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
                        modifier = Modifier.clickable { onAddCategoryClick(mode.txType ?: TxType.EXPENSE) },
                    )
                }
            }
        }
    }

    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }
    val fromAccount = accounts.firstOrNull { it.id == fromAccountId }
    val toAccount = accounts.firstOrNull { it.id == toAccountId }

    /** Символ валюты счёта = валюта его баланса; без счёта — валюта по умолчанию. */
    fun currencySymbolOf(account: AccountEntity?): String = account
        ?.let { balances[it.balanceId]?.currencyCode }
        ?.let { currencySymbols[it] ?: it }
        ?: Currency.DEFAULT

    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }

    val amountSymbol = currencySymbolOf(selectedAccount)
    val fromSymbol = currencySymbolOf(fromAccount)
    val toSymbol = currencySymbolOf(toAccount)
    // Коды валют из балансов счетов; курс перевода показывается по кодам пары.
    val fromCode = fromAccount?.let { balances[it.balanceId]?.currencyCode }
    val toCode = toAccount?.let { balances[it.balanceId]?.currencyCode }
    // Курс и поле зачисления видны, только когда валюты счетов различаются.
    val transferCurrencies = if (fromCode != null && toCode != null && fromCode != toCode) fromCode to toCode else null
    // Одинаковый счёт с обеих сторон перевода — ошибка выбора.
    val sameTransferAccount = fromAccountId != null && fromAccountId == toAccountId
    val fromBalanceMinor = fromAccount?.let { accountBalances[it.id] }
    val toBalanceMinor = toAccount?.let { accountBalances[it.id] }
    // Подписи строк выбранного счёта: код валюты и остаток, одинаково на всех вкладках.
    val selectedAccountSubtitle = selectedAccount
        ?.let { balances[it.balanceId]?.currencyCode }
        ?.let { code ->
            selectedAccount?.let { accountBalances[it.id] }
                ?.let { balance ->
                    // %1$s — код валюты, %2$s — остаток.
                    stringResource(R.string.add_transfer_account_subtitle, code, AmountFormat.amount(balance))
                }
        }
    val fromAccountSubtitle = fromCode?.let { code ->
        fromBalanceMinor?.let { balance ->
            stringResource(R.string.add_transfer_account_subtitle, code, AmountFormat.amount(balance))
        }
    }
    val toAccountSubtitle = toCode?.let { code ->
        toBalanceMinor?.let { balance ->
            stringResource(R.string.add_transfer_account_subtitle, code, AmountFormat.amount(balance))
        }
    }
    // Отрицательный остаток подсвечивается цветом ошибки в подписи строки счёта.
    val selectedAccountBalanceNegative = (selectedAccount?.let { accountBalances[it.id] } ?: 0L) < 0
    val fromAccountBalanceNegative = (fromBalanceMinor ?: 0L) < 0
    val toAccountBalanceNegative = (toBalanceMinor ?: 0L) < 0

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
                // Последняя карточка дотягивается выше панели сохранения.
                .padding(bottom = SaveBarScrollPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AddMode.entries.forEachIndexed { index, modeItem ->
                    SegmentedButton(
                        selected = mode == modeItem,
                        onClick = { viewModel.onModeChange(modeItem) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = AddMode.entries.size),
                        colors = selectedTabColors(),
                    ) {
                        Text(
                            when (modeItem) {
                                AddMode.EXPENSE -> stringResource(R.string.operation_type_expense)
                                AddMode.INCOME -> stringResource(R.string.operation_type_income)
                                AddMode.TRANSFER -> stringResource(R.string.add_mode_transfer)
                            },
                        )
                    }
                }
            }

            if (isTransfer) {
                // Разворот счетов: обе строки сдвигаются на расстояние до места
                // напарника, пересекая линию с кнопкой; по завершении состояния
                // счетов меняются, а смещения сбрасываются мгновенно. Анимация
                // 280 мс и отключается системной настройкой «убрать анимации».
                var swapRunning by remember { mutableStateOf(false) }
                var fromRowHeightPx by remember { mutableIntStateOf(0) }
                var dividerRowHeightPx by remember { mutableIntStateOf(0) }
                var toRowHeightPx by remember { mutableIntStateOf(0) }
                val swapProgress = remember { Animatable(0f) }
                val context = LocalContext.current
                val haptics = LocalHapticFeedback.current
                // Отступы FormCard сверху и снизу линии разворота (verticalSpacing = 2.dp).
                val swapRowGapPx = LocalDensity.current.run { 2.dp.toPx() }
                FormCard(verticalSpacing = 2.dp) {
                    DateTimeRow(
                        date = date,
                        time = time,
                        onDateClick = { showDatePicker = true },
                        onTimeClick = { showTimePicker = true },
                    )
                    SelectorRow(
                        modifier = Modifier
                            .onSizeChanged { fromRowHeightPx = it.height }
                            .graphicsLayer {
                                translationY = swapProgress.value * (fromRowHeightPx + swapRowGapPx * 2 + dividerRowHeightPx)
                            },
                        label = stringResource(R.string.add_transfer_from),
                        value = fromAccount?.name,
                        subtitle = fromAccountSubtitle,
                        subtitleNegative = fromAccountBalanceNegative,
                        showCaptionWhenSelected = true,
                        compact = true,
                        icon = fromAccount?.let { account -> { CategoryIcon(account.iconKey, account.colorKey) } },
                        onClick = {
                            accountSheetForTo = false
                            showAccountSheet = true
                        },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.onSizeChanged { dividerRowHeightPx = it.height },
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                // Настройка «убрать анимации» выставляет масштаб аниматоров в ноль:
                                // тогда разворот применяем мгновенно.
                                val animationsOn = Settings.Global.getFloat(
                                    context.contentResolver,
                                    Settings.Global.ANIMATOR_DURATION_SCALE,
                                    1f,
                                ) > 0f
                                if (animationsOn) {
                                    swapRunning = true
                                    coroutineScope.launch {
                                        swapProgress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
                                        viewModel.swapTransferAccounts()
                                        swapProgress.snapTo(0f) // сброс смещения без анимации после смены мест
                                        swapRunning = false
                                    }
                                } else {
                                    viewModel.swapTransferAccounts()
                                }
                            },
                            enabled = !swapRunning,
                        ) {
                            Icon(
                                Icons.Filled.SwapVert,
                                contentDescription = stringResource(R.string.add_transfer_swap_cd),
                                modifier = Modifier.graphicsLayer { rotationZ = swapProgress.value * 180f },
                            )
                        }
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                    SelectorRow(
                        modifier = Modifier
                            .onSizeChanged { toRowHeightPx = it.height }
                            .graphicsLayer {
                                translationY = -swapProgress.value * (toRowHeightPx + swapRowGapPx * 2 + dividerRowHeightPx)
                            },
                        label = stringResource(R.string.add_transfer_to),
                        value = toAccount?.name,
                        subtitle = toAccountSubtitle,
                        subtitleNegative = toAccountBalanceNegative,
                        showCaptionWhenSelected = true,
                        compact = true,
                        icon = toAccount?.let { account -> { CategoryIcon(account.iconKey, account.colorKey) } },
                        onClick = {
                            accountSheetForTo = true
                            showAccountSheet = true
                        },
                    )
                    if (sameTransferAccount) {
                        Text(
                            text = stringResource(R.string.add_transfer_error_same_account),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // Карточка сумм и комментария: отправление и зачисление рядом,
                // между ними стрелка; курс — подпись, раскрывающаяся в поле.
                FormCard {
                    if (transferCurrencies == null) {
                        // Одинаковые валюты: курс и зачисление не показываются.
                        AmountField(
                            value = fromAmountText,
                            onValueChange = viewModel::onFromAmountChange,
                            // %1$s — символ валюты.
                            label = stringResource(R.string.add_transfer_amount_from, fromSymbol),
                            onCalculate = {
                                calculatorTarget = CALCULATOR_FROM
                                showCalculator = true
                            },
                            modifier = Modifier.bringIntoViewOnFocus(amountBringIntoView, coroutineScope),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { if (feeVisible) feeFocus.requestFocus() else noteFocus.requestFocus() },
                            ),
                        )
                    } else {
                        // Подпись раскрывается в поле с фокусом, чтобы сразу править курс.
                        LaunchedEffect(rateFieldVisible) {
                            if (rateFieldVisible) rateFocus.requestFocus()
                        }
                        // Курс пары разных валют: слева крупная валюта пары,
                        // направление разворачивается кнопкой рядом.
                        val rateCodes = if (rateInverted) {
                            transferCurrencies.second to transferCurrencies.first
                        } else {
                            transferCurrencies.first to transferCurrencies.second
                        }
                        val parsedRate = TransferCalc.parse(rateText)
                        val rateToggleEnabled = parsedRate != null && parsedRate.signum() > 0
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AmountField(
                                value = fromAmountText,
                                onValueChange = viewModel::onFromAmountChange,
                                // %1$s — символ валюты.
                                label = stringResource(R.string.add_transfer_amount_from, fromSymbol),
                                onCalculate = {
                                    calculatorTarget = CALCULATOR_FROM
                                    showCalculator = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .bringIntoViewOnFocus(amountBringIntoView, coroutineScope),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = {
                                        if (rateFieldVisible) rateFocus.requestFocus() else receiveFocus.requestFocus()
                                    },
                                ),
                            )
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            // Нога, ещё не дошедшая по синку, правится только по своей стороне
                            // суммы, поэтому поле зачисления заблокировано до её появления.
                            AmountField(
                                value = toAmountText,
                                onValueChange = viewModel::onToAmountChange,
                                // %1$s — символ валюты.
                                label = stringResource(R.string.add_transfer_amount_to, toSymbol),
                                onCalculate = {
                                    calculatorTarget = CALCULATOR_TO
                                    showCalculator = true
                                },
                                enabled = viewModel.isTransferInLegPresent,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(receiveFocus)
                                    .bringIntoViewOnFocus(receiveBringIntoView, coroutineScope),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = { if (feeVisible) feeFocus.requestFocus() else noteFocus.requestFocus() },
                                ),
                            )
                        }
                        if (rateFieldVisible) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MoneyTextField(
                                    value = rateText,
                                    onValueChange = viewModel::onRateChange,
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(rateFocus)
                                        .bringIntoViewOnFocus(rateBringIntoView, coroutineScope),
                                    colors = fieldColors(),
                                    isError = rateErrorVisible,
                                    supportingText = if (rateErrorVisible) {
                                        { Text(stringResource(R.string.add_transfer_error_rate)) }
                                    } else {
                                        null
                                    },
                                    label = {
                                        Text(
                                            if (parsedRate != null) {
                                                stringResource(
                                                    R.string.add_transfer_rate_label_known,
                                                    rateCodes.first,
                                                    rateCodes.second,
                                                    rateText,
                                                )
                                            } else {
                                                stringResource(
                                                    R.string.add_transfer_rate_label,
                                                    rateCodes.first,
                                                    rateCodes.second,
                                                )
                                            },
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(
                                        onNext = { if (feeVisible) feeFocus.requestFocus() else noteFocus.requestFocus() },
                                    ),
                                )
                                RateDirectionToggle(
                                    enabled = rateToggleEnabled,
                                    onClick = viewModel::toggleRateDirection,
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                // Паддинг 16dp: начало подписи совпадает с отступом
                                // текста полей выше (по умолчанию у кнопки-текста 12dp).
                                TextButton(
                                    onClick = { rateFieldVisible = true },
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        if (parsedRate != null) {
                                            stringResource(
                                                R.string.add_transfer_rate_caption,
                                                rateCodes.first,
                                                rateText,
                                                rateCodes.second,
                                            )
                                        } else {
                                            stringResource(
                                                R.string.add_transfer_rate_caption_unknown,
                                                rateCodes.first,
                                                rateCodes.second,
                                            )
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                RateDirectionToggle(
                                    enabled = rateToggleEnabled,
                                    onClick = viewModel::toggleRateDirection,
                                )
                            }
                        }
                    }
                    if (feeVisible) {
                        MoneyTextField(
                            value = feeText,
                            onValueChange = viewModel::onFeeChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(feeFocus)
                                .bringIntoViewOnFocus(feeBringIntoView, coroutineScope),
                            // %1$s — символ валюты списания.
                            label = { Text(stringResource(R.string.add_transfer_fee, fromSymbol)) },
                            colors = fieldColors(),
                            isError = feeErrorVisible,
                            supportingText = if (feeErrorVisible) {
                                { Text(stringResource(R.string.add_transfer_error_fee)) }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { noteFocus.requestFocus() }),
                        )
                    } else {
                        // Паддинг 16dp: начало подписи совпадает с отступом
                        // текста полей выше (по умолчанию у кнопки-текста 12dp).
                        TextButton(
                            onClick = viewModel::showFee,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            Text(stringResource(R.string.add_transfer_add_fee))
                        }
                    }
                    NoteField(
                        value = noteFieldValue,
                        onValueChange = { newValue ->
                            noteFieldValue = newValue
                            viewModel.onNoteChange(newValue.text)
                            showNoteSuggestions = true
                            coroutineScope.launch {
                                noteBringIntoViewRequester.bringIntoView()
                            }
                        },
                        suggestions = noteSuggestions,
                        showSuggestions = showNoteSuggestions,
                        onSuggestionClick = { suggestion ->
                            viewModel.onNoteChange(suggestion)
                            showNoteSuggestions = false
                        },
                        onDismissSuggestions = { showNoteSuggestions = false },
                        bringIntoViewRequester = noteBringIntoViewRequester,
                        focusRequester = noteFocus,
                    )
                }
            } else {
                // Вкладки расхода и дохода.
                FormCard {
                    DateTimeRow(
                        date = date,
                        time = time,
                        onDateClick = { showDatePicker = true },
                        onTimeClick = { showTimePicker = true },
                    )
                    SelectorRow(
                        label = stringResource(R.string.common_account),
                        value = selectedAccount?.name,
                        subtitle = selectedAccountSubtitle,
                        subtitleNegative = selectedAccountBalanceNegative,
                        icon = selectedAccount?.let { account -> { CategoryIcon(account.iconKey, account.colorKey) } },
                        onClick = {
                            accountSheetForTo = false
                            showAccountSheet = true
                        },
                    )
                    SelectorRow(
                        label = stringResource(R.string.add_category),
                        value = selectedCategory?.name,
                        icon = selectedCategory?.let { category -> { CategoryIcon(category.iconKey, category.colorKey) } },
                        onClick = { viewModel.setCategorySheetVisible(true) },
                    )
                }

                FormCard {
                    AmountField(
                        value = amountText,
                        onValueChange = viewModel::onAmountChange,
                        // %1$s — символ валюты.
                        label = stringResource(R.string.add_amount_label, amountSymbol),
                        onCalculate = {
                            calculatorTarget = CALCULATOR_FROM
                            showCalculator = true
                        },
                        modifier = Modifier.bringIntoViewOnFocus(amountBringIntoView, coroutineScope),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { noteFocus.requestFocus() }),
                    )
                    NoteField(
                        value = noteFieldValue,
                        onValueChange = { newValue ->
                            noteFieldValue = newValue
                            viewModel.onNoteChange(newValue.text)
                            showNoteSuggestions = true
                            coroutineScope.launch {
                                noteBringIntoViewRequester.bringIntoView()
                            }
                        },
                        suggestions = noteSuggestions,
                        showSuggestions = showNoteSuggestions,
                        onSuggestionClick = { suggestion ->
                            viewModel.onNoteChange(suggestion)
                            showNoteSuggestions = false
                        },
                        onDismissSuggestions = { showNoteSuggestions = false },
                        bringIntoViewRequester = noteBringIntoViewRequester,
                        focusRequester = noteFocus,
                    )
                }
            }
        }
    }
}

/** Карточка формы: общая рамка и внутренний отступ 16dp; карточки между собой разносит внешний Column. */
@Composable
private fun FormCard(
    verticalSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            content()
        }
    }
}

/**
 * Строка выбора (счёт, категория, нога перевода) в карточках формы: пока
 * ничего не выбрано — подпись поля и подсказка «Выбрать…», когда выбрано —
 * значение и его деталь. Круглая иконка появляется, только когда выбрано;
 * caption-флаг показывает подпись поля и над выбранным значением.
 * compact — плотный вариант ног перевода: без минимума 64dp и с меньшими
 * вертикальными паддингами.
 */
@Composable
private fun SelectorRow(
    modifier: Modifier = Modifier,
    label: String,
    value: String?,
    subtitle: String? = null,
    subtitleNegative: Boolean = false,
    showCaptionWhenSelected: Boolean = false,
    compact: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (compact) {
                    Modifier.padding(vertical = 4.dp)
                } else {
                    Modifier.heightIn(min = 64.dp).padding(vertical = 8.dp)
                },
            )
            .clickable(onClick = onClick),
    ) {
        if (icon != null) {
            icon()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (icon != null) 12.dp else 0.dp),
        ) {
            if (value == null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.common_select),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (showCaptionWhenSelected) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (subtitleNegative) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}

/**
 * Поле суммы с калькулятором: одинаковая высота и оформление на всех трёх
 * вкладках. Параметры клавиатуры пробрасываются для IME-цепочки полей.
 */
@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onCalculate: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    MoneyTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        colors = fieldColors(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = {
            IconButton(onClick = onCalculate) {
                Icon(Icons.Filled.Calculate, contentDescription = stringResource(R.string.calc_title))
            }
        },
    )
}

/** Кнопка разворота направления показа курса: «1 USD = 25 000 VND» ↔ «1 VND = …». */
@Composable
private fun RateDirectionToggle(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            Icons.Filled.SwapHoriz,
            contentDescription = stringResource(R.string.add_transfer_rate_direction_cd),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Фокус: поле дотягивается выше клавиатуры — панель сохранения с imePadding
 * поджимает контент, а перемещение запроса доводит его до видимой зоны.
 */
private fun Modifier.bringIntoViewOnFocus(
    requester: BringIntoViewRequester,
    scope: CoroutineScope,
): Modifier = this
    .onFocusChanged { state ->
        if (state.isFocused) scope.launch { requester.bringIntoView() }
    }
    .bringIntoViewRequester(requester)

/** Дата и время операции: два центральных кликабельных столбца, общий для всех вкладок. */
@Composable
private fun DateTimeRow(
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

/** Комментарий с подсказками из истории; вынесен, потому что показывается в обоих режимах. */
@Composable
private fun NoteField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    suggestions: List<String>,
    showSuggestions: Boolean,
    onSuggestionClick: (String) -> Unit,
    onDismissSuggestions: () -> Unit,
    bringIntoViewRequester: BringIntoViewRequester,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val scope = rememberCoroutineScope()
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) scope.launch { bringIntoViewRequester.bringIntoView() }
                },
            label = { Text(stringResource(R.string.add_note_label)) },
            colors = fieldColors(),
            // Последнее поле цепочки: Done закрывает клавиатуру.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        DropdownMenu(
            expanded = showSuggestions && suggestions.isNotEmpty(),
            onDismissRequest = onDismissSuggestions,
            properties = PopupProperties(focusable = false),
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = { onSuggestionClick(suggestion) },
                )
            }
        }
    }
}

/** Выбранная вкладка — залитая и контрастная вместо тёмно-синего контейнера на почти чёрном фоне. */
@Composable
private fun selectedTabColors(): SegmentedButtonColors = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.secondary,
    activeContentColor = MaterialTheme.colorScheme.onSecondary,
    activeBorderColor = MaterialTheme.colorScheme.secondary,
)

/** Рамка и подпись-плейсхолдер полей: в тёмной теме заметнее стандартных. */
@Composable
private fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = if (isSystemInDarkTheme()) DarkFieldBorder else MaterialTheme.colorScheme.outline,
    unfocusedLabelColor = if (isSystemInDarkTheme()) DarkFieldLabel else MaterialTheme.colorScheme.onSurfaceVariant,
)
