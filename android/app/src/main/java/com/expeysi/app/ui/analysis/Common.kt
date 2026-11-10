package com.expeysi.app.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expeysi.app.R
import com.expeysi.app.ui.icons.CategoryPalette
import com.expeysi.app.ui.icons.IconCatalog

/** Круглый бейдж категории: фон цвета категории с прозрачностью, иконка цветом категории. */
@Composable
internal fun AnalysisCategoryBadge(
    iconKey: String,
    colorKey: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 40.dp,
) {
    val color = CategoryPalette.color(colorKey)
    Box(
        modifier = modifier
            .size(diameter)
            .background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = IconCatalog.icon(iconKey),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Серые скелет-полоски на месте текста. */
@Composable
internal fun SkeletonBar(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width, height)
            .background(AnalysisColors.Skeleton, RoundedCornerShape(6.dp)),
    )
}

/** Затемнение поверх контента на время обновления данных. */
@Composable
internal fun RefreshScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures { } },
    )
}

/** Сообщение об ошибке с действием «Повторить». */
@Composable
internal fun ErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.analysis_error),
            color = AnalysisColors.TextSecondary,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
        ) {
            Text(text = stringResource(R.string.common_retry))
        }
    }
}
