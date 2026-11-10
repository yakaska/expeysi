package com.expeysi.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.expeysi.app.domain.DateRangeFilter
import com.expeysi.app.domain.HistoryPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val periodLabels = linkedMapOf(
    HistoryPeriod.DAY to "День",
    HistoryPeriod.WEEK to "Неделя",
    HistoryPeriod.MONTH to "Месяц",
    HistoryPeriod.QUARTER to "Квартал",
    HistoryPeriod.HALF_YEAR to "Полгода",
    HistoryPeriod.YEAR to "Год",
    HistoryPeriod.ALL to "Всё время",
)

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelector(
    period: HistoryPeriod,
    customRange: DateRangeFilter?,
    onPeriodSelected: (HistoryPeriod) -> Unit,
    onRangeSelected: (LocalDate, LocalDate) -> Unit,
) {
    var showRangePicker by remember { mutableStateOf(false) }
    var showPeriodDialog by remember { mutableStateOf(false) }

    if (showPeriodDialog) {
        ModalBottomSheet(
            onDismissRequest = { showPeriodDialog = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Text(
                "Период",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                periodLabels.forEach { (value, label) ->
                    val selected = customRange == null && period == value
                    ListItem(
                        headlineContent = {
                            Text(
                                label,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        trailingContent = {
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = "Выбрано")
                            }
                        },
                        modifier = Modifier.clickable {
                            onPeriodSelected(value)
                            showPeriodDialog = false
                        },
                    )
                }
                ListItem(
                    headlineContent = { Text("Диапазон дат…") },
                    trailingContent = {
                        if (customRange != null) {
                            Icon(Icons.Filled.Check, contentDescription = "Выбрано")
                        }
                    },
                    modifier = Modifier.clickable {
                        showPeriodDialog = false
                        showRangePicker = true
                    },
                )
            }
        }
    }

    if (showRangePicker) {
        val rangeState = rememberDateRangePickerState()
        Dialog(onDismissRequest = { showRangePicker = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    DateRangePicker(
                        state = rangeState,
                        modifier = Modifier.weight(1f, fill = false),
                        title = null,
                        headline = null,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showRangePicker = false }) { Text("Отмена") }
                        TextButton(
                            onClick = {
                                val start = rangeState.selectedStartDateMillis
                                val end = rangeState.selectedEndDateMillis
                                if (start != null && end != null) {
                                    val startDay = Instant.ofEpochMilli(start).atZone(ZoneId.of("UTC")).toLocalDate()
                                    val endDay = Instant.ofEpochMilli(end).atZone(ZoneId.of("UTC")).toLocalDate()
                                    onRangeSelected(startDay, endDay)
                                }
                                showRangePicker = false
                            },
                        ) { Text("ОК") }
                    }
                }
            }
        }
    }

    OutlinedCard(
        onClick = { showPeriodDialog = true },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                "Период",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                customRange?.let { rangeLabel(it) } ?: periodLabels.getValue(period),
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Выбрать период")
        }
    }
}

private fun rangeLabel(range: DateRangeFilter): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(range.startMillis).atZone(zone).format(dayFormatter)
    val end = Instant.ofEpochMilli(range.endMillisExclusive - 1).atZone(zone).format(dayFormatter)
    return "$start–$end"
}
