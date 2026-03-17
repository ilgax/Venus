package dev.xcyn.venus.stats

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bukkit.Server

@Serializable
data class StatsPacket(
    val type: String = "stats",
    val tps: Double? = null,
    val mspt: Double? = null,
    @SerialName("ram_used") val ramUsed: Long? = null,
    @SerialName("ram_max") val ramMax: Long? = null,
    val uptime: Long? = null
)

object StatsCollector {

    private val json = Json { explicitNulls = false }

    fun getTPS(server: Server): Double = server.tps[0].coerceAtMost(20.0)
    fun getMSPT(server: Server): Double = server.averageTickTime
    fun getRamUsed(): Long = (Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }) / 1024 / 1024
    fun getRamMax(): Long = Runtime.getRuntime().maxMemory() / 1024 / 1024
    fun getUptime(): Long = java.lang.management.ManagementFactory.getRuntimeMXBean().uptime / 1000

    fun buildStatsJson(server: Server, requestedStats: List<String>): String {
        val packet = StatsPacket(
            tps = if ("tps" in requestedStats) getTPS(server) else null,
            mspt = if ("mspt" in requestedStats) getMSPT(server) else null,
            ramUsed = if ("ram" in requestedStats) getRamUsed() else null,
            ramMax = if ("ram" in requestedStats) getRamMax() else null,
            uptime = if ("uptime" in requestedStats) getUptime() else null
        )
        return json.encodeToString(packet)
    }
}