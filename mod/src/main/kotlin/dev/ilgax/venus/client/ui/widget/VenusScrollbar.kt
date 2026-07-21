package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

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
        g: net.minecraft.client.gui.GuiGraphics,
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
