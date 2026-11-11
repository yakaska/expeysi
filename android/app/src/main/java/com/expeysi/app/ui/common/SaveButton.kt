package com.expeysi.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.expeysi.app.R

/**
 * Кнопка «Сохранить» на всю ширину. Пока сохранение недоступно, тап по кнопке
 * вызывает [onDisabledClick] — например, показывает ошибку пустого названия.
 */
@Composable
fun SaveButton(
    enabled: Boolean,
    onClick: () -> Unit,
    onDisabledClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.common_save))
        }
        if (!enabled) {
            // Тап по неактивной кнопке доходит до [onDisabledClick], минуя disabled-состояние.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDisabledClick() },
            )
        }
    }
}
