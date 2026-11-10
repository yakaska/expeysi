package com.expeysi.app.ui.analysis

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class DonutGeometryTest {

    private val shares = listOf(0.5f, 0.3f, 0.2f)

    /** Точка на кольце с центром в (0, 0); угол от 12 часов по часовой стрелке. */
    private fun pointAt(angleDegrees: Float, radius: Float = 100f): Pair<Float, Float> {
        val radians = Math.toRadians(angleDegrees.toDouble())
        return Pair((sin(radians) * radius).toFloat(), (-cos(radians) * radius).toFloat())
    }

    @Test
    fun `сегменты начинаются с 12 часов по часовой стрелке`() {
        val segments = DonutGeometry.segments(shares, gapDegrees = 2f)
        assertEquals(1f, segments[0].startAngle, 0.001f)
        assertEquals(178f, segments[0].sweep, 0.001f)
        val secondStart = 180f + 1f
        assertEquals(secondStart, segments[1].startAngle, 0.001f)
        assertEquals(106f, segments[1].sweep, 0.001f)
    }

    @Test
    fun `единственный сегмент без зазоров`() {
        val segments = DonutGeometry.segments(listOf(1f), gapDegrees = 2f)
        assertEquals(1, segments.size)
        assertEquals(0f, segments[0].startAngle, 0.001f)
        assertEquals(360f, segments[0].sweep, 0.001f)
    }

    @Test
    fun `пустые доли не дают сегментов`() {
        assertEquals(emptyList<DonutGeometry.Segment>(), DonutGeometry.segments(emptyList(), 2f))
        assertEquals(emptyList<DonutGeometry.Segment>(), DonutGeometry.segments(listOf(0f, 0f), 2f))
    }

    @Test
    fun `касание на 12 часов попадает в первый сегмент`() {
        val (x, y) = pointAt(angleDegrees = 0f)
        val index = DonutGeometry.segmentIndexAt(x, y, 0f, 0f, 100f, 20f, shares)
        assertEquals(0, index)
    }

    @Test
    fun `касание на 3 часа попадает в первый сегмент`() {
        val (x, y) = pointAt(angleDegrees = 90f)
        val index = DonutGeometry.segmentIndexAt(x, y, 0f, 0f, 100f, 20f, shares)
        assertEquals(0, index)
    }

    @Test
    fun `касание на 9 часов попадает во второй сегмент`() {
        val (x, y) = pointAt(angleDegrees = 270f)
        val index = DonutGeometry.segmentIndexAt(x, y, 0f, 0f, 100f, 20f, shares)
        assertEquals(1, index)
    }

    @Test
    fun `касание ближе к концу кольца попадает в последний сегмент`() {
        val (x, y) = pointAt(angleDegrees = 300f)
        val index = DonutGeometry.segmentIndexAt(x, y, 0f, 0f, 100f, 20f, shares)
        assertEquals(2, index)
    }

    @Test
    fun `касание вне кольца игнорируется`() {
        val (outerX, outerY) = pointAt(angleDegrees = 0f, radius = 130f)
        assertEquals(-1, DonutGeometry.segmentIndexAt(outerX, outerY, 0f, 0f, 100f, 20f, shares))
        val (innerX, innerY) = pointAt(angleDegrees = 0f, radius = 80f)
        assertEquals(-1, DonutGeometry.segmentIndexAt(innerX, innerY, 0f, 0f, 100f, 20f, shares))
    }

    @Test
    fun `касание смещённого центра считается от центра`() {
        val centerX = -20f
        val centerY = 10f
        val (dx, dy) = pointAt(angleDegrees = 30f)
        val index = DonutGeometry.segmentIndexAt(centerX + dx, centerY + dy, centerX, centerY, 100f, 20f, shares)
        assertEquals(0, index)
    }

    @Test
    fun `нулевые доли не определяют сегмент`() {
        assertEquals(-1, DonutGeometry.segmentIndexAt(0f, -100f, 0f, 0f, 100f, 20f, listOf(0f, 0f)))
    }

    @Test
    fun `центр кольца считается отверстием`() {
        assertEquals(true, DonutGeometry.isInsideHole(0f, 0f, 0f, 0f, 100f, 20f))
    }

    @Test
    fun `касание у внутреннего края отверстия разграничивается`() {
        val (insideX, insideY) = pointAt(angleDegrees = 40f, radius = 81f)
        assertEquals(true, DonutGeometry.isInsideHole(insideX, insideY, 0f, 0f, 100f, 20f))
        val (outsideX, outsideY) = pointAt(angleDegrees = 40f, radius = 83f)
        assertEquals(false, DonutGeometry.isInsideHole(outsideX, outsideY, 0f, 0f, 100f, 20f))
    }
}
