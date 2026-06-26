package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

/**
 * Sidebar navigation item. Shows an icon glyph + label, highlights when active
 * or hovered, supports keyboard activation. Not an AbstractWidget because
 * sidebar items are driven by the screen's own input loop (page navigation is
 * a screen-level concern, not a per-widget focus hop).
 */
class VenusSidebarItem(
    val page: dev.ilgax.venus.client.ui.core.VenusPage,
    val label: String,
    val icon: VenusIconButton.IconGlyph,
) {
    var bounds: Bounds = Bounds(0, 0, 0, 0)
        private set

    fun layout(b: Bounds) {
        bounds = b
    }

    fun render(
        g: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        active: Boolean,
    ) {
        val hovered = bounds.contains(mouseX, mouseY)
        val bg =
            when {
                active -> VenusTheme.ACTIVE
                hovered -> VenusTheme.HOVER
                else -> VenusTheme.SIDEBAR
            }
        VenusDraw.rect(g, bounds, bg)
        if (active) {
            VenusDraw.rect(g, bounds.x, bounds.y, 2, bounds.height, VenusTheme.ACCENT)
        }

        val iconColor = if (active) VenusTheme.ACCENT else VenusTheme.TEXT_MUTED
        drawSidebarIcon(g, icon, bounds.x + 8, bounds.y + (bounds.height - 12) / 2, iconColor)

        val textColor = if (active) VenusTheme.TEXT else VenusTheme.TEXT_MUTED
        VenusDraw.text(g, font, label, bounds.x + 28, bounds.y + (bounds.height - font.lineHeight) / 2, textColor, false)
    }

    fun isClicked(
        mouseX: Double,
        mouseY: Double,
    ): Boolean = bounds.contains(mouseX, mouseY)

    private fun drawSidebarIcon(
        g: GuiGraphics,
        glyph: VenusIconButton.IconGlyph,
        x: Int,
        y: Int,
        color: Int,
    ) {
        val s = 5
        when (glyph) {
            VenusIconButton.IconGlyph.SETTINGS -> {
                g.fill(x, y + s, x + s * 2, y + s + 1, color)
                g.fill(x + s, y, x + s + 1, y + s * 2, color)
            }
            VenusIconButton.IconGlyph.SEARCH -> {
                g.fill(x, y, x + s, y + 1, color)
                g.fill(x, y, x + 1, y + s, color)
                g.fill(x + s - 1, y, x + s, y + s, color)
                g.fill(x, y + s - 1, x + s, y + s, color)
            }
            VenusIconButton.IconGlyph.REFRESH -> {
                g.fill(x, y, x + s * 2, y + 1, color)
                g.fill(x, y, x + 1, y + s * 2, color)
                g.fill(x + s * 2 - 1, y, x + s * 2, y + s * 2, color)
                g.fill(x, y + s * 2 - 1, x + s * 2, y + s * 2, color)
            }
            else -> {
                g.fill(x, y, x + s * 2, y + s * 2, color)
            }
        }
    }
}

/**
 * Scrollbar metrics and drag math — pure, unit-testable.
 */
data class ScrollbarMetrics(
    val x: Int,
    val trackY: Int,
    val trackHeight: Int,
    val thumbHeight: Int,
    val maxThumbTravel: Int,
)

object ScrollbarMath {
    fun metrics(
        bounds: Bounds,
        totalItems: Int,
        visibleItems: Int,
    ): ScrollbarMetrics? {
        if (totalItems <= visibleItems) return null
        val trackHeight = bounds.height - 8
        val thumbHeight = (trackHeight * visibleItems / totalItems).coerceAtLeast(VenusDimensions.SCROLLBAR_THUMB_MIN)
        return ScrollbarMetrics(
            x = bounds.right - VenusDimensions.SCROLLBAR_WIDTH - 2,
            trackY = bounds.y + 4,
            trackHeight = trackHeight,
            thumbHeight = thumbHeight,
            maxThumbTravel = (trackHeight - thumbHeight).coerceAtLeast(0),
        )
    }

    fun scrollFromThumb(
        thumbTop: Int,
        metrics: ScrollbarMetrics,
        maxScroll: Int,
    ): Int {
        val clamped = thumbTop.coerceIn(metrics.trackY, metrics.trackY + metrics.maxThumbTravel)
        val ratio = if (metrics.maxThumbTravel == 0) 0f else (clamped - metrics.trackY).toFloat() / metrics.maxThumbTravel
        return (maxScroll * ratio).toInt()
    }

    fun thumbY(
        scrollOffset: Int,
        maxScroll: Int,
        metrics: ScrollbarMetrics,
    ): Int {
        val ratio = if (maxScroll == 0) 0f else scrollOffset.toFloat() / maxScroll
        return metrics.trackY + (metrics.maxThumbTravel * ratio).toInt()
    }
}

/**
 * Scrollbar widget — renders track + thumb and handles drag. Scroll value is
 * owned by the caller (list/table); this widget reports drag deltas.
 */
class VenusScrollbar(
    x: Int = 0,
    y: Int = 0,
    height: Int,
    private val totalItems: () -> Int,
    private val visibleItems: () -> Int,
    private val maxScroll: () -> Int,
    private val getScroll: () -> Int,
    private val setScroll: (Int) -> Unit,
) : VenusWidget(
        x,
        y,
        VenusDimensions.SCROLLBAR_WIDTH,
        height,
        net.minecraft.network.chat.Component
            .empty(),
    ) {
    private var dragging = false

    override fun drawVenus(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val metrics = ScrollbarMath.metrics(bounds, totalItems(), visibleItems()) ?: return
        val thumbY = ScrollbarMath.thumbY(getScroll(), maxScroll(), metrics)
        VenusDraw.rect(g, metrics.x, metrics.trackY, VenusDimensions.SCROLLBAR_WIDTH, metrics.trackHeight, VenusTheme.RAISED)
        VenusDraw.rect(
            g,
            metrics.x,
            thumbY,
            VenusDimensions.SCROLLBAR_WIDTH,
            metrics.thumbHeight,
            if (dragging) VenusTheme.ACCENT else VenusTheme.TEXT_MUTED,
        )
    }

    override fun mouseClicked(
        mouseButtonEvent: MouseButtonEvent,
        doubleClick: Boolean,
    ): Boolean {
        if (!visible || mouseButtonEvent.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        val mouseX = mouseButtonEvent.x().toInt()
        val mouseY = mouseButtonEvent.y().toInt()
        val metrics = ScrollbarMath.metrics(bounds, totalItems(), visibleItems()) ?: return false
        if (mouseX in (metrics.x - 2)..(metrics.x + VenusDimensions.SCROLLBAR_WIDTH + 2) &&
            mouseY in metrics.trackY..(metrics.trackY + metrics.trackHeight)
        ) {
            dragging = true
            setFromMouse(mouseY.toDouble())
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
        setFromMouse(mouseButtonEvent.y())
        return true
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (dragging && mouseButtonEvent.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false
            return true
        }
        return false
    }

    private fun setFromMouse(mouseY: Double) {
        val metrics = ScrollbarMath.metrics(bounds, totalItems(), visibleItems()) ?: return
        val thumbTop = (mouseY.toInt() - metrics.thumbHeight / 2)
        setScroll(ScrollbarMath.scrollFromThumb(thumbTop, metrics, maxScroll()))
    }
}
