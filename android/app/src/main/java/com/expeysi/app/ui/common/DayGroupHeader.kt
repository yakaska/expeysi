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

/**
 * Стик-заголовок дня в списках операций: «9 ноября · − 1 200 ₽».
 * Без [totalText] итог считается из доходов и расходов дня; пустая строка
 * оставляет в заголовке только дату — итога нет вовсе.
 */
@Composable
fun DayGroupHeader(
    dateLabel: String,
    incomeMinor: Long,
    expenseMinor: Long,
    currencySymbol: String,
    modifier: Modifier = Modifier,
    totalText: String? = null,
) {
    val total = when {
        totalText == null -> dayNetTotal(incomeMinor, expenseMinor, currencySymbol)
        totalText.isEmpty() -> null
        else -> totalText
    }
    Text(
        text = total
            ?.let { stringResource(R.string.common_day_header_total, dateLabel, it) }
            ?: dateLabel,
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
