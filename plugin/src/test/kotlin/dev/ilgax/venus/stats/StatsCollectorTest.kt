package dev.ilgax.venus.stats

import dev.ilgax.venus.protocol.StatsPacket
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsCollectorTest {
    @Test
    fun `stat precision is limited to one decimal`() {
        assertEquals(19.8, StatsCollector.roundToOneDecimal(19.84))
        assertEquals(19.9, StatsCollector.roundToOneDecimal(19.85))
        assertEquals(20.0, StatsCollector.roundToOneDecimal(20.0))
        assertEquals(2.4, StatsCollector.roundToOneDecimal(2.449))
    }

    @Test
    fun `getTPS clamps to 20`() {
        val server = io.mockk.mockk<org.bukkit.Server>()
        io.mockk.every { server.tps } returns doubleArrayOf(21.5)
        assertEquals(20.0, StatsCollector.getTPS(server))
    }

    @Test
    fun `getTPS handles missing tps`() {
        val server = io.mockk.mockk<org.bukkit.Server>()
        io.mockk.every { server.tps } returns DoubleArray(0)
        assertEquals(20.0, StatsCollector.getTPS(server))
    }

    @Test
    fun `getMSPT returns averageTickTime`() {
        val server = io.mockk.mockk<org.bukkit.Server>()
        io.mockk.every { server.averageTickTime } returns 15.4
        assertEquals(15.4, StatsCollector.getMSPT(server))
    }

    @Test
    fun `buildStatsJson includes only requested stats`() {
        val server = io.mockk.mockk<org.bukkit.Server>()
        io.mockk.every { server.tps } returns doubleArrayOf(20.0)
        io.mockk.every { server.averageTickTime } returns 15.0

        val json = StatsCollector.buildStatsJson(server, listOf("tps", "mspt"))

        val packet = Json.decodeFromString<StatsPacket>(json)

        assertEquals("stats", packet.type)
        assertEquals(20.0, packet.tps)
        assertEquals(15.0, packet.mspt)
        assertNull(packet.ramMax)
        assertNull(packet.ramUsed)
        assertNull(packet.uptime)
        assertNull(packet.cpuLoad)
        assertNull(packet.onlinePlayers)
        assertNull(packet.serverName)
    }

    @Test
    fun `buildStatsJson includes requested player counts`() {
        val server = io.mockk.mockk<org.bukkit.Server>()
        val player = io.mockk.mockk<org.bukkit.entity.Player>()
        io.mockk.every { server.onlinePlayers } returns listOf(player, player, player)
        io.mockk.every { server.maxPlayers } returns 20

        val json = StatsCollector.buildStatsJson(server, listOf("players"))

        assertTrue(json.contains(""""online_players":3"""))
        assertTrue(json.contains(""""max_players":20"""))
        assertTrue(!json.contains("server_name"))
    }

    @Test
    fun `buildStatsJson includes requested cpu stats`() {
        val server = io.mockk.mockk<org.bukkit.Server>()

        val json = StatsCollector.buildStatsJson(server, listOf("cpu"))
        val packet = Json.decodeFromString<StatsPacket>(json)
        val cpuLoad = packet.cpuLoad

        assertEquals("stats", packet.type)
        assertTrue(cpuLoad == null || cpuLoad in 0.0..100.0)
        assertNull(packet.onlinePlayers)
        assertNull(packet.serverName)
    }

    @Test
    fun `buildStatsJson includes requested server identity`() {
        val server = io.mockk.mockk<org.bukkit.Server>()
        io.mockk.every { server.name } returns "Paper"
        io.mockk.every { server.minecraftVersion } returns "1.21.11"

        val json = StatsCollector.buildStatsJson(server, listOf("server"))

        assertTrue(json.contains(""""server_name":"Paper""""))
        assertTrue(json.contains(""""minecraft_version":"1.21.11""""))
        assertTrue(!json.contains("online_players"))
        assertTrue(!json.contains("cpu_load"))
    }
}
