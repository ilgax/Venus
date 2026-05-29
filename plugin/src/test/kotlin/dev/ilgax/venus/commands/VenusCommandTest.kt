package dev.ilgax.venus.commands

import dev.ilgax.venus.VenusPlugin
import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.config.VenusConfig
import dev.ilgax.venus.handlers.AuthHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Server
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class VenusCommandTest {
    private lateinit var plugin: VenusPlugin
    private lateinit var authHandler: AuthHandler
    private lateinit var server: Server
    private lateinit var stack: CommandSourceStack
    private lateinit var consoleSender: ConsoleCommandSender
    private lateinit var playerSender: Player
    private lateinit var command: VenusCommand

    @BeforeTest
    fun setup() {
        plugin = mockk(relaxed = true)
        authHandler = mockk(relaxed = true)
        server = mockk(relaxed = true)
        stack = mockk(relaxed = true)
        consoleSender = mockk(relaxed = true)
        playerSender = mockk(relaxed = true)

        every { plugin.server } returns server
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { consoleSender.sendMessage(any<String>()) } returns Unit
        every { playerSender.sendMessage(any<String>()) } returns Unit

        command = VenusCommand(plugin, authHandler)

        mockkObject(SessionManager)
        mockkObject(AuthorizedKeys)
        mockkObject(VenusConfig)
    }

    @AfterTest
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `execute with empty args shows usage`() {
        every { stack.sender } returns consoleSender
        command.execute(stack, emptyArray())
        verify { consoleSender.sendMessage(match<String> { it.contains("Usage:") }) }
    }

    @Test
    fun `allow and deny from non-console shows error`() {
        every { stack.sender } returns playerSender

        command.execute(stack, arrayOf("allow"))
        verify { playerSender.sendMessage("This command can only be run from the console.") }

        command.execute(stack, arrayOf("deny"))
        verify(exactly = 2) { playerSender.sendMessage("This command can only be run from the console.") }
    }

    @Test
    fun `reload executes successfully from console`() {
        every { stack.sender } returns consoleSender
        every { VenusConfig.load(plugin) } returns Unit

        command.execute(stack, arrayOf("reload"))

        verify { VenusConfig.load(plugin) }
        verify { consoleSender.sendMessage("Venus config reloaded.") }
    }

    @Test
    fun `reload from player with permission succeeds`() {
        every { stack.sender } returns playerSender
        every { playerSender.hasPermission("venus.reload") } returns true
        every { VenusConfig.load(plugin) } returns Unit
        every { playerSender.name } returns "Admin"

        command.execute(stack, arrayOf("reload"))

        verify { VenusConfig.load(plugin) }
        verify { playerSender.sendMessage("Venus config reloaded.") }
    }

    @Test
    fun `allow with no pending requests`() {
        every { stack.sender } returns consoleSender
        every { SessionManager.getNextPendingApproval() } returns null

        command.execute(stack, arrayOf("allow"))

        verify { consoleSender.sendMessage("No pending Venus requests.") }
    }

    @Test
    fun `deny with no pending requests`() {
        every { stack.sender } returns consoleSender
        every { SessionManager.getNextPendingApproval() } returns null

        command.execute(stack, arrayOf("deny"))

        verify { consoleSender.sendMessage("No pending Venus requests.") }
    }

    @Test
    fun `deny with pending request removes it`() {
        val uuid = UUID.randomUUID()
        val approval = mockk<dev.ilgax.venus.auth.PendingApproval>()
        val player = mockk<Player>(relaxed = true)
        every { stack.sender } returns consoleSender
        every { SessionManager.getNextPendingApproval() } returns java.util.AbstractMap.SimpleEntry(uuid, approval)
        every { server.getPlayer(uuid) } returns player
        every { player.name } returns "DeniedPlayer"
        every { SessionManager.removePendingApproval(uuid) } returns approval

        command.execute(stack, arrayOf("deny"))

        verify { SessionManager.removePendingApproval(uuid) }
        verify { authHandler.notifyDenied(player) }
        verify { consoleSender.sendMessage("DeniedPlayer denied.") }
    }

    @Test
    fun `unknown subcommand shows error`() {
        every { stack.sender } returns consoleSender
        command.execute(stack, arrayOf("unknown"))
        verify { consoleSender.sendMessage(match<String> { it.contains("Unknown subcommand") }) }
    }

    @Test
    fun `reload without permission shows error`() {
        every { stack.sender } returns playerSender
        every { playerSender.hasPermission("venus.reload") } returns false
        command.execute(stack, arrayOf("reload"))
        verify { playerSender.sendMessage("You don't have permission to use this command.") }
    }

    @Test
    fun `allow with offline player removes approval`() {
        val uuid = UUID.randomUUID()
        val approval = mockk<dev.ilgax.venus.auth.PendingApproval>()
        every { stack.sender } returns consoleSender
        every { SessionManager.getNextPendingApproval() } returns java.util.AbstractMap.SimpleEntry(uuid, approval)
        every { server.getPlayer(uuid) } returns null
        every { SessionManager.removePendingApproval(uuid) } returns approval

        command.execute(stack, arrayOf("allow"))

        verify { SessionManager.removePendingApproval(uuid) }
        verify { consoleSender.sendMessage("Player is no longer online.") }
    }

    @Test
    fun `allow with online player authorizes and starts challenge`() {
        val uuid = UUID.randomUUID()
        val approval = mockk<dev.ilgax.venus.auth.PendingApproval>()
        val mockPlayer = mockk<Player>()
        val pubKeyBytes = mockk<java.security.PublicKey>()

        every { stack.sender } returns consoleSender
        every { SessionManager.getNextPendingApproval() } returns java.util.AbstractMap.SimpleEntry(uuid, approval)
        every { server.getPlayer(uuid) } returns mockPlayer
        every { mockPlayer.name } returns "TestPlayer"
        every { approval.clientPublicKeyBase64 } returns "base64key"
        every { approval.clientPublicKey } returns pubKeyBytes
        every { SessionManager.removePendingApproval(uuid) } returns approval
        every { AuthorizedKeys.authorize(any(), any()) } returns Unit

        command.execute(stack, arrayOf("allow"))

        verify { AuthorizedKeys.authorize("base64key", "TestPlayer") }
        verify { SessionManager.removePendingApproval(uuid) }
        verify { authHandler.startApprovedChallenge(mockPlayer, pubKeyBytes) }
        verify { consoleSender.sendMessage("TestPlayer authorized.") }
    }
}
