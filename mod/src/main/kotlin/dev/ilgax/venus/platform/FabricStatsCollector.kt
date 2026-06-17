package dev.ilgax.venus.platform

import dev.ilgax.venus.protocol.StatsPacket
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import java.lang.management.ManagementFactory
import java.math.BigDecimal
import java.math.RoundingMode

internal object FabricStatsCollector {
    private val json = Json { explicitNulls = false }

    data class Snapshot(
        val currentSmoothedTickTime: Double?,
        val averageTickTimeNanos: Long?,
        val playerCount: Int?,
        val maxPlayers: Int?,
        val serverModName: String?,
        val serverVersion: String?,
    )

    fun buildStatsJson(
        server: MinecraftServer?,
        requestedStats: List<String>,
    ): String = buildStatsJson(server?.toSnapshot(), requestedStats)

    fun buildStatsJson(
        snapshot: Snapshot?,
        requestedStats: List<String>,
    ): String {
        val packet =
            StatsPacket(
                type = "stats",
                tps = if ("tps" in requestedStats) getTPS(snapshot) else null,
                mspt = if ("mspt" in requestedStats) getMSPT(snapshot) else null,
                cpuLoad = if ("cpu" in requestedStats) getCpuLoad() else null,
                ramUsed = if ("ram" in requestedStats) getRamUsed() else null,
                ramMax = if ("ram" in requestedStats) getRamMax() else null,
                uptime = if ("uptime" in requestedStats) getUptime() else null,
                onlinePlayers = if ("players" in requestedStats) snapshot?.playerCount else null,
                maxPlayers = if ("players" in requestedStats) snapshot?.maxPlayers else null,
                serverName = if ("server" in requestedStats) snapshot?.serverModName else null,
                minecraftVersion = if ("server" in requestedStats) snapshot?.serverVersion else null,
            )
        return json.encodeToString(packet)
    }

    fun getTPS(server: MinecraftServer?): Double? = getTPS(server?.toSnapshot())

    fun getTPS(snapshot: Snapshot?): Double? {
        val mspt = snapshot?.currentSmoothedTickTime ?: return null
        if (mspt <= 0.0) return 20.0
        return getTPSFromMspt(mspt)
    }

    fun getMSPT(server: MinecraftServer?): Double? = getMSPT(server?.toSnapshot())

    fun getMSPT(snapshot: Snapshot?): Double? = snapshot?.averageTickTimeNanos?.let(::getMSPTFromNanos)

    fun getRamUsed(): Long = (Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }) / 1024 / 1024

    fun getRamMax(): Long = Runtime.getRuntime().maxMemory() / 1024 / 1024

    fun getCpuLoad(): Double? {
        val bean = ManagementFactory.getOperatingSystemMXBean()
        if (bean !is com.sun.management.OperatingSystemMXBean) return null
        val load = bean.processCpuLoad
        if (load < 0.0) return null
        return roundToOneDecimal((load * 100.0).coerceIn(0.0, 100.0))
    }

    fun getUptime(): Long = ManagementFactory.getRuntimeMXBean().uptime / 1000

    internal fun getTPSFromMspt(mspt: Double): Double = roundToOneDecimal((1000.0 / mspt).coerceAtMost(20.0))

    internal fun getMSPTFromNanos(nanos: Long): Double = roundToOneDecimal(nanos / 1_000_000.0)

    internal fun roundToOneDecimal(value: Double): Double =
        BigDecimal
            .valueOf(value)
            .setScale(1, RoundingMode.HALF_UP)
            .toDouble()

    private fun MinecraftServer.toSnapshot(): Snapshot =
        Snapshot(
            currentSmoothedTickTime = currentSmoothedTickTime.toDouble(),
            averageTickTimeNanos = averageTickTimeNanos,
            playerCount = playerCount,
            maxPlayers = maxPlayers,
            serverModName = serverModName,
            serverVersion = serverVersion,
        )
}
