package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.core.Animation
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.core.lerp
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Toggle switch. Clear on/off, keyboard accessible (Enter/Space), subtle
 * animated knob via delta-time [Animation]. No frame-rate dependency.
 */
class VenusToggle(
    x: Int = 0,
    y: Int = 0,
    width: Int = VenusDimensions.TOGGLE_WIDTH,
    height: Int = VenusDimensions.TOGGLE_HEIGHT,
    initial: Boolean = false,
    private val onChange: (Boolean) -> Unit,
) : VenusWidget(x, y, width, height, Component.empty()) {
    var on: Boolean = initial
        private set

    private val knob = Animation(VenusDimensions.ANIM_TOGGLE_MS)

    init {
        knob.setImmediate(if (initial) 1f else 0f)
    }

    fun set(value: Boolean) {
        if (on == value) return
        on = value
        knob.target = if (value) 1f else 0f
        onChange(value)
    }

    override fun drawVenus(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        knob.target = if (on) 1f else 0f
        knob.tickFrame()

        val trackColor =
            if (on) {
                VenusDraw.blendColor(VenusTheme.RAISED, VenusTheme.ACCENT, knob.value)
            } else {
                VenusDraw.blendColor(VenusTheme.ACCENT, VenusTheme.RAISED, 1f - knob.value)
            }
        VenusDraw.rect(g, bounds, trackColor)
        VenusDraw.border(g, bounds, if (on) VenusTheme.ACCENT else VenusTheme.BORDER_BRIGHT)

        val knobSize = height - 4
        val travel = width - knobSize - 4
        val kx = bounds.x + 2 + lerp(0, travel, knob.value)
        val ky = bounds.y + 2
        VenusDraw.rect(g, kx, ky, knobSize, knobSize, VenusTheme.TEXT)

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
            set(!on)
            return true
        }
        return false
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (!isFocused) return false
        if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_SPACE) {
            set(!on)
            return true
        }
        return false
    }
}
