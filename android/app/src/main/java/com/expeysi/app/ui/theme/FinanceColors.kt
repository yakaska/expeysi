package com.expeysi.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Смысловые цвета денег: доходы — зелёные, расходы — красные. */
@Immutable
data class FinanceColors(
    val income: Color,
    val expense: Color,
)

/*
 * Светлая тема: тёмные тона, контраст с surface (#FAFAFB)
 * и surfaceContainer (#EFEFF1) — 7.5:1 / 6.9:1 для доходов,
 * 6.3:1 / 5.7:1 для расходов.
 */
val LightFinanceColors = FinanceColors(
    income = Color(0xFF1B5E20),
    expense = Color(0xFFB3261E),
)

/*
 * Тёмная тема: светлые тона, контраст с surface (#111111)
 * и surfaceContainer (#1E1E1E) — 8.0:1 / 7.1:1 для доходов,
 * 7.9:1 / 7.0:1 для расходов.
 */
val DarkFinanceColors = FinanceColors(
    income = Color(0xFF66BB6A),
    expense = Color(0xFFF28B82),
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }
