package com.expeysi.app.ui.debts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expeysi.app.R

// Повышенный контраст полей в тёмной теме вместо стандартных 0xFF949496/0xFF8E8E93.
private val DarkFieldBorder = Color(0xFFB5B5BA)
private val DarkFieldLabel = Color(0xFFA6A6AB)

/** Рамка и подпись-плейсхолдер полей: в тёмной теме заметнее стандартных. */
@Composable
internal fun debtFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = if (isSystemInDarkTheme()) DarkFieldBorder else MaterialTheme.colorScheme.outline,
    unfocusedLabelColor = if (isSystemInDarkTheme()) DarkFieldLabel else MaterialTheme.colorScheme.onSurfaceVariant,
)

/** Карточка формы долгов: рамка, как у полей, вертикальный шаг внутри. */
@Composable
internal fun DebtFormCard(
    verticalSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            content()
        }
    }
}

/**
 * Строка выбора (счёт, контрагент, срок) в карточках форм долгов: пока
 * ничего не выбрано — подпись поля и подсказка «Выбрать…», когда выбрано —
 * значение; круглый слот иконки слева необязателен.
 */
@Composable
internal fun DebtSelectorRow(
    modifier: Modifier = Modifier,
    label: String,
    value: String?,
    subtitle: String? = null,
    subtitleNegative: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        if (icon != null) {
            icon()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (icon != null) 12.dp else 0.dp),
        ) {
            if (value == null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.common_select),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (subtitleNegative) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}

/** Кружок со смысловой иконкой: цвет красит фон полупрозрачно, глиф — целиком. */
@Composable
internal fun DebtIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(tint.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Иконка в кружке вдвое меньше круга — пропорции CategoryIcon.
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size / 2),
        )
    }
}

/** Однострочная подпись заметки: длинный комментарий не ломает строку. */
@Composable
internal fun DebtNoteLine(note: String, modifier: Modifier = Modifier) {
    if (note.isBlank()) return
    Text(
        text = note,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
