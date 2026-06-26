package dev.ilgax.venus.client.ui.component

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusPage
import dev.ilgax.venus.client.ui.core.VenusSpacing
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import dev.ilgax.venus.client.ui.widget.VenusIconButton
import dev.ilgax.venus.client.ui.widget.VenusSidebarItem
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Sidebar navigation. Renders all [VenusSidebarItem]s and reports clicks.
 * The screen owns the active-page state; this component is a passive renderer
 * + hit tester.
 */
class VenusSidebar(
    val bounds: Bounds,
) {
    val items: List<VenusSidebarItem> =
        listOf(
            VenusSidebarItem(VenusPage.DASHBOARD, "Dashboard", VenusIconButton.IconGlyph.REFRESH),
            VenusSidebarItem(VenusPage.PLAYERS, "Players", VenusIconButton.IconGlyph.SEARCH),
            VenusSidebarItem(VenusPage.CONSOLE, "Console", VenusIconButton.IconGlyph.PLUS),
            VenusSidebarItem(VenusPage.AUTH, "Auth", VenusIconButton.IconGlyph.CHECK),
            VenusSidebarItem(VenusPage.SETTINGS, "Settings", VenusIconButton.IconGlyph.SETTINGS),
        )

    private val itemHeight = VenusDimensions.ROW_HEIGHT + 4

    fun itemBounds(index: Int): Bounds {
        val y = bounds.y + VenusSpacing.LG + index * itemHeight
        return Bounds(bounds.x + VenusSpacing.SM, y, bounds.width - VenusSpacing.SM * 2, itemHeight)
    }

    fun render(
        g: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        activePage: VenusPage,
    ) {
        VenusDraw.rect(g, bounds, VenusTheme.SIDEBAR)
        VenusDraw.vSeparator(g, bounds.right - 1, bounds.y, bounds.height, VenusTheme.BORDER)
        items.forEachIndexed { i, item ->
            item.layout(itemBounds(i))
            item.render(g, font, mouseX, mouseY, activePage == item.page)
        }
    }

    fun hitTest(
        mouseX: Int,
        mouseY: Int,
    ): VenusPage? {
        items.forEachIndexed { i, _ ->
            if (itemBounds(i).contains(mouseX, mouseY)) return items[i].page
        }
        return null
    }
}
