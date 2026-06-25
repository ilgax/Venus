package dev.ilgax.venus.commands

import dev.ilgax.venus.VenusPlugin
import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.backend.BackendApprovalResult
import dev.ilgax.venus.backend.BackendApprovalService
import dev.ilgax.venus.config.VenusConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class VenusCommandTest {
    private lateinit var plugin: VenusPlugin
    private lateinit var approvals: BackendApprovalService
    private lateinit var stack: CommandSourceStack
    private lateinit var consoleSender: ConsoleCommandSender
    private lateinit var playerSender: Player
    private lateinit var command: VenusCommand

    @BeforeTest
    fun setup() {
        plugin = mockk(relaxed = true)
        approvals = mockk(relaxed = true)
        stack = mockk(relaxed = true)
        consoleSender = mockk(relaxed = true)
        playerSender = mockk(relaxed = true)

        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { consoleSender.sendMessage(any<String>()) } returns Unit
        every { playerSender.sendMessage(any<String>()) } returns Unit

        command = VenusCommand(plugin, approvals)

        mockkObject(VenusConfig)
        mockkObject(AuthorizedKeys)
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
        every { approvals.allowNextPending() } returns
            BackendApprovalResult(
                success = false,
                message = "No pending Venus requests.",
            )

        command.execute(stack, arrayOf("allow"))

        verify { consoleSender.sendMessage("No pending Venus requests.") }
    }

    @Test
    fun `deny with no pending requests`() {
        every { stack.sender } returns consoleSender
        every { approvals.denyNextPending() } returns
            BackendApprovalResult(
                success = false,
                message = "No pending Venus requests.",
            )

        command.execute(stack, arrayOf("deny"))

        verify { consoleSender.sendMessage("No pending Venus requests.") }
    }

    @Test
    fun `deny with pending request removes it`() {
        every { stack.sender } returns consoleSender
        every { approvals.denyNextPending() } returns
            BackendApprovalResult(
                success = true,
                message = "DeniedPlayer denied.",
            )

        command.execute(stack, arrayOf("deny"))

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
        every { stack.sender } returns consoleSender
        every { approvals.allowNextPending() } returns
            BackendApprovalResult(
                success = false,
                message = "Player is no longer online.",
            )

        command.execute(stack, arrayOf("allow"))

        verify { consoleSender.sendMessage("Player is no longer online.") }
    }

    @Test
    fun `allow with online player authorizes and starts challenge`() {
        every { stack.sender } returns consoleSender
        every { approvals.allowNextPending() } returns
            BackendApprovalResult(
                success = true,
                message = "TestPlayer authorized.",
            )

        command.execute(stack, arrayOf("allow"))

        verify { consoleSender.sendMessage("TestPlayer authorized.") }
    }

    @Test
    fun `list from console with no keys shows empty message`() {
        every { stack.sender } returns consoleSender
        every { AuthorizedKeys.list() } returns emptyList()

        command.execute(stack, arrayOf("list"))

        verify { consoleSender.sendMessage("No authorized Venus keys.") }
    }

    @Test
    fun `list from console with keys shows fingerprints`() {
        every { stack.sender } returns consoleSender
        every { AuthorizedKeys.list() } returns
            listOf(
                AuthorizedKeys.Entry("key1_b64", "Alice", "SHA256:aaa=="),
                AuthorizedKeys.Entry("key2_b64", "Bob", "SHA256:bbb=="),
            )

        command.execute(stack, arrayOf("list"))

        verify { consoleSender.sendMessage("Authorized Venus keys (2):") }
        verify { consoleSender.sendMessage("  SHA256:aaa==  (claimed: Alice)") }
        verify { consoleSender.sendMessage("  SHA256:bbb==  (claimed: Bob)") }
    }

    @Test
    fun `list from non-console shows error`() {
        every { stack.sender } returns playerSender

        command.execute(stack, arrayOf("list"))

        verify { playerSender.sendMessage("This command can only be run from the console.") }
    }

    @Test
    fun `revoke by fingerprint removes key`() {
        every { stack.sender } returns consoleSender
        every { AuthorizedKeys.removeByFingerprint("SHA256:abc==") } returns true

        command.execute(stack, arrayOf("revoke", "SHA256:abc=="))

        verify { consoleSender.sendMessage("Revoked Venus key SHA256:abc==.") }
    }

    @Test
    fun `revoke by player name is rejected`() {
        every { stack.sender } returns consoleSender

        command.execute(stack, arrayOf("revoke", "Alice"))

        verify {
            consoleSender.sendMessage(
                match<String> {
                    it.contains("Revocation by name is not supported") && it.contains("spoofable")
                },
            )
        }
    }

    @Test
    fun `revoke with unknown fingerprint shows not found`() {
        every { stack.sender } returns consoleSender
        every { AuthorizedKeys.removeByFingerprint("SHA256:nonexistent==") } returns false

        command.execute(stack, arrayOf("revoke", "SHA256:nonexistent=="))

        verify { consoleSender.sendMessage("No authorized Venus key found for SHA256:nonexistent==.") }
    }

    @Test
    fun `revoke with no args shows usage`() {
        every { stack.sender } returns consoleSender

        command.execute(stack, arrayOf("revoke"))

        verify { consoleSender.sendMessage("Usage: venus revoke <fingerprint>") }
    }

    @Test
    fun `revoke from non-console shows error`() {
        every { stack.sender } returns playerSender

        command.execute(stack, arrayOf("revoke", "Alice"))

        verify { playerSender.sendMessage("This command can only be run from the console.") }
    }
}
