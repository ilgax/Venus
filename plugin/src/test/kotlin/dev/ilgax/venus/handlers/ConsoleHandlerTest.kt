package dev.ilgax.venus.handlers

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.bukkit.Server
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.Test

class ConsoleHandlerTest {
    @Test
    fun `handle ignores malformed json`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val sendData: (Player, String) -> Unit = mockk(relaxed = true)
        val json = Json { ignoreUnknownKeys = true }

        every { plugin.server } returns server
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { player.name } returns "TestPlayer"

        val handler = ConsoleHandler(plugin, json, sendData)
        handler.handle(player, "{ malformed ")

        verify(exactly = 0) { server.dispatchCommand(any(), any()) }
        verify(exactly = 0) { sendData(any(), any()) }
    }

    @Test
    fun `handle dispatches valid command`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val sender = mockk<CommandSender>(relaxed = true)
        val sendData: (Player, String) -> Unit = mockk(relaxed = true)
        val suppressOwnExecutionLog: (UUID, String) -> Unit = mockk(relaxed = true)
        val json = Json { ignoreUnknownKeys = true }
        val uuid = UUID.randomUUID()

        every { plugin.server } returns server
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { player.name } returns "TestPlayer"
        every { player.uniqueId } returns uuid

        // Mock the createCommandSender behavior
        every { server.createCommandSender(any()) } returns sender
        every { server.dispatchCommand(sender, "say hello") } returns true

        val handler = ConsoleHandler(plugin, json, sendData, suppressOwnExecutionLog)

        val packetJson = """{"type":"console_cmd","command":"say hello"}"""
        handler.handle(player, packetJson)

        verify { server.dispatchCommand(sender, "say hello") }
        verify { suppressOwnExecutionLog(uuid, "TestPlayer executed console command: say hello") }
    }

    @Test
    fun `handle returns unknown command message when dispatch fails without output`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val sender = mockk<CommandSender>(relaxed = true)
        val sent = mutableListOf<String>()
        val json = Json { ignoreUnknownKeys = true }

        every { plugin.server } returns server
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { player.name } returns "TestPlayer"
        every { server.createCommandSender(any()) } returns sender
        every { server.dispatchCommand(sender, "nope") } returns false

        val handler = ConsoleHandler(plugin, json, { _, data -> sent.add(data) })

        handler.handle(player, """{"type":"console_cmd","command":"nope"}""")

        kotlin.test.assertTrue(sent.single().contains("Unknown command"))
    }
}
