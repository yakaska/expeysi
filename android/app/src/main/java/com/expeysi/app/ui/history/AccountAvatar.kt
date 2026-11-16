package com.expeysi.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expeysi.app.R
import com.expeysi.app.ui.home.TransferAccount
import com.expeysi.app.ui.icons.CategoryIcon
import com.expeysi.app.ui.icons.CategoryPalette

/**
 * Аватар счёта в карточках переводов: тональный круг с иконкой счёта, без
 * иконки — первая буква имени. Цвет берётся из палитры по colorKey счёта,
 * выбранному при его создании; он детерминирован и одинаково читается в
 * светлой и тёмной теме. Никакие пользовательские цвета здесь не
 * перекрашиваются.
 */
@Composable
internal fun AccountAvatar(account: TransferAccount, size: Dp) {
    if (account.iconKey.isBlank()) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(CategoryPalette.color(account.colorKey).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = account.name
                    .ifBlank { stringResource(R.string.common_account) }
                    .take(1)
                    .uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = CategoryPalette.color(account.colorKey),
                modifier = Modifier.padding(1.dp),
            )
        }
    } else {
        CategoryIcon(
            iconKey = account.iconKey,
            colorKey = account.colorKey,
            size = size,
        )
    }
}
