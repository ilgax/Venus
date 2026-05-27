package dev.ilgax.venus.handlers

import dev.ilgax.venus.stats.StatSubscriptionManager
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
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class StatsHandlerTest {

    @BeforeTest
    fun setup() {
        mockkObject(StatSubscriptionManager)
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

        verify(exactly = 0) { StatSubscriptionManager.subscribe(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handleSubscribe processes valid request`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val sendData: (Player, String) -> Unit = mockk(relaxed = true)
        val json = Json { ignoreUnknownKeys = true }

        val uuid = UUID.randomUUID()
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { player.name } returns "TestPlayer"
        every { player.uniqueId } returns uuid
        every { StatSubscriptionManager.subscribe(any(), any(), any(), any(), any()) } returns Unit

        val handler = StatsHandler(plugin, json, sendData)
        val packetJson = """{"type":"stat_subscribe","stats":["tps"],"intervalSeconds":5}"""
        
        handler.handleSubscribe(player, packetJson)

        verify { StatSubscriptionManager.subscribe(any(), any(), any(), any(), any()) }
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
        every { StatsCollector.buildStatsJson(server, listOf("tps")) } returns """{"type":"stat_data","data":{}}"""

        val handler = StatsHandler(plugin, json, sendData)
        val packetJson = """{"type":"stat_get","stats":["tps"]}"""

        handler.handleGet(player, packetJson)

        verify { sendData(player, """{"type":"stat_data","data":{}}""") }
    }
}
