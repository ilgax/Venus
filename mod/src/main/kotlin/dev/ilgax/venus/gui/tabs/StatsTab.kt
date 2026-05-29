package dev.ilgax.venus.gui.tabs

import dev.ilgax.venus.protocol.StatsPacket
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import java.util.Locale
import kotlin.math.roundToInt

object StatsTab {
    private data class Rect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    private data class OverviewLayout(
        val summary: Rect,
        val cards: List<Rect>,
        val showGraphs: Boolean,
    )

    fun render(
        guiGraphics: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        val stats = SessionState.latestStats
        val gap = 8

        val summaryHeight = 28
        val summaryRect = Rect(x, y, width, summaryHeight)

        val cardTopY = y + summaryHeight + gap
        val availableHeightForCards = height - summaryHeight - gap

        val showGraphs = availableHeightForCards > 80
        val numCards = 3

        val maxCols = if (width < 800) 2 else 5
        val rows = (numCards + maxCols - 1) / maxCols
        val cardWidth = (width - gap * (maxCols - 1)) / maxCols
        val cardHeight = if (showGraphs) (availableHeightForCards - gap * (rows - 1)) / rows else 46

        val cards = mutableListOf<Rect>()
        var currentY = cardTopY
        var currentX = x
        var col = 0
        for (i in 0 until numCards) {
            cards.add(Rect(currentX, currentY, cardWidth, cardHeight))
            col++
            if (col >= maxCols) {
                col = 0
                currentX = x
                currentY += cardHeight + gap
            } else {
                currentX += cardWidth + gap
            }
        }

        val layout = OverviewLayout(summaryRect, cards, showGraphs)

        renderServerSummary(guiGraphics, font, layout.summary, stats)

        val history = SessionState.statsHistory
        val tpsValue = stats?.tps
        val tpsBadge =
            when {
                tpsValue == null -> null
                tpsValue >= 19.0 -> "Stable"
                tpsValue >= 16.0 -> "Warn"
                else -> "Bad"
            }
        val msptValue = stats?.mspt
        val msptBadge =
            when {
                msptValue == null -> null
                msptValue <= 40.0 -> "Good"
                msptValue <= 50.0 -> "High"
                else -> "Bad"
            }

        combinedCard(
            guiGraphics,
            font,
            layout.cards[0],
            "TPS",
            formatDecimal(tpsValue),
            tpsBadge,
            COLOR_TPS,
            layout.showGraphs,
            history.mapNotNull { it.tps },
            0.0,
            20.0,
            { formatDecimal(it) },
        )
        combinedCard(
            guiGraphics,
            font,
            layout.cards[1],
            "MSPT",
            formatDecimal(msptValue),
            msptBadge,
            COLOR_MSPT,
            layout.showGraphs,
            history.mapNotNull { it.mspt },
            0.0,
            100.0,
            { formatDecimal(it) },
        )
        dualCombinedCard(
            guiGraphics,
            font,
            layout.cards[2],
            "CPU",
            formatPercent(stats?.cpuLoad),
            COLOR_CPU,
            history.mapNotNull { it.cpuLoad },
            0.0,
            100.0,
            { "${it.roundToInt()}%" },
            "RAM",
            formatRamShort(stats),
            COLOR_RAM,
            history.mapNotNull { it.ramUsed?.toDouble() },
            0.0,
            (stats?.ramMax ?: history.mapNotNull { it.ramMax }.maxOrNull() ?: 1L).toDouble().coerceAtLeast(1.0),
            { "${mbToGb(it.toLong())} GB" },
            layout.showGraphs,
        )
    }

    private fun renderServerSummary(
        guiGraphics: GuiGraphics,
        font: Font,
        rect: Rect,
        stats: StatsPacket?,
    ) {
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, COLOR_CARD)
        guiGraphics.renderOutline(rect.x, rect.y, rect.width, rect.height, COLOR_BORDER)

        val serverName = stats?.serverName ?: "Server"
        val version = stats?.minecraftVersion ?: "1.x"
        val playersOnline = stats?.onlinePlayers
        val playersMax = stats?.maxPlayers
        val playersValue = if (playersOnline != null && playersMax != null) "$playersOnline / $playersMax" else "--"
        val uptimeValue = formatUptime(stats?.uptime)
        val address = SessionState.serverAddress

        val leftText = "$serverName - $version"
        val rightText = "$playersValue - $uptimeValue"

        val rightWidth = font.width(rightText)
        val rightX = rect.x + rect.width - rightWidth - 10
        guiGraphics.drawString(font, rightText, rightX, rect.y + 10, COLOR_TEXT, false)

        val maxLeftWidth = if (address != null) rect.width / 3 else rect.width - rightWidth - 30
        val clippedLeft = font.plainSubstrByWidth(leftText, maxLeftWidth)
        val leftWidth = font.width(clippedLeft)
        guiGraphics.drawString(font, clippedLeft, rect.x + 10, rect.y + 10, COLOR_TEXT, false)

        if (address != null) {
            val availableSpace = rightX - (rect.x + 10 + leftWidth) - 20
            if (availableSpace > 20) {
                val clippedCenter = font.plainSubstrByWidth(address, availableSpace)
                val centerWidth = font.width(clippedCenter)
                var centerX = rect.x + (rect.width - centerWidth) / 2

                if (centerX < rect.x + 10 + leftWidth + 10) {
                    centerX = rect.x + 10 + leftWidth + 10
                }

                guiGraphics.drawString(font, clippedCenter, centerX, rect.y + 10, COLOR_MUTED, false)
            }
        }
    }

    private fun combinedCard(
        guiGraphics: GuiGraphics,
        font: Font,
        rect: Rect,
        label: String,
        value: String,
        badge: String?,
        accent: Int,
        showGraph: Boolean,
        samples: List<Double>,
        minValue: Double,
        maxValue: Double,
        formatAxis: (Double) -> String,
    ) {
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, COLOR_CARD)
        guiGraphics.renderOutline(rect.x, rect.y, rect.width, rect.height, COLOR_BORDER)

        guiGraphics.drawString(font, label, rect.x + 8, rect.y + 8, COLOR_MUTED, false)
        if (badge != null) {
            val badgeColor =
                when (badge) {
                    "Stable", "Good" -> COLOR_OK
                    "Warn", "High" -> COLOR_WARN
                    else -> COLOR_BAD
                }
            guiGraphics.drawString(font, badge, rect.x + rect.width - font.width(badge) - 8, rect.y + 8, badgeColor, false)
        }

        guiGraphics.drawString(font, value, rect.x + 8, rect.y + 22, COLOR_TEXT, false)

        if (!showGraph) {
            guiGraphics.fill(rect.x + 1, rect.y + rect.height - 3, rect.x + rect.width - 1, rect.y + rect.height - 1, accent)
            return
        }

        val graphX = rect.x + 8
        val graphY = rect.y + 38
        val graphWidth = rect.width - 16
        val graphHeight = rect.height - 46

        if (graphHeight < 20) {
            guiGraphics.fill(rect.x + 1, rect.y + rect.height - 3, rect.x + rect.width - 1, rect.y + rect.height - 1, accent)
            return
        }

        val maxLabel = formatAxis(maxValue)
        val minLabel = formatAxis(minValue)

        guiGraphics.drawString(font, maxLabel, graphX + graphWidth - font.width(maxLabel) - 2, graphY + 2, COLOR_DIM, false)
        guiGraphics.drawString(
            font,
            minLabel,
            graphX + graphWidth - font.width(minLabel) - 2,
            graphY + graphHeight - font.lineHeight - 2,
            COLOR_DIM,
            false,
        )

        if (samples.size < 2) {
            guiGraphics.fill(graphX, graphY + graphHeight - 2, graphX + graphWidth, graphY + graphHeight - 1, accent)
            return
        }

        drawGraphLine(guiGraphics, graphX, graphY, graphWidth, graphHeight, samples, minValue, maxValue, accent)
    }

    private fun dualCombinedCard(
        guiGraphics: GuiGraphics,
        font: Font,
        rect: Rect,
        label1: String,
        value1: String,
        accent1: Int,
        samples1: List<Double>,
        min1: Double,
        max1: Double,
        formatAxis1: (Double) -> String,
        label2: String,
        value2: String,
        accent2: Int,
        samples2: List<Double>,
        min2: Double,
        max2: Double,
        formatAxis2: (Double) -> String,
        showGraph: Boolean,
    ) {
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, COLOR_CARD)
        guiGraphics.renderOutline(rect.x, rect.y, rect.width, rect.height, COLOR_BORDER)

        // Left side text (Metric 1)
        guiGraphics.drawString(font, label1, rect.x + 8, rect.y + 8, accent1, false)
        guiGraphics.drawString(font, value1, rect.x + 8, rect.y + 22, COLOR_TEXT, false)

        // Right side text (Metric 2)
        val rightX = rect.x + rect.width / 2 + 8
        guiGraphics.drawString(font, label2, rightX, rect.y + 8, accent2, false)
        guiGraphics.drawString(font, value2, rightX, rect.y + 22, COLOR_TEXT, false)

        if (!showGraph) {
            val mid = rect.x + rect.width / 2
            guiGraphics.fill(rect.x + 1, rect.y + rect.height - 3, mid, rect.y + rect.height - 1, accent1)
            guiGraphics.fill(mid, rect.y + rect.height - 3, rect.x + rect.width - 1, rect.y + rect.height - 1, accent2)
            return
        }

        val graphX = rect.x + 8
        val graphY = rect.y + 38
        val graphWidth = rect.width - 16
        val graphHeight = rect.height - 46

        if (graphHeight < 20) {
            val mid = rect.x + rect.width / 2
            guiGraphics.fill(rect.x + 1, rect.y + rect.height - 3, mid, rect.y + rect.height - 1, accent1)
            guiGraphics.fill(mid, rect.y + rect.height - 3, rect.x + rect.width - 1, rect.y + rect.height - 1, accent2)
            return
        }

        val maxLabel1 = formatAxis1(max1)
        val minLabel1 = formatAxis1(min1)
        guiGraphics.drawString(font, maxLabel1, graphX + 2, graphY + 2, accent1, false)
        guiGraphics.drawString(font, minLabel1, graphX + 2, graphY + graphHeight - font.lineHeight - 2, accent1, false)

        val maxLabel2 = formatAxis2(max2)
        val minLabel2 = formatAxis2(min2)
        guiGraphics.drawString(font, maxLabel2, graphX + graphWidth - font.width(maxLabel2) - 2, graphY + 2, accent2, false)
        guiGraphics.drawString(
            font,
            minLabel2,
            graphX + graphWidth - font.width(minLabel2) - 2,
            graphY + graphHeight - font.lineHeight - 2,
            accent2,
            false,
        )

        if (samples1.size >= 2) {
            drawGraphLine(guiGraphics, graphX, graphY, graphWidth, graphHeight, samples1, min1, max1, accent1)
        } else {
            guiGraphics.fill(graphX, graphY + graphHeight - 2, graphX + graphWidth, graphY + graphHeight - 1, accent1)
        }

        if (samples2.size >= 2) {
            drawGraphLine(guiGraphics, graphX, graphY, graphWidth, graphHeight, samples2, min2, max2, accent2)
        } else {
            guiGraphics.fill(graphX, graphY + graphHeight - 3, graphX + graphWidth, graphY + graphHeight - 2, accent2)
        }
    }

    private fun drawGraphLine(
        guiGraphics: GuiGraphics,
        graphX: Int,
        graphY: Int,
        graphWidth: Int,
        graphHeight: Int,
        samples: List<Double>,
        minValue: Double,
        maxValue: Double,
        accent: Int,
    ) {
        val points = samples.takeLast(MAX_GRAPH_SAMPLES)
        val numPoints = points.size

        for (xOffset in 0 until graphWidth) {
            val progress = xOffset.toDouble() / (graphWidth - 1).coerceAtLeast(1)
            val pointIndexFloat = progress * (numPoints - 1)
            val index0 = pointIndexFloat.toInt().coerceIn(0, numPoints - 1)
            val index1 = (index0 + 1).coerceIn(0, numPoints - 1)
            val fraction = pointIndexFloat - index0

            val p0 = points[maxOf(0, index0 - 1)]
            val p1 = points[index0]
            val p2 = points[index1]
            val p3 = points[minOf(numPoints - 1, index1 + 1)]

            val t = fraction
            val t2 = t * t
            val t3 = t2 * t
            val c0 = 2 * p1
            val c1 = -p0 + p2
            val c2 = 2 * p0 - 5 * p1 + 4 * p2 - p3
            val c3 = -p0 + 3 * p1 - 3 * p2 + p3
            val interpolatedValue = 0.5 * (c0 + c1 * t + c2 * t2 + c3 * t3)

            val clampedValue = interpolatedValue.coerceIn(minValue, maxValue)
            val currentY = sampleY(clampedValue, minValue, maxValue, graphY, graphHeight)
            val currentX = graphX + xOffset

            val topColor = (accent and 0x00FFFFFF) or 0x44000000
            val bottomColor = accent and 0x00FFFFFF
            guiGraphics.fillGradient(currentX, currentY, currentX + 1, graphY + graphHeight, topColor, bottomColor)

            guiGraphics.fill(currentX, currentY - 1, currentX + 1, currentY + 1, accent)
            guiGraphics.fill(currentX, currentY - 2, currentX + 1, currentY - 1, (accent and 0x00FFFFFF) or 0x44000000)
            guiGraphics.fill(currentX, currentY + 1, currentX + 1, currentY + 2, (accent and 0x00FFFFFF) or 0x44000000)
        }
    }

    private fun sampleY(
        value: Double,
        minValue: Double,
        maxValue: Double,
        graphY: Int,
        graphHeight: Int,
    ): Int {
        val range = (maxValue - minValue).coerceAtLeast(1.0)
        val normalized = ((value - minValue) / range).coerceIn(0.0, 1.0)
        return graphY + graphHeight - (normalized * graphHeight).roundToInt()
    }

    private fun formatDecimal(value: Double?): String =
        if (value == null) {
            "--"
        } else {
            String.format(Locale.US, "%.1f", value)
        }

    private fun formatPercent(value: Double?): String =
        if (value == null) {
            "--"
        } else {
            "${formatDecimal(value)}%"
        }

    private fun formatRamShort(stats: StatsPacket?): String {
        val used = stats?.ramUsed ?: return "--"
        val max = stats.ramMax ?: return "${mbToGb(used)} GB"
        val pct = (used.toDouble() / max.toDouble() * 100).roundToInt()
        return "${mbToGb(used)} / ${mbToGb(max)} GB ($pct%)"
    }

    private fun mbToGb(value: Long): String = String.format(Locale.US, "%.1f", value / 1024.0)

    private fun formatUptime(uptimeSeconds: Long?): String {
        if (uptimeSeconds == null) return "--"
        val hours = uptimeSeconds / 3600
        val minutes = (uptimeSeconds % 3600) / 60
        val seconds = uptimeSeconds % 60

        return when {
            hours > 0 -> "%02d:%02d:%02d".format(hours, minutes, seconds)
            else -> "%02d:%02d".format(minutes, seconds)
        }
    }

    private const val MAX_GRAPH_SAMPLES = 60
    private const val COLOR_CARD = 0xFF101216.toInt()
    private const val COLOR_BORDER = 0xFF2A2E36.toInt()
    private const val COLOR_TEXT = 0xFFF4F7FA.toInt()
    private const val COLOR_MUTED = 0xFFA1A7B3.toInt()
    private const val COLOR_DIM = 0xFF6F7580.toInt()
    private const val COLOR_OK = 0xFF10D39E.toInt()
    private const val COLOR_WARN = 0xFFE3B341.toInt()
    private const val COLOR_BAD = 0xFFFF4D64.toInt()
    private const val COLOR_TPS = 0xFF10D39E.toInt()
    private const val COLOR_MSPT = 0xFF7A8CFF.toInt()
    private const val COLOR_RAM = 0xFFFF62BC.toInt()
    private const val COLOR_CPU = 0xFFFFC107.toInt()
}
