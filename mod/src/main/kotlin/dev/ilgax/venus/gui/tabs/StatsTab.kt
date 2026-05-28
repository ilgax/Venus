package dev.ilgax.venus.gui.tabs

import dev.ilgax.venus.protocol.StatsPacket
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import java.util.Locale
import kotlin.math.max

object StatsTab {
    fun render(
        guiGraphics: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
        width: Int,
    ) {
        val stats = SessionState.latestStats
        val columns = 2
        val gap = 12
        val cardWidth = max(120, (width - gap) / columns)
        val cardHeight = 54

        statCard(
            guiGraphics,
            font,
            x,
            y,
            cardWidth,
            cardHeight,
            "TPS",
            formatDecimal(stats?.tps),
            "ticks per second",
        )
        statCard(
            guiGraphics,
            font,
            x + cardWidth + gap,
            y,
            cardWidth,
            cardHeight,
            "MSPT",
            formatDecimal(stats?.mspt),
            "milliseconds per tick",
        )
        statCard(
            guiGraphics,
            font,
            x,
            y + cardHeight + gap,
            cardWidth,
            cardHeight,
            "RAM",
            formatRam(stats),
            "used / max",
        )
        statCard(
            guiGraphics,
            font,
            x + cardWidth + gap,
            y + cardHeight + gap,
            cardWidth,
            cardHeight,
            "Uptime",
            formatUptime(stats?.uptime),
            "server uptime",
        )
    }

    private fun statCard(
        guiGraphics: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: String,
        value: String,
        hint: String,
    ) {
        guiGraphics.fill(x, y, x + width, y + height, COLOR_CARD)
        guiGraphics.renderOutline(x, y, width, height, COLOR_BORDER)
        guiGraphics.drawString(font, label, x + 10, y + 8, COLOR_MUTED, false)
        guiGraphics.drawString(font, value, x + 10, y + 24, COLOR_TEXT, false)
        guiGraphics.drawString(font, hint, x + 10, y + 40, COLOR_DIM, false)
    }

    private fun formatDecimal(value: Double?): String =
        if (value == null) {
            "--"
        } else {
            String.format(Locale.US, "%.1f", value)
        }

    private fun formatRam(stats: StatsPacket?): String {
        val used = stats?.ramUsed ?: return "--"
        val max = stats.ramMax ?: return "$used MB"
        return "$used / $max MB"
    }

    private fun formatUptime(uptimeSeconds: Long?): String {
        if (uptimeSeconds == null) return "--"
        val hours = uptimeSeconds / 3600
        val minutes = (uptimeSeconds % 3600) / 60
        val seconds = uptimeSeconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    private const val COLOR_CARD = 0xFF111820.toInt()
    private const val COLOR_BORDER = 0xFF2B3542.toInt()
    private const val COLOR_TEXT = 0xFFF4F7FA.toInt()
    private const val COLOR_MUTED = 0xFF9AA7B5.toInt()
    private const val COLOR_DIM = 0xFF687482.toInt()
}
