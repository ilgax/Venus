package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.core.Bounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScrollbarMathTest {
    @Test
    fun `metrics are absent when all items fit`() {
        assertNull(ScrollbarMath.metrics(Bounds(10, 20, 100, 108), totalItems = 10, visibleItems = 10))
    }

    @Test
    fun `metrics derive track and proportional thumb geometry`() {
        val metrics = ScrollbarMath.metrics(Bounds(10, 20, 100, 108), totalItems = 20, visibleItems = 5)

        assertEquals(102, metrics?.x)
        assertEquals(24, metrics?.trackY)
        assertEquals(100, metrics?.trackHeight)
        assertEquals(25, metrics?.thumbHeight)
        assertEquals(75, metrics?.maxThumbTravel)
    }

    @Test
    fun `metrics enforce minimum thumb height`() {
        val metrics = ScrollbarMath.metrics(Bounds(0, 0, 20, 108), totalItems = 1000, visibleItems = 1)

        assertEquals(12, metrics?.thumbHeight)
        assertEquals(88, metrics?.maxThumbTravel)
    }

    @Test
    fun `thumb conversion clamps track endpoints`() {
        val metrics = ScrollbarMetrics(0, 10, 100, 20, 80)

        assertEquals(0, ScrollbarMath.scrollFromThumb(-20, metrics, 40))
        assertEquals(20, ScrollbarMath.scrollFromThumb(50, metrics, 40))
        assertEquals(40, ScrollbarMath.scrollFromThumb(200, metrics, 40))
    }

    @Test
    fun `zero travel and zero scroll remain at track origin`() {
        val fixed = ScrollbarMetrics(0, 10, 20, 20, 0)
        val movable = ScrollbarMetrics(0, 10, 100, 20, 80)

        assertEquals(0, ScrollbarMath.scrollFromThumb(30, fixed, 40))
        assertEquals(10, ScrollbarMath.thumbY(20, 0, movable))
        assertEquals(50, ScrollbarMath.thumbY(20, 40, movable))
    }
}
