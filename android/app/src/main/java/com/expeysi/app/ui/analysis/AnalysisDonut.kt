package com.expeysi.app.ui.analysis

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expeysi.app.R
import kotlin.math.min
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.util.lerp as lerpFloat

private const val ANIMATION_MILLIS = 350
private val donutDiameter = 250.dp
private val donutStroke = 20.dp
private const val DONUT_GAP_DEGREES = 2f
private val swipeThreshold = 48.dp

/** РљРѕР»СЊС†РµРІР°СЏ РґРёР°РіСЂР°РјРјР° СЃ СЃРµРіРјРµРЅС‚Р°РјРё РїРѕ РґРѕР»СЏРј РєР°С‚РµРіРѕСЂРёР№, СЃС‚СЂРµР»РєР°РјРё Рё СЃРІР°Р№РїРѕРј РјРµСЃСЏС†Р°. */
@Composable
internal fun AnalysisDonut(
    shares: List<Float>,
    colors: List<Color>,
    centerLabel: String,
    description: String,
    nextEnabled: Boolean,
    onSegmentClick: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strokePx = with(LocalDensity.current) { donutStroke.toPx() }

    val previousShares = remember { mutableStateOf(shares) }
    val previousColors = remember { mutableStateOf(colors) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(shares, colors) {
        if (previousShares.value == shares && previousColors.value == colors) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(ANIMATION_MILLIS))
            previousShares.value = shares
            previousColors.value = colors
        }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.analysis_prev_month_cd),
                tint = AnalysisColors.TextPrimary,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(donutDiameter)
                .semantics { contentDescription = description }
                .pointerInput(shares) {
                    detectTapGestures { offset ->
                        val index = DonutGeometry.segmentIndexAt(
                            x = offset.x,
                            y = offset.y,
                            centerX = size.width / 2f,
                            centerY = size.height / 2f,
                            radius = min(size.width, size.height) / 2f,
                            stroke = strokePx,
                            shares = shares,
                        )
                        if (index >= 0) onSegmentClick(index)
                    }
                }
                .pointerInput(onPrevious, onNext) {
                    var accumulated = 0f
                    val threshold = with(density) { swipeThreshold.toPx() }
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                accumulated <= -threshold -> onNext()
                                accumulated >= threshold -> onPrevious()
                                else -> Unit
                            }
                            accumulated = 0f
                        },
                    ) { change, dragAmount ->
                        accumulated += dragAmount
                        change.consume()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = min(size.width, size.height) / 2f
                val innerRadius = radius - strokePx / 2f
                if (shares.sum() <= 0f) {
                    drawCircle(
                        color = AnalysisColors.DonutEmpty,
                        radius = innerRadius,
                        center = center,
                        style = Stroke(width = strokePx),
                    )
                } else {
                    val blendedShares = blendShares(shares, previousShares.value, progress.value)
                    val blendedColors = blendColors(colors, previousColors.value, progress.value)
                    DonutGeometry.segments(blendedShares, DONUT_GAP_DEGREES)
                        .forEachIndexed { index, segment ->
                            drawArc(
                                color = blendedColors.getOrElse(index) { AnalysisColors.DonutEmpty },
                                startAngle = DonutGeometry.START_ANGLE + segment.startAngle,
                                sweepAngle = segment.sweep,
                                useCenter = false,
                                topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                                size = Size(innerRadius * 2f, innerRadius * 2f),
                                style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                            )
                        }
                }
            }
            Crossfade(targetState = centerLabel) { label ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        color = AnalysisColors.TextSecondary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        IconButton(onClick = onNext, enabled = nextEnabled) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.analysis_next_month_cd),
                tint = AnalysisColors.TextPrimary,
            )
        }
    }
}

private fun blendShares(target: List<Float>, from: List<Float>, fraction: Float): List<Float> = List(maxOf(target.size, from.size)) { index ->
    lerpFloat(from.getOrElse(index) { 0f }, target.getOrElse(index) { 0f }, fraction)
}

private fun blendColors(target: List<Color>, from: List<Color>, fraction: Float): List<Color> = List(maxOf(target.size, from.size)) { index ->
    val newColor = target.getOrElse(index) { target.lastOrNull() ?: AnalysisColors.DonutEmpty }
    val oldColor = from.getOrElse(index) { newColor }
    lerpColor(oldColor, newColor, fraction)
}
