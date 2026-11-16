package com.expeysi.app.ui.icons

import androidx.compose.ui.graphics.Color

/**
 * Ключ цвета категории → Material-цвет, одинаково читаемый на светлой и
 * тёмной теме: мягкие (тон 300) и средние (400–500), для оттенков-близнецов
 * ещё и глубокие (600–800). Неизвестный ключ → default.
 *
 * Порядок = порядок в сетке выбора: шесть ячеек в ряду, от тёплых оттенков
 * к холодным и нейтральным. Первый ключ («orange») — цвет новых категорий
 * и счетов.
 */
object CategoryPalette {

    const val DEFAULT_KEY = "default"

    private val colors: Map<String, Color> = mapOf(
        // Тёплые
        "orange" to Color(0xFFFFA726),
        "deep_orange" to Color(0xFFFF7043),
        "red" to Color(0xFFEF5350),
        "wine" to Color(0xFFC62828),
        "pink" to Color(0xFFEC407A),
        "berry" to Color(0xFFD81B60),
        // Фиолетовые
        "pink_light" to Color(0xFFF06292),
        "purple" to Color(0xFFAB47BC),
        "deep_purple" to Color(0xFF7E57C2),
        "lavender" to Color(0xFF9575CD),
        "indigo" to Color(0xFF5C6BC0),
        "navy" to Color(0xFF3F51B5),
        // Холодные
        "blue" to Color(0xFF42A5F5),
        "ocean" to Color(0xFF1E88E5),
        "cyan" to Color(0xFF26C6DA),
        "teal" to Color(0xFF26A69A),
        "mint" to Color(0xFF4DB6AC),
        "green" to Color(0xFF66BB6A),
        // Натуральные и нейтральные
        "light_green" to Color(0xFF8BC34A),
        "lime" to Color(0xFFC0CA33),
        "brown" to Color(0xFF8D6E63),
        "amber" to Color(0xFFFFCA28),
        "graphite" to Color(0xFF37474F),
        DEFAULT_KEY to Color(0xFF78909C),
    )

    val availableKeys: List<String> = colors.keys.toList()

    fun color(key: String): Color = colors[key] ?: colors.getValue(DEFAULT_KEY)
}
