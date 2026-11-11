package com.expeysi.app.ui.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Иконка баланса: тональный круг CategoryIcon; пустой ключ — круг с первой буквой названия. */
@Composable
fun BalanceIcon(
    name: String,
    iconKey: String,
    colorKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    if (iconKey.isBlank()) {
        Box(
            modifier = modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().takeIf { it.isNotEmpty() }?.take(1)?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        CategoryIcon(iconKey = iconKey, colorKey = colorKey, modifier = modifier, size = size)
    }
}
