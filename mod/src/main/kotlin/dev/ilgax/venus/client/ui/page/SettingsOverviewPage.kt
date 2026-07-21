package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.UiThemeRuntime
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

class SettingsOverviewPage(
    private val profileName: () -> String,
    private val onCustomize: () -> Unit,
) : VenusPageContract {
    private var bounds = Bounds(0, 0, 0, 0)
    private var customizeBounds = Bounds(0, 0, 0, 0)

    override fun layout(contentBounds: Bounds) {
        bounds = contentBounds.inset(12)
        customizeBounds = Bounds(bounds.x, bounds.y + 54, minOf(160, bounds.width), 22)
    }

    override fun render(
        g: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        VenusDraw.text(g, font, "UI Profile", bounds.x, bounds.y, VenusTheme.TEXT_MUTED, false)
        VenusDraw.text(g, font, profileName(), bounds.x, bounds.y + 16, VenusTheme.TEXT, false)
        val warnings = UiThemeRuntime.contrastWarnings(UiThemeRuntime.current)
        val warningText = if (warnings.isEmpty()) "Theme readability looks good" else warnings.first()
        VenusDraw.text(
            g,
            font,
            warningText,
            bounds.x,
            bounds.y + 34,
            if (warnings.isEmpty()) VenusTheme.SUCCESS else VenusTheme.WARNING,
            false,
        )
        val hovered = customizeBounds.contains(mouseX, mouseY)
        VenusDraw.rect(g, customizeBounds, if (hovered) VenusTheme.HOVER else VenusTheme.RAISED)
        VenusDraw.border(g, customizeBounds, VenusTheme.BORDER)
        VenusDraw.textCentered(g, font, "Customize UI", customizeBounds, VenusTheme.TEXT, false)
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        if (button != 0 || !customizeBounds.contains(mouseX, mouseY)) return false
        onCustomize()
        return true
    }
}
