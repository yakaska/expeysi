package com.expeysi.app.ui.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Ячеек в ряду сеток иконок и цветов. */
internal const val GRID_COLUMNS = 6

/** Сетка иконок: 6 в ряд; у выбранной фон выбранного цвета, глиф белый. */
@Composable
fun IconGrid(
    iconKeys: List<String>,
    selectedColor: Color,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        iconKeys.chunked(GRID_COLUMNS).forEach { rowKeys ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowKeys.forEach { key ->
                    val selected = key == selectedKey
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .then(
                                    if (selected) {
                                        Modifier.background(selectedColor)
                                    } else {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                    },
                                )
                                .clickable { onSelect(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = IconCatalog.icon(key),
                                contentDescription = key,
                                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
