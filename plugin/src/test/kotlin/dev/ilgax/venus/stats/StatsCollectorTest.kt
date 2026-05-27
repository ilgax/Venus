package dev.ilgax.venus.stats

import kotlin.test.Test
import kotlin.test.assertEquals

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

        val requestedStats = listOf("tps", "mspt")
        val json = StatsCollector.buildStatsJson(server, requestedStats)

        kotlin.test.assertTrue(json.contains(""""type":"stats""""))
        kotlin.test.assertTrue(json.contains(""""tps":20.0"""))
        kotlin.test.assertTrue(json.contains(""""mspt":15.0"""))
        kotlin.test.assertTrue(!json.contains("ramMax"))
        kotlin.test.assertTrue(!json.contains("ramUsed"))
        kotlin.test.assertTrue(!json.contains("uptime"))
    }
}
