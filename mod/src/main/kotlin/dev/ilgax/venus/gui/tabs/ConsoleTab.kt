package dev.ilgax.venus.gui.tabs

import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.max

object ConsoleTab {
    fun render(
        guiGraphics: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        scrollOffset: Int,
        selectedStart: Int?,
        selectedEnd: Int?,
    ) {
        guiGraphics.fill(x, y, x + width, y + height, COLOR_CONSOLE)
        guiGraphics.renderOutline(x, y, width, height, COLOR_BORDER)

        val lines = SessionState.consoleLines
        if (lines.isEmpty()) {
            guiGraphics.drawString(font, "Waiting for console output...", x + 10, y + 10, COLOR_MUTED, false)
            return
        }

        val lineHeight = font.lineHeight + 2
        val maxVisibleLines = max(1, (height - 20) / lineHeight)
        val maxScroll = max(0, lines.size - maxVisibleLines)
        val currentScroll = scrollOffset.coerceIn(0, maxScroll)
        val visibleLines =
            lines
                .dropLast(currentScroll)
                .takeLast(maxVisibleLines)
        val firstVisibleLine = lines.size - currentScroll - visibleLines.size
        val selectionStart = minOf(selectedStart ?: -1, selectedEnd ?: -1)
        val selectionEnd = maxOf(selectedStart ?: -1, selectedEnd ?: -1)
        var lineY = y + 10

        visibleLines.forEachIndexed { index, line ->
            val lineIndex = firstVisibleLine + index
            if (lineIndex in selectionStart..selectionEnd) {
                guiGraphics.fill(x + 6, lineY - 1, x + width - 12, lineY + lineHeight - 1, COLOR_SELECTED)
            }
            guiGraphics.drawString(font, trimLine(font, line, width - 28), x + 10, lineY, COLOR_TEXT, false)
            lineY += lineHeight
        }

        renderScrollbar(guiGraphics, x, y, width, height, maxVisibleLines, lines.size, currentScroll)
    }

    private fun trimLine(
        font: Font,
        line: String,
        maxWidth: Int,
    ): String {
        if (font.width(line) <= maxWidth) return line

        var trimmed = line
        while (trimmed.isNotEmpty() && font.width("$trimmed...") > maxWidth) {
            trimmed = trimmed.dropLast(1)
        }
        return "$trimmed..."
    }

    private fun renderScrollbar(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        visibleLines: Int,
        totalLines: Int,
        scrollOffset: Int,
    ) {
        if (totalLines <= visibleLines) return

        val trackX = x + width - 8
        val trackY = y + 8
        val trackHeight = height - 16
        val thumbHeight = max(12, trackHeight * visibleLines / totalLines)
        val maxThumbTravel = trackHeight - thumbHeight
        val maxScroll = max(1, totalLines - visibleLines)
        val thumbY = trackY + maxThumbTravel - (maxThumbTravel * scrollOffset / maxScroll)

        guiGraphics.fill(trackX, trackY, trackX + 2, trackY + trackHeight, COLOR_SCROLL_TRACK)
        guiGraphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, COLOR_SCROLL_THUMB)
    }

    private const val COLOR_CONSOLE = 0xFF05070A.toInt()
    private const val COLOR_BORDER = 0xFF2B3542.toInt()
    private const val COLOR_TEXT = 0xFFE6EDF3.toInt()
    private const val COLOR_MUTED = 0xFF687482.toInt()
    private const val COLOR_SELECTED = 0xFF1F4E6A.toInt()
    private const val COLOR_SCROLL_TRACK = 0xFF121820.toInt()
    private const val COLOR_SCROLL_THUMB = 0xFF3A4A5B.toInt()
}
