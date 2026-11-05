package com.expeysi.app.ui.icons

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Иконка категории в круглом ободе цвета категории. */
@Composable
fun CategoryIcon(iconKey: String, colorKey: String, modifier: Modifier = Modifier) {
    val color = CategoryPalette.color(colorKey)
    Box(
        modifier = modifier
            .size(40.dp)
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            IconCatalog.icon(iconKey),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
    }
}
