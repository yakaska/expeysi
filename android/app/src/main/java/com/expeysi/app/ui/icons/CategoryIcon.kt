package com.expeysi.app.ui.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Тональный круг категории: полупрозрачный фон цвета категории, глиф цветом категории. */
@Composable
fun CategoryIcon(
    iconKey: String,
    colorKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val color = CategoryPalette.color(colorKey)
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            IconCatalog.icon(iconKey),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
