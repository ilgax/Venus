package dev.ilgax.venus.platform

import dev.ilgax.venus.protocol.StatsPacket
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FabricStatsCollectorTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `buildStatsJson includes rounded tps and mspt`() {
        val snapshot =
            FabricStatsCollector.Snapshot(
                currentSmoothedTickTime = 50.0,
                averageTickTimeNanos = 15_430_000L,
                playerCount = 3,
                maxPlayers = 20,
                serverModName = "Fabric",
                serverVersion = "1.21.11",
            )

        val packet =
            json.decodeFromString<StatsPacket>(
                FabricStatsCollector.buildStatsJson(
                    snapshot,
                    listOf("tps", "mspt", "players", "server"),
                ),
            )

        assertEquals(20.0, packet.tps)
        assertEquals(15.4, packet.mspt)
        assertEquals(3, packet.onlinePlayers)
        assertEquals(20, packet.maxPlayers)
        assertEquals("Fabric", packet.serverName)
        assertEquals("1.21.11", packet.minecraftVersion)
    }

    @Test
    fun `getTPS rounds and caps at twenty`() {
        assertEquals(20.0, FabricStatsCollector.getTPSFromMspt(33.333))
    }

    @Test
    fun `getMSPT converts nanos to milliseconds`() {
        assertEquals(50.6, FabricStatsCollector.getMSPTFromNanos(50_550_000L))
    }
}
