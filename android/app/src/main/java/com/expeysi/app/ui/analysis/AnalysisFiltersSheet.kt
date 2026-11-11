package com.expeysi.app.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.expeysi.app.R
import com.expeysi.app.data.analysis.AnalysisAccount
import com.expeysi.app.ui.icons.CategoryIcon

/**
 * Нижняя шторка «Фильтры». Секции расширяемые: новая секция — новый FilterSection.
 * Изменения живут в черновике [draft] и применяются только по кнопке.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnalysisFiltersSheet(
    accounts: List<AnalysisAccount>,
    draft: Set<String>?,
    onAccountToggle: (String) -> Unit,
    onAllAccounts: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.analysis_filters),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FilterSection(title = stringResource(R.string.home_accounts)) {
                FilterAccountRow(
                    iconContent = {
                        Icon(
                            Icons.Filled.CreditCard,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    iconColor = MaterialTheme.colorScheme.outlineVariant,
                    title = stringResource(R.string.analysis_all_accounts),
                    subtitle = "",
                    // Индикатор: горит и при ручном выборе всех счетов, не меняя сам выбор.
                    selected = draft == null || accounts.all { it.id in draft },
                    onToggle = onAllAccounts,
                )
                accounts.forEach { account ->
                    FilterAccountRow(
                        iconContent = {
                            CategoryIcon(iconKey = account.iconKey, colorKey = account.colorKey)
                        },
                        iconColor = Color.Transparent,
                        title = account.name,
                        subtitle = account.last4,
                        selected = draft != null && account.id in draft,
                        onToggle = { onAccountToggle(account.id) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onReset) {
                    Text(text = stringResource(R.string.analysis_reset))
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.analysis_apply))
                }
            }
        }
    }
}

/** Секция фильтра: заголовок и содержимое. Новые секции добавляются так же. */
@Composable
private fun FilterSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        content()
    }
}

@Composable
private fun FilterAccountRow(
    iconContent: @Composable () -> Unit,
    iconColor: Color,
    title: String,
    subtitle: String,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            iconContent()
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = stringResource(R.string.analysis_account_last4, subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
    }
}
