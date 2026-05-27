package dev.ilgax.venus.channel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PacketRouterTest {
    @Test
    fun `known command packet types map to handlers`() {
        assertEquals(CommandRoute.CONSOLE_CMD, CommandRoute.fromPacketType("console_cmd"))
        assertEquals(CommandRoute.STAT_SUBSCRIBE, CommandRoute.fromPacketType("stat_subscribe"))
        assertEquals(CommandRoute.STAT_GET, CommandRoute.fromPacketType("stat_get"))
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
        val player = io.mockk.mockk<org.bukkit.entity.Player>(relaxed = true)
        val router = PacketRouter(plugin, kotlinx.serialization.json.Json { ignoreUnknownKeys = true }, consoleHandler, statsHandler)

        io.mockk.mockkObject(dev.ilgax.venus.auth.SessionManager)
        io.mockk.every { dev.ilgax.venus.auth.SessionManager.isActive(any()) } returns false

        router.handleCommand(player, """{"type":"console_cmd"}""")
        io.mockk.verify(exactly = 0) { consoleHandler.handle(any(), any()) }

        io.mockk.unmockkAll()
    }

    @Test
    fun `handleCommand ignores malformed json`() {
        val plugin = io.mockk.mockk<org.bukkit.plugin.java.JavaPlugin>(relaxed = true)
        val consoleHandler = io.mockk.mockk<dev.ilgax.venus.handlers.ConsoleHandler>(relaxed = true)
        val statsHandler = io.mockk.mockk<dev.ilgax.venus.handlers.StatsHandler>(relaxed = true)
        val player = io.mockk.mockk<org.bukkit.entity.Player>(relaxed = true)
        val router = PacketRouter(plugin, kotlinx.serialization.json.Json { ignoreUnknownKeys = true }, consoleHandler, statsHandler)

        io.mockk.mockkObject(dev.ilgax.venus.auth.SessionManager)
        io.mockk.every { dev.ilgax.venus.auth.SessionManager.isActive(any()) } returns true

        router.handleCommand(player, """{invalid}""")
        io.mockk.verify(exactly = 0) { consoleHandler.handle(any(), any()) }

        io.mockk.unmockkAll()
    }

    @Test
    fun `handleCommand routes console_cmd`() {
        val plugin = io.mockk.mockk<org.bukkit.plugin.java.JavaPlugin>(relaxed = true)
        val consoleHandler = io.mockk.mockk<dev.ilgax.venus.handlers.ConsoleHandler>(relaxed = true)
        val statsHandler = io.mockk.mockk<dev.ilgax.venus.handlers.StatsHandler>(relaxed = true)
        val player = io.mockk.mockk<org.bukkit.entity.Player>(relaxed = true)
        val router = PacketRouter(plugin, kotlinx.serialization.json.Json { ignoreUnknownKeys = true }, consoleHandler, statsHandler)

        io.mockk.mockkObject(dev.ilgax.venus.auth.SessionManager)
        io.mockk.every { dev.ilgax.venus.auth.SessionManager.isActive(any()) } returns true

        val data = """{"type":"console_cmd"}"""
        router.handleCommand(player, data)
        io.mockk.verify { consoleHandler.handle(player, data) }

        io.mockk.unmockkAll()
    }
}
