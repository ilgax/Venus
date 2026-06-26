package dev.ilgax.venus.client.ui.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnimationTest {
    @Test
    fun `starts at zero and snaps to target with reduced motion`() {
        val anim = Animation(durationMs = 100f, reducedMotion = { true })
        anim.target = 1f
        assertEquals(1f, anim.value)
    }

    @Test
    fun `tick advances toward target`() {
        val anim = Animation(durationMs = 100f)
        anim.target = 1f
        val v1 = anim.tick(10f)
        assertTrue(v1 > 0f && v1 < 1f)
        val v2 = anim.tick(100f)
        assertEquals(1f, v2)
    }

    @Test
    fun `tick does not overshoot target`() {
        val anim = Animation(durationMs = 50f)
        anim.target = 1f
        anim.tick(100f)
        assertEquals(1f, anim.value)
    }

    @Test
    fun `setImmediate sets current and goal`() {
        val anim = Animation(durationMs = 100f)
        anim.setImmediate(0.5f)
        assertEquals(0.5f, anim.value)
        assertEquals(0.5f, anim.target)
        assertTrue(anim.isComplete)
    }

    @Test
    fun `tick handles backward animation`() {
        val anim = Animation(durationMs = 100f)
        anim.setImmediate(1f)
        anim.target = 0f
        val v = anim.tick(50f)
        assertTrue(v < 1f && v > 0f)
        anim.tick(100f)
        assertEquals(0f, anim.value)
    }
}

class BoundsTest {
    @Test
    fun `contains checks inclusive top-left exclusive bottom-right`() {
        val b = Bounds(10, 10, 20, 20)
        assertTrue(b.contains(10, 10))
        assertTrue(b.contains(29, 29))
        assertTrue(!b.contains(30, 30))
        assertTrue(!b.contains(9, 10))
    }

    @Test
    fun `inset shrinks uniformly`() {
        val b = Bounds(10, 10, 20, 20).inset(4)
        assertEquals(14, b.x)
        assertEquals(14, b.y)
        assertEquals(12, b.width)
        assertEquals(12, b.height)
    }

    @Test
    fun `right and bottom computed correctly`() {
        val b = Bounds(10, 10, 20, 30)
        assertEquals(30, b.right)
        assertEquals(40, b.bottom)
        assertEquals(20, b.centerX)
        assertEquals(25, b.centerY)
    }
}
