package dev.ilgax.venus.client.ui.render

import dev.ilgax.venus.client.ui.core.Bounds
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Text rendering utilities beyond what [VenusDraw] covers: measurement,
 * truncation helpers, and vertical centering math that pages reuse.
 *
 * All methods are allocation-free. [truncate] returns a substring but does not
 * touch [GuiGraphics]; callers use the returned string for display.
 */
object TextRenderUtil {
    fun width(
        font: Font,
        text: String,
    ): Int = font.width(text)

    /**
     * Returns the longest prefix of [text] (plus optional [suffix]) that fits
     * within [maxWidth]. Does not draw.
     */
    fun truncate(
        font: Font,
        text: String,
        maxWidth: Int,
        suffix: String = "...",
    ): String {
        if (width(font, text) <= maxWidth) return text
        val suffixW = width(font, suffix)
        val limit = maxWidth - suffixW
        if (limit <= 0) return suffix
        var end = text.length
        while (end > 0 && width(font, text.substring(0, end)) > limit) end--
        return if (end <= 0) suffix else text.substring(0, end) + suffix
    }

    fun verticalCenter(
        bounds: Bounds,
        font: Font,
    ): Int = bounds.y + (bounds.height - font.lineHeight) / 2

    fun horizontalCenter(
        bounds: Bounds,
        font: Font,
        text: String,
    ): Int = bounds.x + (bounds.width - width(font, text)) / 2

    fun split(
        font: Font,
        text: String,
        maxWidth: Int,
    ): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (font.width(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = word
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return if (lines.isEmpty()) listOf(text) else lines
    }
}
