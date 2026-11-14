package com.expeysi.app.ui.history

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.expeysi.app.R
import com.expeysi.app.domain.DateRangeFilter
import com.expeysi.app.domain.HistoryPeriod
import com.expeysi.app.ui.common.DateRangePickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Метка периода истории: День / Неделя / … / Всё время. */
internal fun HistoryPeriod.labelRes(): Int = when (this) {
    HistoryPeriod.DAY -> R.string.period_day
    HistoryPeriod.WEEK -> R.string.period_week
    HistoryPeriod.MONTH -> R.string.period_month
    HistoryPeriod.QUARTER -> R.string.period_quarter
    HistoryPeriod.HALF_YEAR -> R.string.period_half_year
    HistoryPeriod.YEAR -> R.string.period_year
    HistoryPeriod.ALL -> R.string.period_all
}

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")

private data class PeriodSheetOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * Шторка выбора периода, одинаковая для отчётов и анализа:
 * скользящие периоды и произвольный диапазон дат.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodPickerSheet(
    period: HistoryPeriod,
    customRange: DateRangeFilter?,
    onPeriodSelected: (HistoryPeriod) -> Unit,
    onRangeSelected: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var showRangePicker by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(R.string.period_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            HistoryPeriod.entries.forEach { value ->
                val label = stringResource(value.labelRes())
                val selected = customRange == null && period == value
                PeriodOptionRow(
                    label = label,
                    selected = selected,
                    onClick = {
                        onPeriodSelected(value)
                        onDismiss()
                    },
                )
            }
            PeriodOptionRow(
                label = stringResource(R.string.period_custom_range),
                selected = customRange != null,
                onClick = { showRangePicker = true },
            )
        }
        if (showRangePicker) {
            DateRangePickerDialog(
                initialStart = customRange?.startLocalDate(),
                initialEnd = customRange?.endLocalDate(),
                onConfirm = onRangeSelected,
                onDismiss = onDismiss,
            )
        }
    }
}

/** Пункт выбора периода с галочкой у выбранного значения. */
@Composable
private fun PeriodOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.period_selected_cd),
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Триггер-карточка отчётов: текущий выбор периода и открытие шторки. */
@Composable
fun PeriodSelector(
    period: HistoryPeriod,
    customRange: DateRangeFilter?,
    onPeriodSelected: (HistoryPeriod) -> Unit,
    onRangeSelected: (LocalDate, LocalDate) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    OutlinedCard(
        onClick = { showSheet = true },
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
                stringResource(R.string.period_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = customRange?.let { rangeLabel(it) } ?: stringResource(period.labelRes()),
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.period_pick_cd))
        }
    }
    if (showSheet) {
        PeriodPickerSheet(
            period = period,
            customRange = customRange,
            onPeriodSelected = onPeriodSelected,
            onRangeSelected = onRangeSelected,
            onDismiss = { showSheet = false },
        )
    }
}

internal fun rangeLabel(range: DateRangeFilter): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(range.startMillis).atZone(zone).format(dayFormatter)
    val end = Instant.ofEpochMilli(range.endMillisExclusive - 1).atZone(zone).format(dayFormatter)
    return "$start–$end"
}

/** Начало диапазона пикеру: millis хранятся в системной зоне (см. DateRangeFilter.ofDays). */
private fun DateRangeFilter.startLocalDate(): LocalDate = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()

/** Конец диапазона пикеру: миллисекунда конца дня, у последнего дня — от неё же. */
private fun DateRangeFilter.endLocalDate(): LocalDate = Instant.ofEpochMilli(endMillisExclusive - 1).atZone(ZoneId.systemDefault()).toLocalDate()
