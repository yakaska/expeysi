package com.expeysi.app.ui.icons

import androidx.compose.ui.graphics.Color

/**
 * Ключ цвета категории → Material-цвет средней насыщенности (тон 400–500),
 * одинаково читаемый на светлой и тёмной теме. Неизвестный ключ → default.
 */
object CategoryPalette {

    const val DEFAULT_KEY = "default"

    private val colors: Map<String, Color> = mapOf(
        "orange" to Color(0xFFFFA726),
        "blue" to Color(0xFF42A5F5),
        "brown" to Color(0xFF8D6E63),
        "purple" to Color(0xFFAB47BC),
        "teal" to Color(0xFF26A69A),
        "red" to Color(0xFFEF5350),
        "pink" to Color(0xFFEC407A),
        "deep_purple" to Color(0xFF7E57C2),
        "pink_light" to Color(0xFFF06292),
        "green" to Color(0xFF66BB6A),
        "amber" to Color(0xFFFFCA28),
        DEFAULT_KEY to Color(0xFF78909C),
    )

    val availableKeys: List<String> = colors.keys.toList()

    fun color(key: String): Color = colors[key] ?: colors.getValue(DEFAULT_KEY)
}
