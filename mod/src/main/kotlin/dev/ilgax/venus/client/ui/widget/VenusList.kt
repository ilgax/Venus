package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.ScissorStack
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.GuiGraphics

/**
 * Scrollable list. Clips with scissor, mouse wheel support, stable when
 * content changes, optional empty/loading/error states. No per-item widget
 * allocation — rows are rendered directly in a loop.
 *
 * The list is passive: it renders and reports hits; the owning page owns the
 * selection state and scroll offset. This keeps the list reusable across
 * players, events, and auth requests.
 */
class VenusList(
    val bounds: Bounds,
    val rowHeight: Int = VenusDimensions.ROW_HEIGHT,
) {
    var scrollOffset: Int = 0
        internal set

    fun maxScroll(itemCount: Int): Int {
        val visible = visibleRows()
        return (itemCount - visible).coerceAtLeast(0)
    }

    fun visibleRows(): Int = (bounds.height / rowHeight).coerceAtLeast(1)

    fun clampScroll(itemCount: Int) {
        scrollOffset = scrollOffset.coerceIn(0, maxScroll(itemCount))
    }

    fun scroll(
        lines: Int,
        itemCount: Int,
    ) {
        scrollOffset = (scrollOffset + lines).coerceIn(0, maxScroll(itemCount))
    }

    fun rowBounds(index: Int): Bounds {
        val y = bounds.y + (index - scrollOffset) * rowHeight
        return Bounds(bounds.x, y, bounds.width - VenusDimensions.SCROLLBAR_WIDTH - 2, rowHeight)
    }

    fun hitTest(
        mouseX: Int,
        mouseY: Int,
        itemCount: Int,
    ): Int {
        if (!bounds.contains(mouseX, mouseY)) return -1
        if (mouseX > bounds.right - VenusDimensions.SCROLLBAR_WIDTH - 2) return -1
        val relative = mouseY - bounds.y
        val index = scrollOffset + relative / rowHeight
        return if (index in 0 until itemCount) index else -1
    }

    /**
     * Render the list background and scissor frame. Caller renders rows inside
     * [renderRows] via the provided [renderer] so no per-item widget exists.
     */
    inline fun render(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        itemCount: Int,
        scrollbar: VenusScrollbar?,
        emptyText: String? = null,
        renderer: (index: Int, rowBounds: Bounds, hovered: Boolean) -> Unit,
    ) {
        clampScroll(itemCount)
        VenusDraw.rect(g, bounds, VenusTheme.SURFACE)
        VenusDraw.border(g, bounds, VenusTheme.BORDER)

        if (itemCount == 0 && emptyText != null) {
            VenusDraw.textCentered(
                g,
                net.minecraft.client.Minecraft
                    .getInstance()
                    .font,
                emptyText,
                bounds,
                VenusTheme.TEXT_MUTED,
                false,
            )
            return
        }

        val firstVisible = scrollOffset
        val lastVisible = minOf(itemCount, scrollOffset + visibleRows() + 1)

        ScissorStack.with(g, bounds) {
            for (i in firstVisible until lastVisible) {
                val rb = rowBounds(i)
                if (rb.bottom < bounds.y || rb.y > bounds.bottom) continue
                renderer(i, rb, rb.contains(mouseX, mouseY))
            }
        }

        scrollbar?.renderVenus(g, mouseX, mouseY, 0f)
    }
}

/**
 * Scrollbar helper bound to a [VenusList]'s scroll state.
 */
fun scrollbarForList(
    list: VenusList,
    itemCount: () -> Int,
): VenusScrollbar {
    val height = list.bounds.height
    return VenusScrollbar(
        x = list.bounds.right - VenusDimensions.SCROLLBAR_WIDTH - 2,
        y = list.bounds.y,
        height = height,
        totalItems = itemCount,
        visibleItems = list::visibleRows,
        maxScroll = { list.maxScroll(itemCount()) },
        getScroll = { list.scrollOffset },
        setScroll = { list.scrollOffset = it },
    )
}
