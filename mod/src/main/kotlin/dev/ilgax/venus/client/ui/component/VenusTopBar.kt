package dev.ilgax.venus.client.ui.component

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusSpacing
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Top bar — V logo, "VENUS CONTROL" title, server name, connection state,
 * close button. Passive visual; the screen handles the close hit.
 */
class VenusTopBar(
    val bounds: Bounds,
) {
    var serverName: String = ""
    var connected: Boolean = false

    fun render(
        g: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        closeBounds: Bounds,
    ) {
        VenusDraw.rect(g, bounds, VenusTheme.TOP_BAR)
        VenusDraw.hSeparator(g, bounds.x, bounds.bottom - 1, bounds.width, VenusTheme.BORDER)

        // V logo — accent square with "V" letter
        val logoX = bounds.x + VenusSpacing.LG
        val logoY = bounds.y + (bounds.height - 14) / 2
        VenusDraw.rect(g, logoX, logoY, 14, 14, VenusTheme.ACCENT)
        VenusDraw.textCentered(g, font, "V", Bounds(logoX, logoY - 1, 14, 14), VenusTheme.TOP_BAR, false)

        VenusDraw.text(g, font, "VENUS CONTROL", logoX + 22, bounds.y + (bounds.height - font.lineHeight) / 2, VenusTheme.TEXT, false)

        // Right cluster: server name, state
        var rightX = bounds.right - VenusSpacing.LG

        val stateColor = if (connected) VenusTheme.SUCCESS else VenusTheme.DANGER
        val stateText = if (connected) "Connected" else "Offline"
        VenusDraw.statusDot(g, rightX - 8, bounds.y + (bounds.height - 6) / 2, stateColor)
        VenusDraw.textRight(g, font, stateText, rightX - 12, bounds.y + (bounds.height - font.lineHeight) / 2, stateColor, false)
        rightX -= font.width(stateText) + 24

        if (serverName.isNotEmpty()) {
            VenusDraw.textTruncated(
                g,
                font,
                serverName,
                bounds.x + logoX + 22 + font.width("VENUS CONTROL") + VenusSpacing.LG,
                bounds.y + (bounds.height - font.lineHeight) / 2,
                rightX - (bounds.x + 120),
                VenusTheme.TEXT_MUTED,
                false,
            )
        }

        // Close button (X) — drawn here, hit handled by screen
        val closeHovered = closeBounds.contains(mouseX, mouseY)
        VenusDraw.rect(g, closeBounds, if (closeHovered) VenusTheme.HOVER else VenusTheme.TOP_BAR)
        val cx = closeBounds.centerX
        val cy = closeBounds.centerY
        val xColor = if (closeHovered) VenusTheme.DANGER else VenusTheme.TEXT_MUTED
        g.fill(cx - 4, cy - 4, cx + 4, cy - 3, xColor)
        g.fill(cx - 4, cy + 3, cx + 4, cy + 4, xColor)
        g.fill(cx - 4, cy - 4, cx - 3, cy + 4, xColor)
        g.fill(cx + 3, cy - 4, cx + 4, cy + 4, xColor)
    }
}
