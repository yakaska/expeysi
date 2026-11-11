package com.expeysi.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.ui.MoneyTextField
import com.expeysi.app.ui.common.EntityPreview
import com.expeysi.app.ui.common.SaveButton
import com.expeysi.app.ui.icons.CategoryIcon
import com.expeysi.app.ui.icons.CategoryPalette
import com.expeysi.app.ui.icons.ColorGrid
import com.expeysi.app.ui.icons.IconGrid

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditAccountScreen(
    onClose: () -> Unit,
    viewModel: EditAccountViewModel = hiltViewModel(),
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val iconKey by viewModel.iconKey.collectAsStateWithLifecycle()
    val colorKey by viewModel.colorKey.collectAsStateWithLifecycle()
    val openingBalanceText by viewModel.openingBalanceText.collectAsStateWithLifecycle()
    val balanceId by viewModel.balanceId.collectAsStateWithLifecycle()
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf(false) }
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
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = { Text(stringResource(R.string.account_delete_text)) },
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isEdit) R.string.account_title_edit else R.string.account_title_new)) },
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
                // Сохранение недоступно и по другим причинам (некорректная сумма);
                // подсказка про название — только при пустом названии.
                onDisabledClick = { if (name.isBlank()) showNameError = true },
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
            EntityPreview(name = name) {
                CategoryIcon(iconKey = iconKey, colorKey = colorKey, size = 48.dp)
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
                Text(stringResource(R.string.common_balance), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    balances.forEach { balance ->
                        FilterChip(
                            selected = balanceId == balance.id,
                            onClick = { viewModel.onBalanceSelect(balance.id) },
                            label = { Text(balance.name) },
                        )
                    }
                }
            }
            MoneyTextField(
                value = openingBalanceText,
                onValueChange = viewModel::onOpeningBalanceChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.account_opening_balance)) },
            )
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
