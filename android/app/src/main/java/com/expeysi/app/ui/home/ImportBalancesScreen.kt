package com.expeysi.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.expeysi.app.R
import com.expeysi.app.domain.Money
import com.expeysi.app.ui.MoneyTextField

/**
 * Экран остатков импорта внешней таблицы: у файла нет остатков счетов, поэтому
 * перед записью показывается список счетов файла с полем «сколько на счете
 * сейчас». Введенные значения (в том числе 0 — счет пуст или закрыт) превращает
 * импорт в первоначальные остатки; пустое поле счета остаток импорта не задает —
 * баланс копии считается только по операциям файла.
 */
@Composable
fun ImportBalancesScreen(
    state: ImportBalancesUiState,
    onConfirm: (Map<Int, Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val typed = remember(state.rows) { mutableStateMapOf<Int, String>() }

    fun typedMinor(index: Int): Long? = typed[index]
        ?.takeIf { it.isNotBlank() }
        ?.let { Money.parseToMinor(it) }

    /** Пустое поле — остаток не задается; непустое обязано разбираться числом. */
    fun valid(index: Int) = typed[index].isNullOrBlank() || typedMinor(index) != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 16.dp),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
                    }
                    Text(
                        stringResource(R.string.import_balances_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Text(
                    stringResource(R.string.import_balances_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    itemsIndexed(state.rows) { index, row ->
                        ImportBalanceItem(
                            row = row,
                            typed = typed[index].orEmpty(),
                            isError = typedMinor(index) == null && !typed[index].isNullOrEmpty(),
                            onTyped = { value -> typed[index] = value },
                            enabled = !state.inProgress,
                        )
                        HorizontalDivider()
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !state.inProgress,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(
                        enabled = !state.inProgress && state.rows.indices.all(::valid),
                        onClick = {
                            onConfirm(
                                typed.keys.mapNotNull { index ->
                                    typedMinor(index)?.let { current -> index to current }
                                }.toMap(),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.inProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.import_balances_import))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportBalanceItem(
    row: ImportBalanceRow,
    typed: String,
    isError: Boolean,
    onTyped: (String) -> Unit,
    enabled: Boolean,
) {
    val balanceLabel = listOfNotNull(row.balanceName, row.currencyCode).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.accountName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                balanceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MoneyTextField(
            value = typed,
            onValueChange = onTyped,
            label = { Text(stringResource(R.string.import_balances_field_hint)) },
            supportingText = {
                Text(stringResource(R.string.import_balances_file_net, Money.formatAmount(row.netMinor, row.currencyCode)))
            },
            isError = isError,
            enabled = enabled,
            modifier = Modifier.width(170.dp),
        )
    }
}
