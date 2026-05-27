package dev.ilgax.venus.stats

import dev.ilgax.venus.protocol.StatsPacket
import kotlinx.serialization.json.Json
import org.bukkit.Server

object StatsCollector {
    private val json = Json { explicitNulls = false }

    fun getTPS(server: Server): Double = (server.tps.getOrNull(0) ?: 20.0).coerceAtMost(20.0)

    fun getMSPT(server: Server): Double = server.averageTickTime

    fun getRamUsed(): Long = (Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }) / 1024 / 1024

    fun getRamMax(): Long = Runtime.getRuntime().maxMemory() / 1024 / 1024

    fun getUptime(): Long =
        java.lang.management.ManagementFactory
            .getRuntimeMXBean()
            .uptime / 1000

    fun buildStatsJson(
        server: Server,
        requestedStats: List<String>,
    ): String {
        val packet =
            StatsPacket(
                type = "stats",
                tps = if ("tps" in requestedStats) getTPS(server) else null,
                mspt = if ("mspt" in requestedStats) getMSPT(server) else null,
                ramUsed = if ("ram" in requestedStats) getRamUsed() else null,
                ramMax = if ("ram" in requestedStats) getRamMax() else null,
                uptime = if ("uptime" in requestedStats) getUptime() else null,
            )
        return json.encodeToString(packet)
    }
}
