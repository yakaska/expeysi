package com.expeysi.app.ui.balances

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBalanceScreen(
    onClose: () -> Unit,
    viewModel: EditBalanceViewModel = hiltViewModel(),
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()
    val currencies by viewModel.currencies.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var currencyQuery by remember { mutableStateOf("") }

    LaunchedEffect(saved) {
        if (saved) onClose()
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить баланс?") },
            text = { Text("Баланс, все его счета и операции будут удалены. Это действие нельзя отменить.") },
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

    if (showCurrencyPicker) {
        val filtered = currencies.filter {
            currencyQuery.isBlank() ||
                it.code.contains(currencyQuery, ignoreCase = true) ||
                it.name.contains(currencyQuery, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = {
                showCurrencyPicker = false
                currencyQuery = ""
            },
            title = { Text("Валюта баланса") },
            text = {
                Column {
                    OutlinedTextField(
                        value = currencyQuery,
                        onValueChange = { currencyQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Поиск") },
                        singleLine = true,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .padding(top = 8.dp),
                    ) {
                        items(filtered, key = { it.code }) { currency ->
                            ListItem(
                                headlineContent = { Text("${currency.code} — ${currency.name}") },
                                supportingContent = { Text("Символ: ${currency.symbol}") },
                                modifier = Modifier.clickable {
                                    viewModel.onCurrencySelect(currency.code)
                                    showCurrencyPicker = false
                                    currencyQuery = ""
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCurrencyPicker = false
                        currencyQuery = ""
                    },
                ) { Text("Отмена") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEdit) "Изменить баланс" else "Новый баланс") },
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
            OutlinedTextField(
                value = name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название") },
                singleLine = true,
            )

            Text("Валюта", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { showCurrencyPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val current = currencies.firstOrNull { it.code == currencyCode }
                Text(if (current != null) "${current.code} — ${current.name}" else currencyCode)
            }
        }
    }
}
