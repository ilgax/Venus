package dev.ilgax.venus.client.ui.module

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.page.VenusPageContract
import dev.ilgax.venus.client.ui.profile.UiModuleInstance
import dev.ilgax.venus.client.ui.render.VenusDraw
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import kotlin.math.roundToInt

abstract class BaseUiModule(
    final override val instance: UiModuleInstance,
) : UiModule {
    protected var bounds = Bounds(0, 0, 0, 0)

    final override fun layout(bounds: Bounds) {
        this.bounds = bounds
        onLayout(bounds)
    }

    protected open fun onLayout(bounds: Bounds) {}

    protected fun surface(
        graphics: GuiGraphics,
        font: Font,
    ): Bounds {
        VenusDraw.rect(graphics, bounds, instance.style.backgroundOverride ?: VenusTheme.SURFACE)
        VenusDraw.border(graphics, bounds, instance.style.borderOverride ?: VenusTheme.BORDER)
        val padding = instance.style.padding ?: 8
        val inner = bounds.inset(padding)
        if (instance.style.showTitle && !instance.title.isNullOrBlank()) {
            VenusDraw.text(graphics, font, instance.title!!, inner.x, inner.y, VenusTheme.TEXT_MUTED, false)
            return Bounds(inner.x, inner.y + font.lineHeight + 4, inner.width, inner.height - font.lineHeight - 4)
        }
        return inner
    }
}

class ServerStatusModule(
    instance: UiModuleInstance,
) : BaseUiModule(instance) {
    override fun render(
        graphics: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val inner = surface(graphics, font)
        val server = SessionState.serverListName ?: SessionState.serverAddress ?: "No server"
        val state = if (SessionState.sessionActive) "Authenticated" else "Offline"
        val color = if (SessionState.sessionActive) VenusTheme.SUCCESS else VenusTheme.DANGER
        VenusDraw.textTruncated(graphics, font, server, inner.x, inner.y, inner.width * 2 / 3, VenusTheme.TEXT, false)
        VenusDraw.textRight(graphics, font, state, inner.right, inner.y, color, false)
    }
}

class MetricCardModule(
    instance: UiModuleInstance,
) : BaseUiModule(instance) {
    override fun render(
        graphics: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val inner = surface(graphics, font)
        val value = formatMetric(instance.settings.primaryMetric)
        val secondary = instance.settings.secondaryMetric?.let(::formatMetric)
        val accent = instance.style.accentOverride ?: VenusTheme.ACCENT
        VenusDraw.text(graphics, font, value, inner.x, inner.y + 2, VenusTheme.TEXT, false)
        if (secondary != null) {
            VenusDraw.textRight(graphics, font, secondary, inner.right, inner.y + 2, accent, false)
        }
    }
}

class StatGraphModule(
    instance: UiModuleInstance,
) : BaseUiModule(instance) {
    override fun render(
        graphics: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val inner = surface(graphics, font)
        val samples = SessionState.statsHistory.mapNotNull { statValue(it, instance.settings.primaryMetric) }
        if (samples.size < 2) {
            VenusDraw.text(graphics, font, "Waiting for history...", inner.x, inner.y, VenusTheme.TEXT_MUTED, false)
            return
        }
        val min = samples.min()
        val max = samples.max()
        val range = (max - min).takeIf { it > 0.0001 } ?: 1.0
        val color = instance.style.accentOverride ?: VenusTheme.ACCENT
        samples.forEachIndexed { index, value ->
            val x = inner.x + index * (inner.width - 1) / (samples.size - 1)
            val y = inner.bottom - 1 - (((value - min) / range) * (inner.height - 1)).roundToInt()
            graphics.fill(x, y, x + 2, y + 2, color)
        }
        VenusDraw.text(graphics, font, String.format("%.1f", samples.last()), inner.x, inner.y, VenusTheme.TEXT, false)
    }
}

class OnlinePlayersModule(
    instance: UiModuleInstance,
) : BaseUiModule(instance) {
    override fun render(
        graphics: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val inner = surface(graphics, font)
        val players = SessionState.latestPlayerList?.onlinePlayers.orEmpty()
        if (players.isEmpty()) {
            VenusDraw.text(graphics, font, "No online players", inner.x, inner.y, VenusTheme.TEXT_MUTED, false)
            return
        }
        players.take(instance.settings.maxRows.coerceIn(1, 32)).forEachIndexed { index, player ->
            VenusDraw.textTruncated(
                graphics,
                font,
                player.displayName,
                inner.x,
                inner.y + index * (font.lineHeight + 3),
                inner.width,
                VenusTheme.TEXT,
                false,
            )
        }
    }
}

class ActiveTransfersModule(
    instance: UiModuleInstance,
) : BaseUiModule(instance) {
    override fun render(
        graphics: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val inner = surface(graphics, font)
        val transfers = SessionState.activeFileTransfers
        if (transfers.isEmpty()) {
            VenusDraw.text(graphics, font, "No active transfers", inner.x, inner.y, VenusTheme.TEXT_MUTED, false)
            return
        }
        transfers.take(8).forEachIndexed { index, transfer ->
            val progress =
                if (transfer.totalBytes > 0) {
                    " ${(transfer.transferredBytes * 100 / transfer.totalBytes).coerceIn(0, 100)}%"
                } else {
                    ""
                }
            VenusDraw.textTruncated(
                graphics,
                font,
                "${transfer.direction}: ${transfer.path}$progress",
                inner.x,
                inner.y + index * (font.lineHeight + 3),
                inner.width,
                VenusTheme.TEXT,
                false,
            )
        }
    }
}

class PageWorkflowModule(
    instance: UiModuleInstance,
    private val page: VenusPageContract,
    private val childProvider: () -> List<AbstractWidget> = { emptyList() },
    private val keyHandler: ((Int, Int, Int) -> Boolean)? = null,
) : BaseUiModule(instance) {
    override fun onLayout(bounds: Bounds) {
        page.layout(bounds)
    }

    override fun render(
        graphics: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        page.render(graphics, font, mouseX, mouseY, partialTick)
    }

    override fun onActivate() {
        page.onEnter()
        children().forEach { it.visible = true }
    }

    override fun onDeactivate() {
        page.onLeave()
        children().forEach { it.visible = false }
    }

    override fun children(): List<AbstractWidget> = childProvider()

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean = page.mouseClicked(mouseX, mouseY, button)

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean = page.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)

    fun keyPressed(
        key: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean = keyHandler?.invoke(key, scanCode, modifiers) == true
}

private fun formatMetric(metric: String?): String {
    val stats = SessionState.latestStats ?: return "--"
    return when (metric) {
        "tps" -> stats.tps?.let { String.format("%.1f", it) } ?: "--"
        "mspt" -> stats.mspt?.let { String.format("%.1f", it) } ?: "--"
        "ram_used" -> stats.ramUsed?.let { String.format("%.1f GB", it / 1024.0) } ?: "--"
        "ram_max" -> stats.ramMax?.let { String.format("%.1f GB", it / 1024.0) } ?: "--"
        "online_players" -> (stats.onlinePlayers ?: 0).toString()
        "max_players" -> (stats.maxPlayers ?: 0).toString()
        "uptime" -> stats.uptime?.let { "${it / 3600}h ${(it % 3600) / 60}m" } ?: "--"
        else -> "--"
    }
}

private fun statValue(
    stats: dev.ilgax.venus.protocol.StatsPacket,
    metric: String?,
): Double? =
    when (metric) {
        "tps" -> stats.tps
        "mspt" -> stats.mspt
        "ram_used" -> stats.ramUsed?.toDouble()
        "ram_max" -> stats.ramMax?.toDouble()
        "online_players" -> stats.onlinePlayers?.toDouble()
        "max_players" -> stats.maxPlayers?.toDouble()
        "uptime" -> stats.uptime?.toDouble()
        else -> null
    }
