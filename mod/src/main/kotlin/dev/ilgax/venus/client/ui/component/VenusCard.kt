package dev.ilgax.venus.client.ui.component

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Card — surface panel with optional title and border. Compact, no huge empty
 * boxes. Content is rendered by the caller via [renderContent].
 */
class VenusCard(
    val bounds: Bounds,
) {
    var title: String? = null

    inline fun render(
        g: GuiGraphics,
        font: Font,
        renderContent: (contentBounds: Bounds) -> Unit,
    ) {
        VenusDraw.rect(g, bounds, VenusTheme.SURFACE)
        VenusDraw.border(g, bounds, VenusTheme.BORDER)
        var contentY = bounds.y + VenusDimensions.CARD_PADDING
        title?.let {
            VenusDraw.text(g, font, it, bounds.x + VenusDimensions.CARD_PADDING, contentY, VenusTheme.TEXT, false)
            contentY += font.lineHeight + VenusDimensions.SECTION_TITLE_GAP
            VenusDraw.hSeparator(
                g,
                bounds.x + VenusDimensions.CARD_PADDING,
                contentY - 3,
                bounds.width - VenusDimensions.CARD_PADDING * 2,
                VenusTheme.BORDER,
            )
        }
        renderContent(
            Bounds(
                bounds.x + VenusDimensions.CARD_PADDING,
                contentY,
                bounds.width - VenusDimensions.CARD_PADDING * 2,
                bounds.bottom - contentY - VenusDimensions.CARD_PADDING,
            ),
        )
    }
}

/**
 * Section — titled group within a page. Lighter than a card (no fill) but with
 * a title and separator.
 */
class VenusSection(
    val bounds: Bounds,
    val title: String,
) {
    inline fun render(
        g: GuiGraphics,
        font: Font,
        renderContent: (contentBounds: Bounds) -> Unit,
    ) {
        VenusDraw.text(g, font, title, bounds.x, bounds.y, VenusTheme.TEXT_MUTED, false)
        val sepY = bounds.y + font.lineHeight + 2
        VenusDraw.hSeparator(g, bounds.x, sepY, bounds.width, VenusTheme.BORDER)
        renderContent(
            Bounds(
                bounds.x,
                sepY + VenusDimensions.SECTION_TITLE_GAP,
                bounds.width,
                bounds.bottom - sepY - VenusDimensions.SECTION_TITLE_GAP,
            ),
        )
    }
}

/**
 * Compact metric card for the dashboard: label, big value, optional accent.
 */
class VenusMetricCard(
    var bounds: Bounds,
) {
    var label: String = ""
    var value: String = ""
    var accent: Int = VenusTheme.ACCENT
    var subtext: String? = null

    fun render(
        g: GuiGraphics,
        font: Font,
    ) {
        VenusDraw.rect(g, bounds, VenusTheme.SURFACE)
        VenusDraw.border(g, bounds, VenusTheme.BORDER)
        VenusDraw.rect(g, bounds.x, bounds.y, 2, bounds.height, accent)
        val pad = VenusDimensions.CARD_PADDING
        VenusDraw.text(g, font, label, bounds.x + pad + 2, bounds.y + pad, VenusTheme.TEXT_MUTED, false)
        VenusDraw.text(g, font, value, bounds.x + pad + 2, bounds.y + pad + font.lineHeight + 2, VenusTheme.TEXT, false)
        subtext?.let {
            VenusDraw.text(g, font, it, bounds.x + pad + 2, bounds.bottom - pad - font.lineHeight, VenusTheme.TEXT_MUTED, false)
        }
    }
}

/**
 * Status indicator — dot + label. Semantic color plus text so it's accessible
 * to color-blind users.
 */
class VenusStatusIndicator(
    val x: Int,
    val y: Int,
    val label: String,
    val color: Int,
) {
    fun render(
        g: GuiGraphics,
        font: Font,
    ) {
        VenusDraw.statusDot(g, x, y + (font.lineHeight - 6) / 2, color)
        VenusDraw.text(g, font, label, x + 10, y, color, false)
    }
}
