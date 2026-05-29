package dev.ilgax.venus.channel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PacketRouterTest {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @Test
    fun `known command packet types map to handlers`() {
        assertEquals(CommandRoute.CONSOLE_CMD, CommandRoute.fromPacketType("console_cmd"))
        assertEquals(CommandRoute.LOG_SUBSCRIBE, CommandRoute.fromPacketType("log_subscribe"))
        assertEquals(CommandRoute.STAT_SUBSCRIBE, CommandRoute.fromPacketType("stat_subscribe"))
        assertEquals(CommandRoute.STAT_GET, CommandRoute.fromPacketType("stat_get"))
        assertEquals(CommandRoute.PLAYER_LIST_GET, CommandRoute.fromPacketType("player_list_get"))
        assertEquals(CommandRoute.PLAYER_DETAIL_GET, CommandRoute.fromPacketType("player_detail_get"))
        assertEquals(CommandRoute.PLAYER_ACTION, CommandRoute.fromPacketType("player_action"))
    }

    @Test
    fun `unknown command packet type has no route`() {
        assertNull(CommandRoute.fromPacketType("file_get"))
    }

    @Test
    fun `handleCommand ignores packet if no active session`() {
        val plugin = io.mockk.mockk<org.bukkit.plugin.java.JavaPlugin>(relaxed = true)
        val consoleHandler = io.mockk.mockk<dev.ilgax.venus.handlers.ConsoleHandler>(relaxed = true)
        val statsHandler = io.mockk.mockk<dev.ilgax.venus.handlers.StatsHandler>(relaxed = true)
        val logHandler = io.mockk.mockk<dev.ilgax.venus.handlers.LogHandler>(relaxed = true)
        val playersHandler = io.mockk.mockk<dev.ilgax.venus.handlers.PlayersHandler>(relaxed = true)
        val player = io.mockk.mockk<org.bukkit.entity.Player>(relaxed = true)
        val router = createRouter(plugin, consoleHandler, statsHandler, logHandler, playersHandler)

        io.mockk.mockkObject(dev.ilgax.venus.auth.SessionManager)
        io.mockk.every {
            dev.ilgax.venus.auth.SessionManager
                .isActive(any())
        } returns false

        router.handleCommand(player, """{"type":"console_cmd"}""")
        io.mockk.verify(exactly = 0) { consoleHandler.handle(any(), any()) }

        io.mockk.unmockkAll()
    }

    @Test
    fun `handleCommand ignores malformed json`() {
        val plugin = io.mockk.mockk<org.bukkit.plugin.java.JavaPlugin>(relaxed = true)
        val consoleHandler = io.mockk.mockk<dev.ilgax.venus.handlers.ConsoleHandler>(relaxed = true)
        val statsHandler = io.mockk.mockk<dev.ilgax.venus.handlers.StatsHandler>(relaxed = true)
        val logHandler = io.mockk.mockk<dev.ilgax.venus.handlers.LogHandler>(relaxed = true)
        val playersHandler = io.mockk.mockk<dev.ilgax.venus.handlers.PlayersHandler>(relaxed = true)
        val player = io.mockk.mockk<org.bukkit.entity.Player>(relaxed = true)
        val router = createRouter(plugin, consoleHandler, statsHandler, logHandler, playersHandler)

        io.mockk.mockkObject(dev.ilgax.venus.auth.SessionManager)
        io.mockk.every {
            dev.ilgax.venus.auth.SessionManager
                .isActive(any())
        } returns true

        router.handleCommand(player, """{invalid}""")
        io.mockk.verify(exactly = 0) { consoleHandler.handle(any(), any()) }

        io.mockk.unmockkAll()
    }

    @Test
    fun `handleCommand routes console_cmd`() {
        val plugin = io.mockk.mockk<org.bukkit.plugin.java.JavaPlugin>(relaxed = true)
        val consoleHandler = io.mockk.mockk<dev.ilgax.venus.handlers.ConsoleHandler>(relaxed = true)
        val statsHandler = io.mockk.mockk<dev.ilgax.venus.handlers.StatsHandler>(relaxed = true)
        val logHandler = io.mockk.mockk<dev.ilgax.venus.handlers.LogHandler>(relaxed = true)
        val playersHandler = io.mockk.mockk<dev.ilgax.venus.handlers.PlayersHandler>(relaxed = true)
        val player = io.mockk.mockk<org.bukkit.entity.Player>(relaxed = true)
        val router = createRouter(plugin, consoleHandler, statsHandler, logHandler, playersHandler)

        io.mockk.mockkObject(dev.ilgax.venus.auth.SessionManager)
        io.mockk.every {
            dev.ilgax.venus.auth.SessionManager
                .isActive(any())
        } returns true

        val data = """{"type":"console_cmd"}"""
        router.handleCommand(player, data)
        io.mockk.verify { consoleHandler.handle(player, data) }

        io.mockk.unmockkAll()
    }

    @Test
    fun `handleCommand routes log_subscribe`() {
        val plugin = io.mockk.mockk<org.bukkit.plugin.java.JavaPlugin>(relaxed = true)
        val consoleHandler = io.mockk.mockk<dev.ilgax.venus.handlers.ConsoleHandler>(relaxed = true)
        val statsHandler = io.mockk.mockk<dev.ilgax.venus.handlers.StatsHandler>(relaxed = true)
        val logHandler = io.mockk.mockk<dev.ilgax.venus.handlers.LogHandler>(relaxed = true)
        val playersHandler = io.mockk.mockk<dev.ilgax.venus.handlers.PlayersHandler>(relaxed = true)
        val player = io.mockk.mockk<org.bukkit.entity.Player>(relaxed = true)
        val router = createRouter(plugin, consoleHandler, statsHandler, logHandler, playersHandler)

        io.mockk.mockkObject(dev.ilgax.venus.auth.SessionManager)
        io.mockk.every {
            dev.ilgax.venus.auth.SessionManager
                .isActive(any())
        } returns true

        val data = """{"type":"log_subscribe"}"""
        router.handleCommand(player, data)
        io.mockk.verify { logHandler.handleSubscribe(player, data) }

        io.mockk.unmockkAll()
    }

    @Test
    fun `handleCommand routes player_list_get`() {
        val plugin = io.mockk.mockk<org.bukkit.plugin.java.JavaPlugin>(relaxed = true)
        val consoleHandler = io.mockk.mockk<dev.ilgax.venus.handlers.ConsoleHandler>(relaxed = true)
        val statsHandler = io.mockk.mockk<dev.ilgax.venus.handlers.StatsHandler>(relaxed = true)
        val logHandler = io.mockk.mockk<dev.ilgax.venus.handlers.LogHandler>(relaxed = true)
        val playersHandler = io.mockk.mockk<dev.ilgax.venus.handlers.PlayersHandler>(relaxed = true)
        val player = io.mockk.mockk<org.bukkit.entity.Player>(relaxed = true)
        val router = createRouter(plugin, consoleHandler, statsHandler, logHandler, playersHandler)

        io.mockk.mockkObject(dev.ilgax.venus.auth.SessionManager)
        io.mockk.every {
            dev.ilgax.venus.auth.SessionManager
                .isActive(any())
        } returns true

        val data = """{"type":"player_list_get"}"""
        router.handleCommand(player, data)
        io.mockk.verify { playersHandler.handleListGet(player, data) }

        io.mockk.unmockkAll()
    }

    @Test
    fun `handleCommand routes player_action`() {
        val plugin = io.mockk.mockk<org.bukkit.plugin.java.JavaPlugin>(relaxed = true)
        val consoleHandler = io.mockk.mockk<dev.ilgax.venus.handlers.ConsoleHandler>(relaxed = true)
        val statsHandler = io.mockk.mockk<dev.ilgax.venus.handlers.StatsHandler>(relaxed = true)
        val logHandler = io.mockk.mockk<dev.ilgax.venus.handlers.LogHandler>(relaxed = true)
        val playersHandler = io.mockk.mockk<dev.ilgax.venus.handlers.PlayersHandler>(relaxed = true)
        val player = io.mockk.mockk<org.bukkit.entity.Player>(relaxed = true)
        val router = createRouter(plugin, consoleHandler, statsHandler, logHandler, playersHandler)

        io.mockk.mockkObject(dev.ilgax.venus.auth.SessionManager)
        io.mockk.every {
            dev.ilgax.venus.auth.SessionManager
                .isActive(any())
        } returns true

        val data = """{"type":"player_action"}"""
        router.handleCommand(player, data)
        io.mockk.verify { playersHandler.handleAction(player, data) }

        io.mockk.unmockkAll()
    }

    private fun createRouter(
        plugin: org.bukkit.plugin.java.JavaPlugin,
        consoleHandler: dev.ilgax.venus.handlers.ConsoleHandler,
        statsHandler: dev.ilgax.venus.handlers.StatsHandler,
        logHandler: dev.ilgax.venus.handlers.LogHandler,
        playersHandler: dev.ilgax.venus.handlers.PlayersHandler,
    ): PacketRouter =
        PacketRouter(
            plugin,
            json,
            consoleHandler,
            statsHandler,
            logHandler,
            playersHandler,
        )
}
