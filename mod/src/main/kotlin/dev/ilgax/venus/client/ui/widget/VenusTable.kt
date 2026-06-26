package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.ScissorStack
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Column definition for [VenusTable]. [minWidth] lets the table shrink or hide
 * low-priority columns on narrow layouts via [TableMath].
 */
data class VenusColumn(
    val id: String,
    val label: String,
    val minWidth: Int = 40,
    val preferredWidth: Int = 100,
    val priority: Int = 0,
    val hideable: Boolean = true,
)

/**
 * Scrollable table with header, selectable rows, hover states, and responsive
 * column hiding. No SaaS data-grid look — thin borders, compact rows, cyan
 * accent for selection. Column sizing math is in [TableMath] for unit testing.
 */
class VenusTable(
    val bounds: Bounds,
    val columns: List<VenusColumn>,
    val rowHeight: Int = VenusDimensions.ROW_HEIGHT,
) {
    var scrollOffset: Int = 0
        private set
    var selectedRowIndex: Int = -1
        private set

    private val headerHeight = rowHeight

    fun bodyBounds(): Bounds = Bounds(bounds.x, bounds.y + headerHeight, bounds.width, bounds.height - headerHeight)

    fun visibleRows(): Int = (bodyBounds().height / rowHeight).coerceAtLeast(1)

    fun maxScroll(itemCount: Int): Int = (itemCount - visibleRows()).coerceAtLeast(0)

    fun scroll(
        lines: Int,
        itemCount: Int,
    ) {
        scrollOffset = (scrollOffset + lines).coerceIn(0, maxScroll(itemCount))
    }

    fun select(index: Int) {
        selectedRowIndex = index
    }

    fun rowBounds(index: Int): Bounds {
        val body = bodyBounds()
        val y = body.y + (index - scrollOffset) * rowHeight
        return Bounds(body.x, y, body.width - VenusDimensions.SCROLLBAR_WIDTH - 2, rowHeight)
    }

    fun hitTest(
        mouseX: Int,
        mouseY: Int,
        itemCount: Int,
    ): Int {
        val body = bodyBounds()
        if (!body.contains(mouseX, mouseY)) return -1
        if (mouseX > body.right - VenusDimensions.SCROLLBAR_WIDTH - 2) return -1
        val relative = mouseY - body.y
        val index = scrollOffset + relative / rowHeight
        return if (index in 0 until itemCount) index else -1
    }

    inline fun render(
        g: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        itemCount: Int,
        columnWidths: List<Int>,
        cellText: (row: Int, columnIndex: Int) -> String,
    ) {
        val visible = TableMath.visibleColumns(columns, bounds.width, columnWidths)
        VenusDraw.rect(g, bounds, VenusTheme.SURFACE)
        VenusDraw.border(g, bounds, VenusTheme.BORDER)

        renderHeader(g, font, visible)
        val body = bodyBounds()

        if (itemCount == 0) {
            VenusDraw.textCentered(g, font, "No data", body, VenusTheme.TEXT_MUTED, false)
            return
        }

        ScissorStack.with(g, body) {
            val first = scrollOffset
            val last = minOf(itemCount, scrollOffset + visibleRows() + 1)
            for (i in first until last) {
                val rb = rowBounds(i)
                if (rb.bottom < body.y || rb.y > body.bottom) continue
                val hovered = rb.contains(mouseX, mouseY)
                val selected = i == selectedRowIndex
                val bg =
                    when {
                        selected -> VenusTheme.ACTIVE
                        hovered -> VenusTheme.HOVER
                        else -> VenusTheme.SURFACE
                    }
                VenusDraw.rect(g, rb, bg)
                var cx = rb.x
                visible.forEachIndexed { ci, col ->
                    val text = cellText(i, ci)
                    VenusDraw.textTruncated(
                        g,
                        font,
                        text,
                        cx + 6,
                        rb.y + (rb.height - font.lineHeight) / 2,
                        col.second - 12,
                        VenusTheme.TEXT,
                        false,
                    )
                    cx += col.second
                }
            }
        }
    }

    @PublishedApi
    internal fun renderHeader(
        g: GuiGraphics,
        font: Font,
        visible: List<Pair<VenusColumn, Int>>,
    ) {
        val header = Bounds(bounds.x, bounds.y, bounds.width, headerHeight)
        VenusDraw.rect(g, header, VenusTheme.RAISED)
        VenusDraw.hSeparator(g, header.x, header.bottom - 1, header.width, VenusTheme.BORDER_BRIGHT)
        var cx = header.x
        for ((col, w) in visible) {
            VenusDraw.textTruncated(
                g,
                font,
                col.label,
                cx + 6,
                header.y + (header.height - font.lineHeight) / 2,
                w - 12,
                VenusTheme.TEXT_MUTED,
                false,
            )
            cx += w
        }
    }
}

/**
 * Pure table column math — unit-testable.
 */
object TableMath {
    /**
     * Compute widths for all columns, then drop low-priority hideable columns
     * that don't fit [availableWidth]. Returns visible columns paired with
     * their allocated width.
     */
    fun visibleColumns(
        columns: List<VenusColumn>,
        availableWidth: Int,
        preferredWidths: List<Int>,
    ): List<Pair<VenusColumn, Int>> {
        val totalPreferred = preferredWidths.sum()
        if (totalPreferred <= availableWidth) {
            return columns.zip(preferredWidths)
        }
        val sorted = columns.mapIndexed { i, c -> IndexedValue(i, c) }.sortedBy { it.value.priority }
        val removed = mutableSetOf<Int>()
        var currentWidth = totalPreferred
        for (entry in sorted) {
            if (currentWidth <= availableWidth) break
            if (!entry.value.hideable) continue
            currentWidth -= preferredWidths[entry.index]
            removed.add(entry.index)
        }
        return columns.zip(preferredWidths).filterIndexed { i, _ -> i !in removed }
    }

    /**
     * Distribute [availableWidth] across [columns] by preferred weight, never
     * dropping below [VenusColumn.minWidth]. Unused space is given to the
     * highest-priority column.
     */
    fun distribute(
        columns: List<VenusColumn>,
        availableWidth: Int,
    ): List<Int> {
        val minTotal = columns.sumOf { it.minWidth }
        if (availableWidth <= minTotal) return columns.map { it.minWidth }
        val prefTotal = columns.sumOf { it.preferredWidth }
        if (availableWidth >= prefTotal) {
            val extra = availableWidth - prefTotal
            val result = columns.map { it.preferredWidth }.toMutableList()
            if (result.isNotEmpty()) {
                val top = columns.indices.maxByOrNull { columns[it].priority } ?: 0
                result[top] += extra
            }
            return result
        }
        val aboveMin = availableWidth - minTotal
        val prefAboveMin = prefTotal - minTotal
        return columns.map { c ->
            if (prefAboveMin <= 0) {
                c.minWidth
            } else {
                c.minWidth + (aboveMin * (c.preferredWidth - c.minWidth) / prefAboveMin)
            }
        }
    }
}
