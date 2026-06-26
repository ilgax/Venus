package dev.ilgax.venus.client.ui.component

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Empty state — centered muted message + optional subtext.
 */
class VenusEmptyState(
    val bounds: Bounds,
) {
    var message: String = "Nothing here"
    var subtext: String? = null

    fun render(
        g: GuiGraphics,
        font: Font,
    ) {
        VenusDraw.textCentered(g, font, message, bounds, VenusTheme.TEXT_MUTED, false)
        subtext?.let {
            val sub = Bounds(bounds.x, bounds.y + font.lineHeight + 2, bounds.width, bounds.height - font.lineHeight - 2)
            VenusDraw.textCentered(g, font, it, sub, VenusTheme.TEXT_DISABLED, false)
        }
    }
}

/**
 * Loading state — centered "Loading..." with an animated pulse dot.
 */
class VenusLoadingState(
    val bounds: Bounds,
) {
    var message: String = "Loading..."

    fun render(
        g: GuiGraphics,
        font: Font,
        pulse: Float,
    ) {
        val dotSize = (4 + pulse * 4).toInt()
        val cx = bounds.centerX
        val cy = bounds.centerY - font.lineHeight - 4
        VenusDraw.rect(g, cx - dotSize / 2, cy - dotSize / 2, dotSize, dotSize, VenusTheme.ACCENT)
        VenusDraw.textCentered(g, font, message, bounds, VenusTheme.TEXT_MUTED, false)
    }
}

/**
 * Error state — centered danger message.
 */
class VenusErrorState(
    val bounds: Bounds,
) {
    var message: String = "Failed to load"

    fun render(
        g: GuiGraphics,
        font: Font,
    ) {
        VenusDraw.textCentered(g, font, message, bounds, VenusTheme.DANGER, false)
    }
}

/**
 * Player head placeholder. Renders a colored square derived from the UUID hash
 * until real skin textures are wired. The texture path is prepared so a real
 * head render can drop in without changing call sites.
 */
class VenusPlayerHead(
    val x: Int,
    val y: Int,
    val uuid: String,
    val size: Int = VenusDimensions.PLAYER_HEAD_SIZE,
) {
    fun render(
        g: GuiGraphics,
        showHeads: Boolean = true,
    ) {
        if (showHeads) {
            VenusDraw.rect(g, x, y, size, size, faceColor(uuid))
            VenusDraw.border(g, x, y, size, size, VenusTheme.BORDER)
        } else {
            VenusDraw.rect(g, x, y, size, size, VenusTheme.RAISED)
            VenusDraw.border(g, x, y, size, size, VenusTheme.BORDER)
        }
    }

    private fun faceColor(uuid: String): Int {
        val hash = uuid.hashCode()
        val r = (hash and 0xFF)
        val g = ((hash shr 8) and 0xFF)
        val b = ((hash shr 16) and 0xFF)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
