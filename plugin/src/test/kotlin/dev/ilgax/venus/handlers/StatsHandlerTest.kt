package dev.ilgax.venus.handlers

import dev.ilgax.venus.stats.StatsCollector
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class StatsHandlerTest {
    @BeforeTest
    fun setup() {
        mockkObject(StatsCollector)
    }

    @AfterTest
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `handleSubscribe ignores malformed json`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val sendData: (Player, String) -> Unit = mockk(relaxed = true)
        val json = Json { ignoreUnknownKeys = true }

        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { player.name } returns "TestPlayer"

        val handler = StatsHandler(plugin, json, sendData)
        handler.handleSubscribe(player, "{ malformed ")

        verify(exactly = 0) { sendData(any(), any()) }
    }

    @Test
    fun `handleSubscribe processes valid request`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        val scheduler = mockk<BukkitScheduler>(relaxed = true)
        val task = mockk<BukkitTask>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val sendData: (Player, String) -> Unit = mockk(relaxed = true)
        val json = Json { ignoreUnknownKeys = true }

        val uuid = UUID.randomUUID()
        every { plugin.server } returns server
        every { server.scheduler } returns scheduler
        every { scheduler.runTaskTimer(any(), any<Runnable>(), any(), any()) } returns task
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { player.name } returns "TestPlayer"
        every { player.uniqueId } returns uuid

        val handler = StatsHandler(plugin, json, sendData)
        val packetJson = """{"type":"stat_subscribe","stats":["tps"],"interval_seconds":5}"""

        handler.handleSubscribe(player, packetJson)

        verify { scheduler.runTaskTimer(plugin, any<Runnable>(), 100L, 100L) }
    }

    @Test
    fun `handleGet ignores malformed json`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val sendData: (Player, String) -> Unit = mockk(relaxed = true)
        val json = Json { ignoreUnknownKeys = true }

        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { player.name } returns "TestPlayer"

        val handler = StatsHandler(plugin, json, sendData)
        handler.handleGet(player, "{ malformed ")

        verify(exactly = 0) { sendData(any(), any()) }
    }

    @Test
    fun `handleGet processes valid request`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val sendData: (Player, String) -> Unit = mockk(relaxed = true)
        val json = Json { ignoreUnknownKeys = true }

        every { plugin.server } returns server
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { player.name } returns "TestPlayer"
        every { player.uniqueId } returns UUID.randomUUID()
        every { server.getPlayer(player.uniqueId) } returns player
        every { StatsCollector.buildStatsJson(server, listOf("tps")) } returns """{"type":"stat_data","data":{}}"""

        val handler = StatsHandler(plugin, json, sendData)
        val packetJson = """{"type":"stat_get","stats":["tps"]}"""

        handler.handleGet(player, packetJson)

        verify { sendData(player, """{"type":"stat_data","data":{}}""") }
    }
}
