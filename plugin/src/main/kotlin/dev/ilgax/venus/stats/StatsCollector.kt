package dev.ilgax.venus.stats

import dev.ilgax.venus.protocol.StatsPacket
import kotlinx.serialization.json.Json
import org.bukkit.Server
import java.lang.management.ManagementFactory
import java.math.BigDecimal
import java.math.RoundingMode

object StatsCollector {
    private val json = Json { explicitNulls = false }

    fun getTPS(server: Server): Double = (server.tps.getOrNull(0) ?: 20.0).coerceAtMost(20.0)

    fun getMSPT(server: Server): Double = server.averageTickTime

    fun getRamUsed(): Long = (Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }) / 1024 / 1024

    fun getRamMax(): Long = Runtime.getRuntime().maxMemory() / 1024 / 1024

    fun getCpuLoad(): Double? {
        val bean = ManagementFactory.getOperatingSystemMXBean()
        if (bean !is com.sun.management.OperatingSystemMXBean) return null
        val load = bean.processCpuLoad
        if (load < 0.0) return null
        return roundToOneDecimal((load * 100.0).coerceIn(0.0, 100.0))
    }

    fun getUptime(): Long =
        ManagementFactory
            .getRuntimeMXBean()
            .uptime / 1000

    internal fun roundToOneDecimal(value: Double): Double =
        BigDecimal
            .valueOf(value)
            .setScale(1, RoundingMode.HALF_UP)
            .toDouble()

    fun buildStatsJson(
        server: Server,
        requestedStats: List<String>,
    ): String {
        val packet =
            StatsPacket(
                type = "stats",
                tps = if ("tps" in requestedStats) roundToOneDecimal(getTPS(server)) else null,
                mspt = if ("mspt" in requestedStats) roundToOneDecimal(getMSPT(server)) else null,
                cpuLoad = if ("cpu" in requestedStats) getCpuLoad() else null,
                ramUsed = if ("ram" in requestedStats) getRamUsed() else null,
                ramMax = if ("ram" in requestedStats) getRamMax() else null,
                uptime = if ("uptime" in requestedStats) getUptime() else null,
                onlinePlayers = if ("players" in requestedStats) server.onlinePlayers.size else null,
                maxPlayers = if ("players" in requestedStats) server.maxPlayers else null,
                serverName = if ("server" in requestedStats) server.name else null,
                minecraftVersion = if ("server" in requestedStats) server.minecraftVersion else null,
            )
        return json.encodeToString(packet)
    }
}
