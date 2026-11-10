package com.expeysi.app.ui.analysis

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Фиксированная тёмная тема экрана анализа. */
object AnalysisColors {

    val Background = Color(0xFF111111)
    val Card = Color(0xFF1E1E1E)
    val Divider = Color(0xFF2C2C2E)
    val Skeleton = Color(0xFF2C2C2E)
    val TextPrimary = Color(0xFFF2F3F5)
    val TextSecondary = Color(0xFF8E8E93)
    val Accent = Color(0xFF66BB6A)
    val DonutEmpty = Color(0xFF2C2C2E)

    /** Скругление карточек, 24dp. */
    val cardShape = RoundedCornerShape(24.dp)
}
