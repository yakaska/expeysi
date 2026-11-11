package com.expeysi.app.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.expeysi.app.R
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.domain.Money
import com.expeysi.app.ui.icons.BalanceIcon
import com.expeysi.app.ui.theme.ExpeysiTheme

/**
 * Шторка выбора баланса: список с итогами и футер с действиями
 * «Новый баланс…» и «Изменить». Открывается с чипа в hero-карточке.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BalanceSwitcherSheet(
    balances: List<BalanceEntity>,
    selectedBalance: BalanceEntity?,
    balanceTotals: Map<String, Long>,
    balanceSymbols: Map<String, String>,
    onBalanceSelected: (String) -> Unit,
    onAddBalance: () -> Unit,
    onEditBalance: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val millionUnit = stringResource(R.string.money_suffix_million)
    val billionUnit = stringResource(R.string.money_suffix_billion)
    val selectedCd = stringResource(R.string.period_selected_cd)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = stringResource(R.string.home_budgets_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            items(balances, key = { it.id }) { balance ->
                BalanceSwitcherRow(
                    balance = balance,
                    selected = balance.id == selectedBalance?.id,
                    totalMinor = balanceTotals[balance.id] ?: 0L,
                    currencySymbol = balanceSymbols[balance.id].orEmpty(),
                    selectedCd = selectedCd,
                    millionUnit = millionUnit,
                    billionUnit = billionUnit,
                    onClick = { onBalanceSelected(balance.id) },
                )
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onAddBalance,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.home_new_balance),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            TextButton(
                onClick = onEditBalance,
                enabled = selectedBalance != null,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.home_budgets_edit),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** Строка баланса: иконка или круг с первой буквой, название и валюта, итог и галочка выбора. */
@Composable
private fun BalanceSwitcherRow(
    balance: BalanceEntity,
    selected: Boolean,
    totalMinor: Long,
    currencySymbol: String,
    selectedCd: String,
    millionUnit: String,
    billionUnit: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BalanceIcon(name = balance.name, iconKey = balance.iconKey, colorKey = balance.colorKey)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = balance.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (currencySymbol.isNotBlank()) {
                Text(
                    text = currencySymbol,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = Money.formatCompact(totalMinor, millionUnit, billionUnit),
            style = MaterialTheme.typography.titleMedium,
            color = if (totalMinor < 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (selected) {
            Spacer(modifier = Modifier.size(8.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = selectedCd,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun previewBalance(
    id: String,
    name: String,
    currencyCode: String,
    iconKey: String = "",
    colorKey: String = "default",
) = BalanceEntity(
    id = id,
    name = name,
    currencyCode = currencyCode,
    iconKey = iconKey,
    colorKey = colorKey,
    updatedAt = 0,
)

@Composable
private fun BalanceSwitcherSheetPreview(balanceList: List<BalanceEntity>, dark: Boolean) {
    ExpeysiTheme(darkTheme = dark) {
        BalanceSwitcherSheet(
            balances = balanceList,
            selectedBalance = balanceList.firstOrNull(),
            balanceTotals = mapOf(
                "b1" to 48_750_25,
                "b2" to -12_300,
                "b3" to 900_000_00,
                "b4" to 0,
            ),
            balanceSymbols = mapOf(
                "b1" to "₽",
                "b2" to "₫",
                "b3" to "$",
                "b4" to "€",
            ),
            onBalanceSelected = {},
            onAddBalance = {},
            onEditBalance = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Один баланс · Light", showBackground = true)
@Composable
private fun BalanceSwitcherSheetSingleLightPreview() {
    BalanceSwitcherSheetPreview(
        listOf(previewBalance("b1", "Семейный", "RUB", iconKey = "payments", colorKey = "blue")),
        dark = false,
    )
}

@Preview(
    name = "Один баланс · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BalanceSwitcherSheetSingleDarkPreview() {
    BalanceSwitcherSheetPreview(
        listOf(previewBalance("b1", "Семейный", "RUB", iconKey = "payments", colorKey = "blue")),
        dark = true,
    )
}

@Preview(name = "Несколько · Light", showBackground = true)
@Composable
private fun BalanceSwitcherSheetManyLightPreview() {
    BalanceSwitcherSheetPreview(
        listOf(
            previewBalance("b1", "Семейный баланс на месяц и запасы", "RUB", iconKey = "payments", colorKey = "blue"),
            previewBalance("b2", "Вьетнам (VND)", "VND"),
            previewBalance("b3", "Доллары", "USD", iconKey = "attach_money", colorKey = "green"),
            previewBalance("b4", "", "EUR"),
        ),
        dark = false,
    )
}

@Preview(
    name = "Несколько · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BalanceSwitcherSheetManyDarkPreview() {
    BalanceSwitcherSheetPreview(
        listOf(
            previewBalance("b1", "Семейный баланс на месяц и запасы", "RUB", iconKey = "payments", colorKey = "blue"),
            previewBalance("b2", "Вьетнам (VND)", "VND"),
            previewBalance("b3", "Доллары", "USD", iconKey = "attach_money", colorKey = "green"),
            previewBalance("b4", "", "EUR"),
        ),
        dark = true,
    )
}
