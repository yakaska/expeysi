package com.expeysi.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.expeysi.app.R

/** Итог дня в стик-заголовке: нетто (доходы − расходы) со знаком; ноль — без знака. */
private fun dayNetTotal(incomeMinor: Long, expenseMinor: Long, currencySymbol: String): String {
    val net = incomeMinor - expenseMinor
    return when {
        net > 0L -> "+" + AmountFormat.withCurrency(net, currencySymbol)
        net < 0L -> "−" + AmountFormat.withCurrency(-net, currencySymbol)
        else -> AmountFormat.withCurrency(0L, currencySymbol)
    }
}

/** Стик-заголовок дня в списках операций: «9 ноября · − 1 200 ₽». */
@Composable
fun DayGroupHeader(
    dateLabel: String,
    incomeMinor: Long,
    expenseMinor: Long,
    currencySymbol: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(
            R.string.common_day_header_total,
            dateLabel,
            dayNetTotal(incomeMinor, expenseMinor, currencySymbol),
        ),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Отступы (modifier) применяются после фона: плашка закрывает их,
        // иначе в стик-заголовке виден прозрачный зазор сверху.
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .then(modifier),
    )
}
