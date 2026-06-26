package dev.ilgax.venus.client.ui.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class SliderMathTest {
    @Test
    fun `clamps to step increments`() {
        assertEquals(10.0, SliderMath.clampToStep(10.3, 0.0, 100.0, 5.0))
        assertEquals(15.0, SliderMath.clampToStep(13.0, 0.0, 100.0, 5.0))
        assertEquals(0.0, SliderMath.clampToStep(-5.0, 0.0, 100.0, 5.0))
        assertEquals(100.0, SliderMath.clampToStep(105.0, 0.0, 100.0, 5.0))
    }

    @Test
    fun `handles step larger than range`() {
        assertEquals(0.0, SliderMath.clampToStep(1.0, 0.0, 10.0, 20.0))
        assertEquals(10.0, SliderMath.clampToStep(15.0, 0.0, 10.0, 20.0))
    }

    @Test
    fun `progress is zero at min and one at max`() {
        assertEquals(0f, SliderMath.progress(0.0, 0.0, 100.0))
        assertEquals(1f, SliderMath.progress(100.0, 0.0, 100.0))
        assertEquals(0.5f, SliderMath.progress(50.0, 0.0, 100.0))
    }

    @Test
    fun `progress is zero when range is zero`() {
        assertEquals(0f, SliderMath.progress(5.0, 5.0, 5.0))
    }

    @Test
    fun `progress clamps out of range`() {
        assertEquals(1f, SliderMath.progress(150.0, 0.0, 100.0))
        assertEquals(0f, SliderMath.progress(-10.0, 0.0, 100.0))
    }
}
