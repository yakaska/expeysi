package com.expeysi.app.ui.debts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.data.db.CounterpartyEntity
import com.expeysi.app.domain.CounterpartyType
import com.expeysi.app.domain.DebtCalculator
import com.expeysi.app.ui.common.AmountFormat
import com.expeysi.app.ui.theme.ExpeysiTheme
import com.expeysi.app.ui.theme.FinanceColors
import com.expeysi.app.ui.theme.LocalFinanceColors
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtsScreen(
    onBack: () -> Unit,
    onCounterpartyClick: (String) -> Unit,
    onEditCounterpartyClick: (String) -> Unit,
    onAddOperationClick: () -> Unit,
    viewModel: DebtsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DebtsContent(
        state = state,
        onBack = onBack,
        onCounterpartyClick = onCounterpartyClick,
        onEditCounterpartyClick = onEditCounterpartyClick,
        onAddOperationClick = onAddOperationClick,
        onSelectSort = viewModel::selectSort,
        onSelectSideFilter = viewModel::selectSideFilter,
    )
}

/** Название варианта сортировки в меню. */
private fun DebtsSort.labelRes(): Int = when (this) {
    DebtsSort.NAME -> R.string.debts_sort_name
    DebtsSort.BALANCE -> R.string.debts_sort_balance
    DebtsSort.OVERDUE -> R.string.debts_sort_overdue
}

/** Содержимое экрана без ViewModel: то же рисуется в превью. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtsContent(
    state: DebtsUiState,
    onBack: () -> Unit,
    onCounterpartyClick: (String) -> Unit,
    onEditCounterpartyClick: (String) -> Unit,
    onAddOperationClick: () -> Unit,
    onSelectSort: (DebtsSort) -> Unit,
    onSelectSideFilter: (DebtsSideFilter) -> Unit,
) {
    val financeColors = LocalFinanceColors.current
    var sortMenuOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.debts_sort_cd),
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                    ) {
                        DebtsSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes())) },
                                onClick = {
                                    onSelectSort(option)
                                    sortMenuOpen = false
                                },
                                trailingIcon = {
                                    if (option == state.sort) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddOperationClick) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.debts_add_cd),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SummaryCards(state = state, financeColors = financeColors)

            FilterChipsRow(
                selected = state.sideFilter,
                onSelect = onSelectSideFilter,
            )

            state.rows.forEach { row ->
                DebtsRowCard(
                    row = row,
                    currencySymbols = state.currencySymbols,
                    financeColors = financeColors,
                    muted = false,
                    onClick = { onCounterpartyClick(row.counterparty.id) },
                    onEditClick = { onEditCounterpartyClick(row.counterparty.id) },
                )
            }

            if (state.settled.isNotEmpty()) {
                SettledSection(
                    state = state,
                    financeColors = financeColors,
                    onCounterpartyClick = onCounterpartyClick,
                    onEditCounterpartyClick = onEditCounterpartyClick,
                )
            }

            if (state.archived.isNotEmpty()) {
                Text(
                    stringResource(R.string.debts_section_archive),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                state.archived.forEach { row ->
                    DebtsRowCard(
                        row = row,
                        currencySymbols = state.currencySymbols,
                        financeColors = financeColors,
                        muted = true,
                        onClick = { onCounterpartyClick(row.counterparty.id) },
                        onEditClick = { onEditCounterpartyClick(row.counterparty.id) },
                    )
                }
            }

            if (state.rows.isEmpty() && state.settled.isEmpty() && state.archived.isEmpty()) {
                EmptyDebtsState()
            }
        }
    }
}

/** Итоговые карточки: одна широкая «Расчёты закрыты» или две карточки сторон. */
@Composable
private fun SummaryCards(
    state: DebtsUiState,
    financeColors: FinanceColors,
) {
    val bothSettled = state.totals.owedToMe.isEmpty() && state.totals.owedByMe.isEmpty()
    if (bothSettled) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Text(
                text = stringResource(R.string.debts_row_settled),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TotalCard(
                    label = stringResource(R.string.debts_total_owed_to_me),
                    totals = state.totals.owedToMe,
                    currencySymbols = state.currencySymbols,
                    amountColor = financeColors.income,
                    modifier = Modifier.weight(1f),
                )
                TotalCard(
                    label = stringResource(R.string.debts_total_owed_by_me),
                    totals = state.totals.owedByMe,
                    currencySymbols = state.currencySymbols,
                    amountColor = financeColors.expense,
                    modifier = Modifier.weight(1f),
                )
            }
            // Итоги считают закрытых и архивных тоже: секции их строк скрывают,
            // но сам долг никуда не исчезает — сводка показывает его целиком.
            Text(
                text = stringResource(R.string.debts_totals_include_archived),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Фильтр строк по стороне долга: «все / должны мне / должен я»; открытые строки. */
@Composable
private fun FilterChipsRow(
    selected: DebtsSideFilter,
    onSelect: (DebtsSideFilter) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth(),
    ) {
        DebtsSideFilter.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        when (option) {
                            DebtsSideFilter.ALL -> stringResource(R.string.common_all)
                            DebtsSideFilter.OWED_TO_ME -> stringResource(R.string.debts_total_owed_to_me)
                            DebtsSideFilter.OWED_BY_ME -> stringResource(R.string.debts_total_owed_by_me)
                        },
                    )
                },
            )
        }
    }
}

/** Карточка сводки: «Должны мне» / «Должен я» со строками по валютам; нулевая — «—». */
@Composable
private fun TotalCard(
    label: String,
    totals: Map<String, Long>,
    currencySymbols: Map<String, String>,
    amountColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (totals.isEmpty()) {
                Text(
                    text = stringResource(R.string.debts_total_none),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                totals.forEach { (code, minor) ->
                    Text(
                        text = AmountFormat.withCurrency(minor, currencySymbols[code] ?: code),
                        style = MaterialTheme.typography.titleMedium,
                        color = amountColor,
                    )
                }
            }
        }
    }
}

/** Свёрнутая секция «Закрытые»: контрагенты с нулём во всех валютах. */
@Composable
private fun SettledSection(
    state: DebtsUiState,
    financeColors: FinanceColors,
    onCounterpartyClick: (String) -> Unit,
    onEditCounterpartyClick: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "settledChevron",
    )
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.debts_section_settled, state.settled.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.analysis_collapse else R.string.history_expand_cd,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.settled.forEach { row ->
                    DebtsRowCard(
                        row = row,
                        currencySymbols = state.currencySymbols,
                        financeColors = financeColors,
                        muted = false,
                        onClick = { onCounterpartyClick(row.counterparty.id) },
                        onEditClick = { onEditCounterpartyClick(row.counterparty.id) },
                    )
                }
            }
        }
    }
}

/** Строка контрагента: аватар с инициалами, имя и непустые остатки по валютам. */
@Composable
private fun DebtsRowCard(
    row: DebtsRow,
    currencySymbols: Map<String, String>,
    financeColors: FinanceColors,
    muted: Boolean,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
) {
    val display = debtsRowDisplay(row.balances)
    val toneColor = when (display.tone) {
        DebtsTone.POSITIVE -> financeColors.income
        DebtsTone.NEGATIVE -> financeColors.expense
        DebtsTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Архивные строки остаются серыми: секция уже сообщает, что контрагент в архиве.
    val color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else toneColor
    Card(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            CounterpartyAvatar(
                name = row.counterparty.name,
                type = CounterpartyType.fromWire(row.counterparty.type),
                tone = if (muted) DebtsTone.NEUTRAL else display.tone,
                color = color,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.counterparty.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    // Просрочен срок возврата при открытом долге: пометка
                    // рядом с именем, только в открытых строках — архивная
                    // секция и так приглушена.
                    if (row.overdue && !muted) {
                        Text(
                            text = stringResource(R.string.debts_overdue),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Text(
                    text = rowSubtitle(display = display, currencySymbols = currencySymbols),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (display.settled) MaterialTheme.colorScheme.onSurfaceVariant else color,
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.common_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Подпись строки: «вам должны/вы должны» через «·» или одна строка о закрытии. */
@Composable
private fun rowSubtitle(
    display: DebtsRowDisplay,
    currencySymbols: Map<String, String>,
): String = if (display.settled) {
    stringResource(R.string.debts_row_settled)
} else {
    display.parts
        .map { part ->
            stringResource(
                part.textRes,
                AmountFormat.withCurrency(part.amountMinor, currencySymbols[part.currencyCode] ?: part.currencyCode),
            )
        }
        .joinToString(separator = " · ")
}

/**
 * Круглый аватар с инициалами; два малого бейджа: тип контрагента сверху
 * (физлицо или компания) и направление долга снизу — стрелка цвета состояния.
 */
@Composable
private fun CounterpartyAvatar(
    name: String,
    type: CounterpartyType,
    tone: DebtsTone,
    color: Color,
) {
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = counterpartyInitials(name),
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
        }
        // Значок типа: не у всех контрагентов очевидно, компания это
        // или человек — подсказывает до открытия карточки.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when (type) {
                    CounterpartyType.PERSON -> Icons.Filled.Person
                    CounterpartyType.COMPANY -> Icons.Filled.Apartment
                },
                contentDescription = stringResource(
                    when (type) {
                        CounterpartyType.PERSON -> R.string.counterparty_type_person
                        CounterpartyType.COMPANY -> R.string.counterparty_type_company
                    },
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
        }
        when (tone) {
            // Стрелка вниз — деньги придут (должны мне), вверх — уйдут (должен я).
            DebtsTone.POSITIVE -> DirectionBadge(
                Icons.Filled.ArrowDownward,
                color,
                contentDescription = stringResource(R.string.debts_direction_in),
            )
            DebtsTone.NEGATIVE -> DirectionBadge(
                Icons.Filled.ArrowUpward,
                color,
                contentDescription = stringResource(R.string.debts_direction_out),
            )
            DebtsTone.NEUTRAL -> Unit
        }
    }
}

/** Малый бейдж направления в углу аватара: стрелка цвета состояния долга. */
@Composable
private fun BoxScope.DirectionBadge(
    icon: ImageVector,
    color: Color,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(18.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Пустой список долгов: иконка, заголовок и подсказка по центру. */
@Composable
private fun EmptyDebtsState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp)
            .widthIn(max = 320.dp),
    ) {
        Icon(
            Icons.Filled.Handshake,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.debts_empty_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.debts_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/* ----------------------------------- Previews ----------------------------------- */

private val PreviewSymbols = mapOf("RUB" to "₽", "VND" to "₫")

private fun previewCounterparty(id: String, name: String): CounterpartyEntity = CounterpartyEntity(id = id, name = name, type = "person", updatedAt = 0L)

private fun previewRow(
    id: String,
    name: String,
    vararg balances: Pair<String, Long>,
    overdue: Boolean = false,
): DebtsRow = DebtsRow(
    counterparty = previewCounterparty(id, name),
    balances = balances.map { (code, minor) -> DebtCalculator.Balance(id, code, minor) },
    overdue = overdue,
)

/** Состояние из трех секций строк; итоги считаются по всем, как в модели. */
private fun previewState(
    open: List<DebtsRow> = emptyList(),
    settled: List<DebtsRow> = emptyList(),
    archived: List<DebtsRow> = emptyList(),
): DebtsUiState {
    val all = open + settled + archived
    return DebtsUiState(
        rows = open,
        settled = settled,
        archived = archived,
        totals = DebtsTotals(
            owedToMe = all.asSequence()
                .flatMap { it.balances }
                .filter { it.balanceMinor > 0 }
                .groupBy { it.currencyCode }
                .mapValues { (_, list) -> list.sumOf { it.balanceMinor } },
            owedByMe = all.asSequence()
                .flatMap { it.balances }
                .filter { it.balanceMinor < 0 }
                .groupBy { it.currencyCode }
                .mapValues { (_, list) -> list.sumOf { abs(it.balanceMinor) } },
        ),
        currencySymbols = PreviewSymbols,
    )
}

@Composable
private fun PreviewDebtsContent(state: DebtsUiState) {
    DebtsContent(
        state = state,
        onBack = {},
        onCounterpartyClick = {},
        onEditCounterpartyClick = {},
        onAddOperationClick = {},
        onSelectSort = {},
        onSelectSideFilter = {},
    )
}

@Preview(name = "Смешанные остатки / light", showBackground = true)
@Composable
private fun PreviewMixedBalances() {
    val state = previewState(
        open = listOf(
            previewRow("1", "Аня Петрова", "RUB" to 100_000_00, "VND" to 500_000_00),
            previewRow("2", "ООО Ромашка", "RUB" to -30_000_00, overdue = true),
        ),
        settled = listOf(previewRow("3", "Борис Смирнов", "RUB" to 0)),
    )
    ExpeysiTheme { PreviewDebtsContent(state) }
}

@Preview(name = "Всё закрыто / light", showBackground = true)
@Composable
private fun PreviewAllSettled() {
    val state = previewState(
        settled = listOf(
            previewRow("1", "Аня Петрова", "RUB" to 0),
            previewRow("2", "ООО Ромашка", "VND" to 0),
        ),
    )
    ExpeysiTheme { PreviewDebtsContent(state) }
}

@Preview(name = "Пусто / light", showBackground = true)
@Composable
private fun PreviewEmptyState() {
    ExpeysiTheme { PreviewDebtsContent(state = previewState()) }
}
