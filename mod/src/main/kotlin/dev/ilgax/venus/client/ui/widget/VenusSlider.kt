package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.core.clamp
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Slider with min/max/step/current, keyboard control (left/right), dragging,
 * and formatted value display. Safe clamping via [clamp].
 *
 * Pure clamping logic is extracted into [SliderMath] so it can be unit-tested
 * without a Minecraft client.
 */
class VenusSlider(
    x: Int = 0,
    y: Int = 0,
    width: Int,
    height: Int = VenusDimensions.SLIDER_HEIGHT,
    private val min: Double,
    private val max: Double,
    private val step: Double,
    initial: Double,
    private val valueFormatter: (Double) -> String = { String.format("%.1f", it) },
    private val onChange: (Double) -> Unit,
) : VenusWidget(x, y, width, height, Component.empty()) {
    var value: Double = SliderMath.clampToStep(initial, min, max, step)
        private set

    private var dragging = false

    fun set(v: Double) {
        val clamped = SliderMath.clampToStep(v, min, max, step)
        if (clamped == value) return
        value = clamped
        onChange(value)
    }

    override fun drawVenus(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val track = bounds.inset(horizontal = 2, vertical = bounds.height / 2 - 1)
        VenusDraw.rect(g, track, VenusTheme.RAISED)
        VenusDraw.border(g, track, VenusTheme.BORDER)

        val progress = SliderMath.progress(value, min, max)
        val fillW = (track.width * progress).toInt()
        if (fillW > 0) {
            VenusDraw.rect(g, track.x, track.y, fillW, track.height, VenusTheme.ACCENT)
        }

        val knobX = track.x + fillW - 2
        val knobY = bounds.y + 2
        VenusDraw.rect(g, knobX, knobY, 4, bounds.height - 4, VenusTheme.TEXT)

        val label = valueFormatter(value)
        val font =
            net.minecraft.client.Minecraft
                .getInstance()
                .font
        VenusDraw.text(g, font, label, bounds.x, bounds.y - font.lineHeight - 1, VenusTheme.TEXT_MUTED, false)

        if (isFocused) {
            VenusDraw.focusOutline(g, bounds, VenusTheme.ACCENT)
        }
    }

    override fun mouseClicked(
        mouseButtonEvent: MouseButtonEvent,
        doubleClick: Boolean,
    ): Boolean {
        if (!visible || !active) return false
        val mouseX = mouseButtonEvent.x().toInt()
        val mouseY = mouseButtonEvent.y().toInt()
        if (!bounds.contains(mouseX, mouseY)) return false
        if (mouseButtonEvent.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = true
            setFromMouse(mouseX.toDouble())
            return true
        }
        return false
    }

    override fun mouseDragged(
        mouseButtonEvent: MouseButtonEvent,
        dragX: Double,
        dragY: Double,
    ): Boolean {
        if (!dragging || mouseButtonEvent.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        setFromMouse(mouseButtonEvent.x())
        return true
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (dragging && mouseButtonEvent.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false
            return true
        }
        return false
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (!isFocused) return false
        when (keyEvent.key()) {
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> {
                set(value - step)
                return true
            }
            GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> {
                set(value + step)
                return true
            }
        }
        return false
    }

    private fun setFromMouse(mouseX: Double) {
        val track = bounds.inset(horizontal = 2, vertical = 0)
        val ratio = ((mouseX.toInt() - track.x).toDouble() / track.width).coerceIn(0.0, 1.0)
        set(min + (max - min) * ratio)
    }
}

/**
 * Pure slider math — unit-testable without Minecraft.
 */
object SliderMath {
    fun clampToStep(
        value: Double,
        min: Double,
        max: Double,
        step: Double,
    ): Double {
        val safeStep = if (step <= 0) max - min else step
        val n = ((value - min) / safeStep).roundToInt()
        val stepped = min + n * safeStep
        return clamp(stepped, min, max)
    }

    fun progress(
        value: Double,
        min: Double,
        max: Double,
    ): Float {
        val range = max - min
        if (range <= 0) return 0f
        return ((value - min) / range).toFloat().coerceIn(0f, 1f)
    }

    private fun Double.roundToInt(): Int = Math.round(this).toInt()
}
