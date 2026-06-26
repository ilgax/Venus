package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.core.Animation
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Compact square button with an icon (placeholder glyph) instead of text.
 * Tooltip is mandatory for icon-only buttons per the accessibility spec.
 */
class VenusIconButton(
    x: Int = 0,
    y: Int = 0,
    size: Int = VenusDimensions.ICON + 4,
    private val icon: IconGlyph,
    tooltipText: String,
    private val onPressed: () -> Unit,
) : VenusWidget(x, y, size, size, Component.empty()) {
    private val hover = Animation(VenusDimensions.ANIM_HOVER_MS)
    private var pressed = false

    enum class IconGlyph {
        CLOSE,
        REFRESH,
        BACK,
        SEARCH,
        CLEAR,
        SETTINGS,
        PLUS,
        MINUS,
        CHECK,
        X,
        PAUSE,
        PLAY,
    }

    init {
        super.tooltipText = tooltipText
        active = true
    }

    override fun drawVenus(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        hover.target = if (isHovered) 1f else 0f
        hover.tickFrame()

        val bg = VenusDraw.blendColor(VenusTheme.RAISED, VenusTheme.HOVER, hover.value)
        VenusDraw.rect(g, bounds, bg)
        VenusDraw.border(g, bounds, if (isHovered) VenusTheme.BORDER_BRIGHT else VenusTheme.BORDER)
        if (isFocused) VenusDraw.focusOutline(g, bounds, VenusTheme.ACCENT)

        val cx = bounds.centerX
        val cy = bounds.centerY
        val color = if (pressed) VenusTheme.ACCENT else VenusTheme.TEXT
        drawGlyph(g, icon, cx, cy, color)
    }

    private fun drawGlyph(
        g: GuiGraphics,
        glyph: IconGlyph,
        cx: Int,
        cy: Int,
        color: Int,
    ) {
        val s = 6
        when (glyph) {
            IconGlyph.CLOSE, IconGlyph.X -> {
                g.fill(cx - s, cy - s, cx + s, cy - s + 1, color)
                g.fill(cx - s, cy + s - 1, cx + s, cy + s, color)
                g.fill(cx - s, cy - s, cx - s + 1, cy + s, color)
                g.fill(cx + s - 1, cy - s, cx + s, cy + s, color)
            }
            IconGlyph.CHECK -> {
                g.fill(cx - s, cy, cx - s + 2, cy + 1, color)
                g.fill(cx - 2, cy + 2, cx, cy + 3, color)
                g.fill(cx, cy - 2, cx + 2, cy - 1, color)
            }
            IconGlyph.REFRESH -> {
                g.fill(cx - s, cy - s, cx + s, cy - s + 1, color)
                g.fill(cx - s, cy + s - 1, cx + s, cy + s, color)
                g.fill(cx - s, cy - s, cx - s + 1, cy + s, color)
                g.fill(cx + s - 1, cy - s, cx + s, cy + s, color)
            }
            IconGlyph.BACK -> {
                g.fill(cx - 3, cy - 4, cx - 2, cy + 5, color)
                g.fill(cx - 5, cy - 2, cx - 2, cy - 1, color)
                g.fill(cx - 5, cy + 1, cx - 2, cy + 2, color)
            }
            IconGlyph.SEARCH -> {
                g.fill(cx - 4, cy - 5, cx + 3, cy - 4, color)
                g.fill(cx - 5, cy - 4, cx - 4, cy + 3, color)
                g.fill(cx + 2, cy - 4, cx + 3, cy + 3, color)
                g.fill(cx - 4, cy + 2, cx + 3, cy + 3, color)
                g.fill(cx + 3, cy + 3, cx + 6, cy + 4, color)
                g.fill(cx + 4, cy + 4, cx + 6, cy + 6, color)
            }
            IconGlyph.CLEAR -> {
                drawGlyph(g, IconGlyph.X, cx, cy, color)
            }
            IconGlyph.SETTINGS -> {
                g.fill(cx - s, cy - 1, cx + s, cy + 1, color)
                g.fill(cx - 1, cy - s, cx + 1, cy + s, color)
                g.fill(cx - 3, cy - 3, cx - 1, cy - 1, color)
                g.fill(cx + 1, cy + 1, cx + 3, cy + 3, color)
            }
            IconGlyph.PLUS -> {
                g.fill(cx - 4, cy - 1, cx + 4, cy + 1, color)
                g.fill(cx - 1, cy - 4, cx + 1, cy + 4, color)
            }
            IconGlyph.MINUS -> {
                g.fill(cx - 4, cy - 1, cx + 4, cy + 1, color)
            }
            IconGlyph.PAUSE -> {
                g.fill(cx - 3, cy - 4, cx - 1, cy + 4, color)
                g.fill(cx + 1, cy - 4, cx + 3, cy + 4, color)
            }
            IconGlyph.PLAY -> {
                g.fill(cx - 2, cy - 4, cx - 1, cy + 4, color)
                g.fill(cx - 1, cy - 3, cx, cy + 3, color)
                g.fill(cx, cy - 2, cx + 1, cy + 2, color)
                g.fill(cx + 1, cy - 1, cx + 2, cy + 1, color)
                g.fill(cx + 2, cy, cx + 3, cy, color)
            }
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
            pressed = true
            return true
        }
        return false
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (!visible || !active) return false
        val mouseX = mouseButtonEvent.x().toInt()
        val mouseY = mouseButtonEvent.y().toInt()
        if (pressed && mouseButtonEvent.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressed = false
            if (bounds.contains(mouseX, mouseY)) {
                onPressed()
                return true
            }
        }
        return false
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (!isFocused) return false
        if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_SPACE) {
            onPressed()
            return true
        }
        return false
    }
}
