package com.expeysi.app.ui.balances

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.data.db.CurrencyEntity
import com.expeysi.app.ui.common.CurrencyLabels
import com.expeysi.app.ui.common.EntityPreview
import com.expeysi.app.ui.common.SaveButton
import com.expeysi.app.ui.icons.BalanceIcon
import com.expeysi.app.ui.icons.CategoryPalette
import com.expeysi.app.ui.icons.ColorGrid
import com.expeysi.app.ui.icons.IconGrid
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBalanceScreen(
    onClose: () -> Unit,
    viewModel: EditBalanceViewModel = hiltViewModel(),
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()
    val currencies by viewModel.currencies.collectAsStateWithLifecycle()
    val usedCurrencyCodes by viewModel.usedCurrencyCodes.collectAsStateWithLifecycle()
    val recentCurrencyCodes by viewModel.recentCurrencyCodes.collectAsStateWithLifecycle()
    val currencyQuery by viewModel.currencyQuery.collectAsStateWithLifecycle()
    val showAllCurrencies by viewModel.showAllCurrencies.collectAsStateWithLifecycle()
    val iconKey by viewModel.iconKey.collectAsStateWithLifecycle()
    val colorKey by viewModel.colorKey.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCurrencyPicker by rememberSaveable { mutableStateOf(false) }
    // Ошибка пустого названия: после потери фокуса или тапа на неактивную кнопку.
    var nameHadFocus by rememberSaveable { mutableStateOf(false) }
    var showNameError by rememberSaveable { mutableStateOf(false) }
    val nameError = showNameError && name.isBlank()

    LaunchedEffect(saved) {
        if (saved) onClose()
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.balance_delete_title)) },
            text = { Text(stringResource(R.string.balance_delete_text)) },
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

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            currencies = currencies,
            selectedCode = currencyCode,
            usedCodes = usedCurrencyCodes,
            recentCodes = recentCurrencyCodes,
            query = currencyQuery,
            showAll = showAllCurrencies,
            onQueryChange = viewModel::onCurrencyQueryChange,
            onShowAll = viewModel::onShowAllCurrencies,
            onSelect = { currency ->
                viewModel.onCurrencySelect(currency.code)
                viewModel.resetCurrencyPicker()
                showCurrencyPicker = false
            },
            onDismiss = {
                viewModel.resetCurrencyPicker()
                showCurrencyPicker = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isEdit) R.string.balance_title_edit else R.string.balance_title_new)) },
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
            SaveButton(
                enabled = canSave,
                onClick = viewModel::save,
                onDisabledClick = { showNameError = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            EntityPreview(
                name = name,
                subtitle = currencyCode,
            ) {
                BalanceIcon(name = name, iconKey = iconKey, colorKey = colorKey, size = 48.dp)
            }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    viewModel.onNameChange(it)
                    if (it.isNotBlank()) showNameError = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            nameHadFocus = true
                        } else if (nameHadFocus && name.isBlank()) {
                            showNameError = true
                        }
                    },
                label = { Text(stringResource(R.string.common_name)) },
                isError = nameError,
                supportingText = { if (nameError) Text(stringResource(R.string.common_name_error)) },
                singleLine = true,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.balance_currency), style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = { showCurrencyPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val current = currencies.firstOrNull { it.code == currencyCode }
                    val title = current
                        ?.let { CurrencyLabels.title(it.code) }
                        ?: currencyCode
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.common_icon), style = MaterialTheme.typography.titleSmall)
                IconGrid(
                    iconKeys = viewModel.availableIconKeys,
                    selectedColor = CategoryPalette.color(colorKey),
                    selectedKey = iconKey,
                    onSelect = viewModel::onIconSelect,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.common_color), style = MaterialTheme.typography.titleSmall)
                ColorGrid(
                    colorKeys = CategoryPalette.availableKeys,
                    selectedKey = colorKey,
                    onSelect = viewModel::onColorSelect,
                )
            }
        }
    }
}

/** Диалог выбора валюты: секции, поиск и кнопка «Все валюты». */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CurrencyPickerDialog(
    currencies: List<CurrencyEntity>,
    selectedCode: String,
    usedCodes: List<String>,
    recentCodes: List<String>,
    query: String,
    showAll: Boolean,
    onQueryChange: (String) -> Unit,
    onShowAll: () -> Unit,
    onSelect: (CurrencyEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val userLocaleCountry = Locale.getDefault().country
    val labels = remember(currencies) { currencies.associate { it.code to CurrencyLabels.title(it.code) } }
    val sections = remember(currencies, selectedCode, usedCodes, recentCodes, query, showAll, userLocaleCountry) {
        if (query.isBlank()) {
            buildPickerSections(
                all = currencies,
                selectedCode = selectedCode,
                usedCodes = usedCodes,
                recentCodes = recentCodes,
                userLocaleCode = userLocaleCountry,
                showAll = showAll,
            )
        } else {
            searchCurrencies(
                all = currencies,
                selectedCode = selectedCode,
                usedCodes = usedCodes,
                query = query,
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        // containerColor = surface: фоновый цвет sticky-заголовков секций
        // совпадает с панелью диалога, и строки списка не просвечивают.
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.balance_currency_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.common_search)) },
                    singleLine = true,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .padding(top = 8.dp),
                ) {
                    sections.forEach { section ->
                        if (section.kind != SectionKind.SEARCH) {
                            stickyHeader(key = "header-${section.kind}") {
                                SectionHeader(stringResource(sectionTitleRes(section.kind)))
                            }
                        }
                        items(section.items, key = { "${section.kind}-${it.code}" }) { currency ->
                            CurrencyRow(
                                currency = currency,
                                labels = labels,
                                selected = currency.code == selectedCode,
                                onSelect = onSelect,
                            )
                        }
                    }
                    if (query.isBlank() && !showAll) {
                        item(key = "show-all") {
                            OutlinedButton(
                                onClick = onShowAll,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.balance_currency_show_all))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** Ресурс заголовка секции пикера. */
private fun sectionTitleRes(kind: SectionKind): Int = when (kind) {
    SectionKind.CURRENT -> R.string.balance_currency_current
    SectionKind.RECENT -> R.string.balance_currency_recent
    SectionKind.USED -> R.string.balance_currency_used
    SectionKind.POPULAR -> R.string.balance_currency_popular
    SectionKind.REST -> R.string.balance_currency_rest
    // SEARCH рисуется без заголовка (гвардия в списке выше); значение не используется.
    SectionKind.SEARCH -> R.string.balance_currency_rest
}

/** Заголовок секции пикера: липкий, с непрозрачным фоном панели диалога. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
    )
}

/** Строка валюты: название крупно, ниже «КОД · символ» (без символа, если он равен коду). */
@Composable
private fun CurrencyRow(
    currency: CurrencyEntity,
    labels: Map<String, String>,
    selected: Boolean,
    onSelect: (CurrencyEntity) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = labels[currency.code].orEmpty().ifBlank { currency.code },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { Text(text = currencySubtitle(currency.symbol, currency.code)) },
        trailingContent = {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        modifier = Modifier.clickable { onSelect(currency) },
    )
}

private fun currencySubtitle(symbol: String, code: String): String =
    if (symbol.equals(code, ignoreCase = true)) code else "$code · $symbol"
