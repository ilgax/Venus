package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Tooltip renderer — passive visual, not a widget. Drawn at mouse position by
 * the screen when a hovered widget has tooltip text.
 */
object VenusTooltip {
    fun render(
        g: GuiGraphics,
        font: Font,
        text: String,
        mouseX: Int,
        mouseY: Int,
    ) {
        VenusDraw.tooltip(g, font, text, mouseX + 8, mouseY - 4)
    }

    fun render(
        g: GuiGraphics,
        font: Font,
        lines: List<String>,
        mouseX: Int,
        mouseY: Int,
    ) {
        VenusDraw.tooltip(g, font, lines, mouseX + 8, mouseY - 4)
    }
}
