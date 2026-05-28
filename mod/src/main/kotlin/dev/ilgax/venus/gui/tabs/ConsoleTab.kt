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
        val visibleLines =
            lines
                .dropLast(scrollOffset.coerceIn(0, max(0, lines.size - maxVisibleLines)))
                .takeLast(maxVisibleLines)
        val firstVisibleLine = lines.size - scrollOffset.coerceIn(0, max(0, lines.size - maxVisibleLines)) - visibleLines.size
        val selectionStart = minOf(selectedStart ?: -1, selectedEnd ?: -1)
        val selectionEnd = maxOf(selectedStart ?: -1, selectedEnd ?: -1)
        var lineY = y + 10

        visibleLines.forEachIndexed { index, line ->
            val lineIndex = firstVisibleLine + index
            if (lineIndex in selectionStart..selectionEnd) {
                guiGraphics.fill(x + 6, lineY - 1, x + width - 6, lineY + lineHeight - 1, COLOR_SELECTED)
            }
            guiGraphics.drawString(font, trimLine(font, line, width - 20), x + 10, lineY, COLOR_TEXT, false)
            lineY += lineHeight
        }
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

    private const val COLOR_CONSOLE = 0xFF05070A.toInt()
    private const val COLOR_BORDER = 0xFF2B3542.toInt()
    private const val COLOR_TEXT = 0xFFE6EDF3.toInt()
    private const val COLOR_MUTED = 0xFF687482.toInt()
    private const val COLOR_SELECTED = 0xFF1F4E6A.toInt()
}
