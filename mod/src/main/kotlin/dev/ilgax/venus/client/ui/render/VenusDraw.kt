package dev.ilgax.venus.client.ui.render

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusSpacing
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.core.lerp
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

/**
 * Reusable native Minecraft drawing helpers for the Venus UI kit.
 * Every method is stateless and allocation-free; callers pass a [GuiGraphics]
 * and integer coordinates. No per-frame object creation.
 */
object VenusDraw {
    fun rect(
        g: GuiGraphics,
        bounds: Bounds,
        color: Int,
    ) {
        g.fill(bounds.x, bounds.y, bounds.right, bounds.bottom, color)
    }

    fun rect(
        g: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        g.fill(x, y, x + width, y + height, color)
    }

    fun border(
        g: GuiGraphics,
        bounds: Bounds,
        color: Int = VenusTheme.BORDER,
    ) {
        g.renderOutline(bounds.x, bounds.y, bounds.width, bounds.height, color)
    }

    fun border(
        g: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int = VenusTheme.BORDER,
    ) {
        g.renderOutline(x, y, width, height, color)
    }

    fun panel(
        g: GuiGraphics,
        bounds: Bounds,
        fill: Int = VenusTheme.SURFACE,
        border: Int = VenusTheme.BORDER,
    ) {
        rect(g, bounds, fill)
        border(g, bounds, border)
    }

    /**
     * Horizontal separator line, 1px tall.
     */
    fun hSeparator(
        g: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        color: Int = VenusTheme.BORDER,
    ) {
        g.fill(x, y, x + width, y + 1, color)
    }

    fun vSeparator(
        g: GuiGraphics,
        x: Int,
        y: Int,
        height: Int,
        color: Int = VenusTheme.BORDER,
    ) {
        g.fill(x, y, x + 1, y + height, color)
    }

    /**
     * Subtle vertical gradient. Used sparingly for top bars / headers, never
     * as decorative clutter.
     */
    fun vGradient(
        g: GuiGraphics,
        bounds: Bounds,
        top: Int,
        bottom: Int,
    ) {
        g.fillGradient(bounds.x, bounds.y, bounds.right, bounds.bottom, top, bottom)
    }

    /**
     * Status dot — small filled square (pixel-friendly, not a circle).
     */
    fun statusDot(
        g: GuiGraphics,
        x: Int,
        y: Int,
        color: Int,
        size: Int = 6,
    ) {
        rect(g, x, y, size, size, color)
    }

    /**
     * Horizontal progress bar. [progress] in 0..1.
     */
    fun progressBar(
        g: GuiGraphics,
        bounds: Bounds,
        progress: Float,
        fill: Int = VenusTheme.ACCENT,
        track: Int = VenusTheme.RAISED,
    ) {
        rect(g, bounds, track)
        border(g, bounds, VenusTheme.BORDER)
        val p = progress.coerceIn(0f, 1f)
        val fillW = (bounds.width * p).toInt().coerceAtLeast(0)
        if (fillW > 0) {
            rect(g, bounds.x, bounds.y, fillW, bounds.height, fill)
        }
    }

    /**
     * Ping/connection bar — N segments lit by [bars] (0..[segments]).
     */
    fun pingBars(
        g: GuiGraphics,
        x: Int,
        y: Int,
        bars: Int,
        segments: Int = 5,
        segmentWidth: Int = 3,
        gap: Int = 1,
        fullHeight: Int = 10,
        onColor: Int = VenusTheme.SUCCESS,
        offColor: Int = VenusTheme.TEXT_DISABLED,
    ) {
        val lit = bars.coerceIn(0, segments)
        for (i in 0 until segments) {
            val segX = x + i * (segmentWidth + gap)
            val segH = (fullHeight * (i + 1) / segments).coerceAtLeast(2)
            val segY = y + (fullHeight - segH)
            val color = if (i < lit) onColor else offColor
            rect(g, segX, segY, segmentWidth, segH, color)
        }
    }

    /**
     * Focus outline drawn around a widget when it holds keyboard focus.
     */
    fun focusOutline(
        g: GuiGraphics,
        bounds: Bounds,
        color: Int = VenusTheme.ACCENT,
    ) {
        g.renderOutline(bounds.x - 1, bounds.y - 1, bounds.width + 2, bounds.height + 2, color)
    }

    /**
     * Hover overlay blended at [t] (0..1) — used by widgets for fade in.
     */
    fun hoverOverlay(
        g: GuiGraphics,
        bounds: Bounds,
        t: Float,
        color: Int = VenusTheme.HOVER,
    ) {
        if (t <= 0f) return
        val alpha = (t * 255).toInt().coerceIn(0, 255)
        rect(g, bounds, (alpha shl 24) or (color and 0x00FFFFFF))
    }

    fun text(
        g: GuiGraphics,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        color: Int = VenusTheme.TEXT,
        shadow: Boolean = false,
    ) {
        g.drawString(font, text, x, y, color, shadow)
    }

    fun text(
        g: GuiGraphics,
        font: Font,
        text: Component,
        x: Int,
        y: Int,
        color: Int = VenusTheme.TEXT,
        shadow: Boolean = false,
    ) {
        g.drawString(font, text, x, y, color, shadow)
    }

    fun textCentered(
        g: GuiGraphics,
        font: Font,
        text: String,
        bounds: Bounds,
        color: Int = VenusTheme.TEXT,
        shadow: Boolean = false,
    ) {
        val w = font.width(text)
        g.drawString(font, text, bounds.x + (bounds.width - w) / 2, bounds.y + (bounds.height - font.lineHeight) / 2, color, shadow)
    }

    fun textCenteredX(
        g: GuiGraphics,
        font: Font,
        text: String,
        centerX: Int,
        y: Int,
        color: Int = VenusTheme.TEXT,
        shadow: Boolean = false,
    ) {
        val w = font.width(text)
        g.drawString(font, text, centerX - w / 2, y, color, shadow)
    }

    /**
     * Left-aligned text truncated with ellipsis if it exceeds [maxWidth].
     */
    fun textTruncated(
        g: GuiGraphics,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        maxWidth: Int,
        color: Int = VenusTheme.TEXT,
        shadow: Boolean = false,
    ): String {
        val fullW = font.width(text)
        if (fullW <= maxWidth) {
            g.drawString(font, text, x, y, color, shadow)
            return text
        }
        val ellipsis = "..."
        val ellipsisW = font.width(ellipsis)
        val limit = maxWidth - ellipsisW
        if (limit <= 0) {
            g.drawString(font, ellipsis, x, y, color, shadow)
            return ellipsis
        }
        var end = text.length
        while (end > 0 && font.width(text.substring(0, end)) > limit) end--
        val truncated = text.substring(0, end) + ellipsis
        g.drawString(font, truncated, x, y, color, shadow)
        return truncated
    }

    /**
     * Right-aligned text.
     */
    fun textRight(
        g: GuiGraphics,
        font: Font,
        text: String,
        rightX: Int,
        y: Int,
        color: Int = VenusTheme.TEXT,
        shadow: Boolean = false,
    ) {
        val w = font.width(text)
        g.drawString(font, text, rightX - w, y, color, shadow)
    }

    /**
     * Interpolated color for hover/press fades. [from]→[to] by [t] (0..1) on
     * RGB channels; alpha taken from [to].
     */
    fun blendColor(
        from: Int,
        to: Int,
        t: Float,
    ): Int {
        val tt = t.coerceIn(0f, 1f)
        val r = lerp((from shr 16) and 0xFF, (to shr 16) and 0xFF, tt)
        val gr = lerp((from shr 8) and 0xFF, (to shr 8) and 0xFF, tt)
        val b = lerp(from and 0xFF, to and 0xFF, tt)
        val a = (to shr 24) and 0xFF
        return (a shl 24) or (r shl 16) or (gr shl 8) or b
    }

    /**
     * Standard tooltip rectangle + text drawn at [x], [y]. Caller ensures the
     * tooltip fits on screen (the screen clips to its bounds anyway).
     */
    fun tooltip(
        g: GuiGraphics,
        font: Font,
        text: String,
        x: Int,
        y: Int,
    ) {
        val pad = VenusSpacing.SM
        val w = font.width(text) + pad * 2
        val h = font.lineHeight + pad * 2
        val tx = x.coerceAtLeast(0)
        val ty = (y - h).coerceAtLeast(0)
        rect(g, tx, ty, w, h, VenusTheme.RAISED)
        border(g, tx, ty, w, h, VenusTheme.BORDER_BRIGHT)
        g.drawString(font, text, tx + pad, ty + pad, VenusTheme.TEXT, false)
    }

    fun tooltip(
        g: GuiGraphics,
        font: Font,
        lines: List<String>,
        x: Int,
        y: Int,
    ) {
        if (lines.isEmpty()) return
        val pad = VenusSpacing.SM
        val w = (lines.maxOf { font.width(it) }) + pad * 2
        val h = font.lineHeight * lines.size + pad * 2
        val tx = x.coerceAtLeast(0)
        val ty = (y - h).coerceAtLeast(0)
        rect(g, tx, ty, w, h, VenusTheme.RAISED)
        border(g, tx, ty, w, h, VenusTheme.BORDER_BRIGHT)
        lines.forEachIndexed { i, line ->
            g.drawString(font, line, tx + pad, ty + pad + i * font.lineHeight, VenusTheme.TEXT, false)
        }
    }
}
