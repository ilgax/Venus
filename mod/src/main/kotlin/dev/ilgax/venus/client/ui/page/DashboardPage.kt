package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.client.ui.component.VenusEmptyState
import dev.ilgax.venus.client.ui.component.VenusErrorState
import dev.ilgax.venus.client.ui.component.VenusEventRow
import dev.ilgax.venus.client.ui.component.VenusLoadingState
import dev.ilgax.venus.client.ui.component.VenusMetricCard
import dev.ilgax.venus.client.ui.component.VenusPlayerRow
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusSpacing
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Dashboard page. Four compact metric cards (TPS/MSPT, Memory, Online players,
 * Uptime), a recent-events list, and an online-player preview list. Uses real
 * [SessionState] data; when stats are absent it shows a loading/error state.
 *
 * Data flows in via [SessionState] — this page never issues network requests
 * directly. The screen calls [onEnter] which can trigger stat subscription via
 * the injected callback.
 */
class DashboardPage(
    private val subscribeStats: () -> Unit,
    private val requestPlayerList: () -> Unit,
    private val showPlayerHeads: () -> Boolean = { true },
    private val onNavigateToPlayer: (String) -> Unit = {},
) : VenusPageContract {
    private var contentBounds: Bounds = Bounds(0, 0, 0, 0)
    private var requestedStats = false
    private var onlinePreviewBounds: Bounds? = null
    private var onlinePreviewRowHeight: Int = VenusDimensions.ROW_HEIGHT
    private var onlinePreviewStartY: Int = 0
    private val metricCards = Array(4) { VenusMetricCard(Bounds(0, 0, 0, 0)) }

    override fun layout(contentBounds: Bounds) {
        this.contentBounds = contentBounds
    }

    override fun onEnter() {
        if (SessionState.sessionActive && !requestedStats) {
            subscribeStats()
            requestedStats = true
        }
    }

    override fun render(
        g: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val pad = VenusDimensions.CONTENT_PADDING
        val inner = contentBounds.inset(pad)

        if (!SessionState.sessionActive) {
            VenusErrorState(inner).run {
                message = "Not connected"
                render(g, font)
            }
            return
        }

        val stats = SessionState.latestStats
        if (stats == null) {
            VenusLoadingState(inner).run {
                message = "Waiting for stats..."
                render(g, font, (System.currentTimeMillis() % 1000) / 1000f)
            }
            return
        }

        renderMetricCards(g, font, inner, stats)
        renderLowerSection(g, font, inner, mouseX, mouseY)
    }

    private fun renderMetricCards(
        g: GuiGraphics,
        font: Font,
        inner: Bounds,
        stats: dev.ilgax.venus.protocol.StatsPacket,
    ) {
        val gap = VenusSpacing.SM
        val cardH = 56
        val cardsY = inner.y
        val totalGap = gap * 3
        val cardW = (inner.width - totalGap) / 4

        val cardData =
            arrayOf(
                Triple("TPS • MSPT", formatTps(stats.tps) + " • " + formatMspt(stats.mspt), formatTpsSub(stats.tps)),
                Triple("Memory", formatRam(stats.ramUsed, stats.ramMax), formatRamSub(stats.ramUsed, stats.ramMax)),
                Triple("Players", "${stats.onlinePlayers ?: 0} / ${stats.maxPlayers ?: 0}", "online"),
                Triple("Uptime", formatUptime(stats.uptime), null),
            )

        cardData.forEachIndexed { i, (label, value, sub) ->
            val cb = Bounds(inner.x + i * (cardW + gap), cardsY, cardW, cardH)
            val card = metricCards[i]
            card.bounds = cb
            card.label = label
            card.value = value
            card.accent = VenusTheme.ACCENT
            card.subtext = sub
            card.render(g, font)
        }
    }

    private fun renderLowerSection(
        g: GuiGraphics,
        font: Font,
        inner: Bounds,
        mouseX: Int,
        mouseY: Int,
    ) {
        val topH = 56
        val gap = VenusSpacing.LG
        val lowerY = inner.y + topH + gap
        val lowerH = inner.bottom - lowerY
        val leftW = (inner.width - gap) * 3 / 5
        val rightX = inner.x + leftW + gap
        val rightW = inner.width - leftW - gap

        renderEvents(g, font, Bounds(inner.x, lowerY, leftW, lowerH), mouseX, mouseY)
        val previewBounds = Bounds(rightX, lowerY, rightW, lowerH)
        onlinePreviewBounds = previewBounds
        renderOnlinePreview(g, font, previewBounds, mouseX, mouseY)
    }

    private fun renderEvents(
        g: GuiGraphics,
        font: Font,
        bounds: Bounds,
        mouseX: Int,
        mouseY: Int,
    ) {
        VenusDraw.rect(g, bounds, VenusTheme.SURFACE)
        VenusDraw.border(g, bounds, VenusTheme.BORDER)
        VenusDraw.text(g, font, "Recent Events", bounds.x + 8, bounds.y + 6, VenusTheme.TEXT_MUTED, false)
        VenusDraw.hSeparator(g, bounds.x + 8, bounds.y + 6 + font.lineHeight + 2, bounds.width - 16, VenusTheme.BORDER)

        val events = SessionState.statsHistory.takeLast(8)
        if (events.isEmpty()) {
            VenusEmptyState(Bounds(bounds.x, bounds.y + 30, bounds.width, bounds.height - 30)).run {
                message = "No events yet"
                render(g, font)
            }
            return
        }
        val rowY = bounds.y + 30
        val rowH = VenusDimensions.ROW_HEIGHT_COMPACT
        events.forEachIndexed { i, stat ->
            val rb = Bounds(bounds.x + 8, rowY + i * rowH, bounds.width - 16, rowH)
            val ts = "+${i}s"
            val msg = "${stat.onlinePlayers ?: 0} players • ${formatUptime(stat.uptime)}"
            VenusEventRow(rb, ts, msg).render(g, font)
        }
    }

    private fun renderOnlinePreview(
        g: GuiGraphics,
        font: Font,
        bounds: Bounds,
        mouseX: Int,
        mouseY: Int,
    ) {
        VenusDraw.rect(g, bounds, VenusTheme.SURFACE)
        VenusDraw.border(g, bounds, VenusTheme.BORDER)
        VenusDraw.text(g, font, "Online Players", bounds.x + 8, bounds.y + 6, VenusTheme.TEXT_MUTED, false)
        VenusDraw.hSeparator(g, bounds.x + 8, bounds.y + 6 + font.lineHeight + 2, bounds.width - 16, VenusTheme.BORDER)

        val list = SessionState.latestPlayerList
        if (list == null) {
            if (!SessionState.sessionActive) {
                VenusEmptyState(Bounds(bounds.x, bounds.y + 30, bounds.width, bounds.height - 30)).run {
                    message = "Authenticate to load players"
                    render(g, font)
                }
                return
            }
            VenusEmptyState(Bounds(bounds.x, bounds.y + 30, bounds.width, bounds.height - 30)).run {
                message = "No player data"
                subtext = "Requesting..."
                render(g, font)
            }
            return
        }
        val players = list.onlinePlayers
        if (players.isEmpty()) {
            VenusEmptyState(Bounds(bounds.x, bounds.y + 30, bounds.width, bounds.height - 30)).run {
                message = "No players online"
                render(g, font)
            }
            return
        }
        val rowY = bounds.y + 30
        onlinePreviewStartY = rowY
        val rowH = VenusDimensions.ROW_HEIGHT
        onlinePreviewRowHeight = rowH
        val maxRows = (bounds.height - 36) / rowH
        players.take(maxRows).forEachIndexed { i, p ->
            val rb = Bounds(bounds.x + 4, rowY + i * rowH, bounds.width - 8, rowH)
            VenusPlayerRow(
                rb,
                p.displayName,
                p.uuid,
                p.online,
                p.operator,
            ).render(g, font, rb.contains(mouseX, mouseY), false, showPlayerHeads())
        }
    }

    private fun formatTps(tps: Double?): String = if (tps != null) String.format("%.1f", tps) else "--"

    private fun formatMspt(mspt: Double?): String = if (mspt != null) String.format("%.1f", mspt) else "--"

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        if (button != 0) return false
        val pb = onlinePreviewBounds ?: return false
        if (!pb.contains(mouseX.toInt(), mouseY.toInt())) return false
        val list = SessionState.latestPlayerList ?: return false
        val players = list.onlinePlayers
        if (players.isEmpty()) return false
        val rowH = onlinePreviewRowHeight
        val startY = onlinePreviewStartY
        val maxRows = (pb.height - 36) / rowH
        val relY = mouseY.toInt() - startY
        if (relY < 0) return false
        val index = relY / rowH
        if (index in 0 until minOf(players.size, maxRows)) {
            onNavigateToPlayer(players[index].uuid)
            return true
        }
        return false
    }

    private fun formatTpsSub(tps: Double?): String? =
        if (tps ==
            null
        ) {
            null
        } else if (tps > 18) {
            "healthy"
        } else if (tps > 15) {
            "lagging"
        } else {
            "critical"
        }

    private fun formatRam(
        used: Long?,
        max: Long?,
    ): String =
        if (used != null &&
            max != null
        ) {
            "${String.format("%.1f", used / 1024.0)} / ${String.format("%.1f", max / 1024.0)} GB"
        } else {
            "--"
        }

    private fun formatRamSub(
        used: Long?,
        max: Long?,
    ): String? = if (used != null && max != null && max > 0) "${(used * 100 / max)}% used" else null

    private fun formatUptime(seconds: Long?): String {
        if (seconds == null) return "--"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
