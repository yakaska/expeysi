package com.expeysi.app.ui.analysis

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/** Геометрия кольцевой диаграммы без зависимостей от Compose. */
object DonutGeometry {

    /** Сегмент дуги; углы в градусах от 12 часов по часовой стрелке. */
    data class Segment(val startAngle: Float, val sweep: Float)

    /** 12 часов в системе углов Canvas (0 = 3 часа, положительное направление — по часовой). */
    const val START_ANGLE = -90f

    /** Допуск касания по радиусу в пикселях. */
    const val HIT_SLOP = 8f

    /**
     * Сегменты по долям (сумма произвольна) с зазором между соседними.
     * Кольцо из одного сегмента рисуется без зазоров. Пустой список — пустое кольцо.
     */
    fun segments(shares: List<Float>, gapDegrees: Float): List<Segment> {
        val total = shares.sum()
        if (total <= 0f) return emptyList()
        val gap = if (shares.size > 1) gapDegrees else 0f
        var cursor = 0f
        val segments = ArrayList<Segment>(shares.size)
        shares.forEach { share ->
            val sweep = share / total * 360f
            val visible = (sweep - gap).coerceAtLeast(0f)
            segments += Segment(startAngle = cursor + gap / 2f, sweep = visible)
            cursor += sweep
        }
        return segments
    }

    /**
     * Индекс сегмента по точке касания в локальных координатах контейнера, -1 если мимо
     * кольца или все доли нулевые. Границы сегментов берутся без зазоров.
     */
    fun segmentIndexAt(
        x: Float,
        y: Float,
        centerX: Float,
        centerY: Float,
        radius: Float,
        stroke: Float,
        shares: List<Float>,
    ): Int {
        if (shares.sum() <= 0f) return -1
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt(dx * dx + dy * dy)
        val inner = radius - stroke / 2f - HIT_SLOP
        val outer = radius + stroke / 2f + HIT_SLOP
        if (distance < inner || distance > outer) return -1
        val angle = atan2(dy, dx) * 180f / PI.toFloat()
        val fromTop = (angle - START_ANGLE + 360f) % 360f
        val total = shares.sum()
        var cursor = 0f
        shares.forEachIndexed { index, share ->
            if (share <= 0f) return@forEachIndexed
            cursor += share / total * 360f
            if (fromTop < cursor) return index
        }
        return shares.lastIndex
    }
}
