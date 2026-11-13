package com.expeysi.app.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.ui.common.DateRangePickerDialog
import com.expeysi.app.ui.common.asString
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // Файл, выбранный в системном диалоге и открытый на запись; null — не открыт.
    var savedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val shareLabel = stringResource(R.string.export_share)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            savedUri = uri
            // Ошибка открытия потока → null → «Не удалось открыть файл» без исключения.
            val output = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
            viewModel.exportTo(output)
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message.asString(context),
            actionLabel = shareLabel.takeIf { state.exportSucceeded && savedUri != null },
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            savedUri?.let { shareCsv(context, it) }
        }
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.drawer_data_export),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(vertical = 8.dp),
            ) {
                if (state.inProgress) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                Button(
                    onClick = { exportLauncher.launch(suggestedFileName()) },
                    enabled = (state.includeIncome || state.includeExpense) && !state.inProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.export_button))
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.export_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.export_section_types),
                    style = MaterialTheme.typography.titleSmall,
                )
                TypeCard {
                    TypeRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        label = stringResource(R.string.export_type_income, state.incomeCount),
                        checked = state.includeIncome,
                        onCheckedChange = viewModel::onIncomeChange,
                    )
                    TypeRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        label = stringResource(R.string.export_type_expense, state.expenseCount),
                        checked = state.includeExpense,
                        onCheckedChange = viewModel::onExpenseChange,
                    )
                }
                Text(
                    text = stringResource(R.string.export_opening_balance_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AdvancedSection(state = state, viewModel = viewModel)
        }
    }
}

/** Группа карточкой с обводкой — как поля даты, счёта и категории в операции. */
@Composable
private fun TypeCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp), content = content)
    }
}

@Composable
private fun AdvancedSection(state: ExportUiState, viewModel: ExportViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.export_advanced),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            // Стрелка декоративная: смысл разворачивания несёт подпись строки.
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            AdvancedCard(state = state, viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AdvancedCard(state: ExportUiState, viewModel: ExportViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DateRangeRow(
                from = state.dateFrom,
                to = state.dateTo,
                onPick = viewModel::onDateRangeChange,
                onClear = viewModel::onDateRangeClear,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.export_accounts),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.accounts.forEach { account ->
                        FilterChip(
                            selected = account.id in state.selectedAccountIds,
                            onClick = { viewModel.onAccountToggle(account.id, account.id !in state.selectedAccountIds) },
                            label = { Text(account.name, style = MaterialTheme.typography.labelLarge) },
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.export_delimiter),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.delimiter == ',',
                        onClick = { viewModel.onDelimiterChange(',') },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text(stringResource(R.string.export_delimiter_comma))
                    }
                    SegmentedButton(
                        selected = state.delimiter == ';',
                        onClick = { viewModel.onDelimiterChange(';') },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text(stringResource(R.string.export_delimiter_semicolon))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TypeRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                label = stringResource(R.string.export_bom),
                checked = state.writeBom,
                onCheckedChange = viewModel::onBomChange,
            )
        }
    }
}

/**
 * Строка-чекбокс: кликабельна вся строка, минимум касания 48dp,
 * зазор между чекбоксом и подписью — 12dp.
 */
@Composable
private fun TypeRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Строка выбора диапазона дат: подпись поля, значение снизу — как выбор счёта в операции. */
@Composable
private fun DateRangeRow(
    from: LocalDate?,
    to: LocalDate?,
    onPick: (LocalDate, LocalDate) -> Unit,
    onClear: () -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val selected = from != null && to != null

    ListItem(
        headlineContent = { Text(stringResource(R.string.export_date_range)) },
        supportingContent = {
            Text(
                text = if (selected) {
                    exportRangeLabel(from!!, to!!)
                } else {
                    stringResource(R.string.export_date_not_set)
                },
            )
        },
        trailingContent = {
            if (selected) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.export_date_clear_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null)
            }
        },
        modifier = Modifier.clickable { showPicker = true },
    )
    if (showPicker) {
        DateRangePickerDialog(
            initialStart = from,
            initialEnd = to,
            onConfirm = onPick,
            onDismiss = { showPicker = false },
        )
    }
}

/** Поделиться выгруженным файлом; доступ на чтение SAF-URI у приложения уже есть. */
private fun shareCsv(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** Имя файла по умолчанию: expeysi-2026-11-12.csv. */
private fun suggestedFileName(): String = "expeysi-" + LocalDate.now().format(FILE_DATE_FORMAT) + ".csv"

private val FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** Метка диапазона: «01.01.26–05.02.26», как у шторки периода. */
private fun exportRangeLabel(from: LocalDate, to: LocalDate): String =
    from.format(RANGE_DAY_FORMAT) + "–" + to.format(RANGE_DAY_FORMAT)

private val RANGE_DAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yy")
