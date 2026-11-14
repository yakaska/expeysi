package com.expeysi.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.expeysi.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Диалог выбора диапазона дат для всех экранов приложения. Геометрия
 * исправлена относительно стандартного диалога Material 3:
 *  - окну разрешена вся ширина экрана, поля по 16dp, максимум 392dp; ячейке дня
 *    задан минимум 40dp (токен DateContainerWidth): иначе на узких экранах
 *    7 × 48dp не влезают и последний столбец дня недели срезается;
 *  - календарь ограничен по высоте и показывает начало следующего месяца,
 *    поэтому диапазон через границу месяца выбирается прокруткой, без ввода дат;
 *  - «ОК» вызывает [onConfirm] и закрывает диалог ([onDismiss]); кнопка доступна,
 *    только когда выбраны обе даты.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val rangeState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.toUtcMidnightMillis(),
        initialSelectedEndDateMillis = initialEnd?.toUtcMidnightMillis(),
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 392.dp),
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                    DateRangePicker(
                        state = rangeState,
                        modifier = Modifier.heightIn(max = 460.dp),
                        title = null,
                        headline = null,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    TextButton(
                        enabled = rangeState.selectedStartDateMillis != null &&
                            rangeState.selectedEndDateMillis != null,
                        onClick = {
                            onConfirm(
                                fromUtcMidnightMillis(rangeState.selectedStartDateMillis!!),
                                fromUtcMidnightMillis(rangeState.selectedEndDateMillis!!),
                            )
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}

/**
 * Конвенция пикеров дат проекта — миллисекунды полуночи в UTC
 * (как в AddTransactionViewModel).
 */
private fun LocalDate.toUtcMidnightMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun fromUtcMidnightMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
