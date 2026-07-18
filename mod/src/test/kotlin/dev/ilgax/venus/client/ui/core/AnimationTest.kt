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

    @Test
    fun `double containment and directional inset preserve rectangle semantics`() {
        val bounds = Bounds(10, 20, 30, 40)

        assertTrue(bounds.contains(10.9, 20.9))
        assertTrue(!bounds.contains(40.0, 60.0))
        assertEquals(Bounds(12, 23, 26, 34), bounds.inset(horizontal = 2, vertical = 3))
        assertEquals(Bounds(11, 22, 26, 34), bounds.shrink(left = 1, top = 2, right = 3, bottom = 4))
    }

    @Test
    fun `copy helpers resize and move bounds`() {
        val bounds = Bounds(10, 20, 30, 40)

        assertEquals(Bounds(10, 20, 50, 40), bounds.withWidth(50))
        assertEquals(Bounds(10, 20, 30, 60), bounds.withHeight(60))
        assertEquals(Bounds(1, 2, 30, 40), bounds.moveTo(1, 2))
        assertEquals(Bounds(15, 14, 30, 40), bounds.translate(5, -6))
    }

    @Test
    fun `layout cursor advances rows and columns`() {
        val cursor = LayoutCursor(5, 10)

        assertEquals(Bounds(5, 10, 0, 8), cursor.row(8))
        cursor.advance(4)
        cursor.newline(3)

        assertEquals(9, cursor.x)
        assertEquals(21, cursor.y)
    }

    @Test
    fun `interpolation and clamp helpers handle numeric variants`() {
        assertEquals(5f, lerp(0f, 10f, 0.5f))
        assertEquals(5, lerp(0, 9, 0.5f))
        assertEquals(10, clamp(20, 0, 10))
        assertEquals(0.5f, clamp(0.5f, 0f, 1f))
        assertEquals(0.0, clamp(-1.0, 0.0, 1.0))
    }

    @Test
    fun `frame timer resets and caps long frames`() {
        val timer = FrameTimer()
        timer.reset()
        assertTrue(timer.deltaMs() in 0f..100f)
        val lastNanos = FrameTimer::class.java.getDeclaredField("lastNanos").apply { isAccessible = true }
        lastNanos.setLong(timer, System.nanoTime() - 200_000_000L)

        assertEquals(100f, timer.deltaMs())
    }
}
