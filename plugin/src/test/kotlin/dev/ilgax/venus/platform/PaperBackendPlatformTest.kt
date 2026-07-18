package dev.ilgax.venus.platform

import dev.ilgax.venus.backend.BackendPlayer
import dev.ilgax.venus.protocol.PlayerActionPacket
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.JsonPrimitive
import org.bukkit.GameMode
import org.bukkit.OfflinePlayer
import org.bukkit.Server
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaperBackendPlatformTest {
    @Test
    fun `list returns complete empty snapshot`() {
        val plugin = mockk<JavaPlugin>()
        val server = mockk<Server>()
        every { plugin.server } returns server
        every { server.bannedPlayers } returns emptySet()
        every { server.onlinePlayers } returns emptyList()
        every { server.whitelistedPlayers } returns emptySet()
        every { server.maxPlayers } returns 100

        val snapshot = PaperBackendPlatform(plugin).players().list(BackendPlayer(UUID.randomUUID(), "viewer"))

        assertEquals(0, snapshot.onlineCount)
        assertEquals(100, snapshot.maxPlayers)
        assertTrue(snapshot.onlinePlayers.isEmpty())
        assertTrue(snapshot.whitelistedPlayers.isEmpty())
        assertTrue(snapshot.blockedPlayers.isEmpty())
    }

    @Test
    fun `detail returns null for completely unknown uuid`() {
        val plugin = mockk<JavaPlugin>()
        val server = mockk<Server>()
        every { plugin.server } returns server
        every { server.getPlayer(any<UUID>()) } returns null
        every { server.whitelistedPlayers } returns emptySet()
        every { server.bannedPlayers } returns emptySet()
        every { server.offlinePlayers } returns emptyArray()

        val platform = PaperBackendPlatform(plugin)

        val detail = platform.players().detail(BackendPlayer(UUID.randomUUID(), "viewer"), UUID.randomUUID())

        assertNull(detail)
    }

    @Test
    fun `kill action dispatches kill command through console`() {
        val plugin = mockk<JavaPlugin>()
        val server = mockk<Server>()
        val viewer = mockk<Player>()
        val target = mockk<Player>()
        val console = mockk<ConsoleCommandSender>()
        val viewerUuid = UUID.randomUUID()
        val targetUuid = UUID.randomUUID()
        every { plugin.server } returns server
        every { server.consoleSender } returns console
        every { viewer.uniqueId } returns viewerUuid
        every { viewer.name } returns "Viewer"
        every { target.uniqueId } returns targetUuid
        every { target.name } returns "Target"
        every { target.player } returns target
        every { server.getPlayer(viewerUuid) } returns viewer
        every { server.getPlayer(targetUuid) } returns target
        every { server.dispatchCommand(console, "kill Target") } returns true

        val platform = PaperBackendPlatform(plugin)

        val result =
            platform.players().applyAction(
                BackendPlayer(viewerUuid, "Viewer"),
                PlayerActionPacket(
                    type = "player_action",
                    requestId = "req-1",
                    uuid = targetUuid.toString(),
                    action = "kill",
                    value = kotlinx.serialization.json.JsonPrimitive(true),
                ),
            )

        assertEquals(true, result.success)
        assertEquals("Player killed.", result.message)
    }

    @Test
    fun `invalid action uuid is rejected before player lookup`() {
        val plugin = mockk<JavaPlugin>()
        every { plugin.server } returns mockk(relaxed = true)

        val result =
            PaperBackendPlatform(plugin).players().applyAction(
                BackendPlayer(UUID.randomUUID(), "Viewer"),
                PlayerActionPacket("player_action", "req", "invalid", "heal"),
            )

        assertFalse(result.success)
        assertEquals("Invalid player uuid.", result.message)
    }

    @Test
    fun `unknown action target is rejected`() {
        val plugin = mockk<JavaPlugin>()
        val server = mockk<Server>()
        val targetUuid = UUID.randomUUID()
        every { plugin.server } returns server
        every { server.getPlayer(targetUuid) } returns null
        every { server.whitelistedPlayers } returns emptySet()
        every { server.bannedPlayers } returns emptySet()
        every { server.getOfflinePlayer(targetUuid) } throws IllegalStateException("unknown")

        val result =
            PaperBackendPlatform(plugin).players().applyAction(
                BackendPlayer(UUID.randomUUID(), "Viewer"),
                PlayerActionPacket("player_action", "req", targetUuid.toString(), "heal"),
            )

        assertFalse(result.success)
        assertEquals("Player not found.", result.message)
    }

    @Test
    fun `whitelist action updates resolved offline player`() {
        val plugin = mockk<JavaPlugin>()
        val server = mockk<Server>()
        val viewer = mockk<Player>()
        val target = mockk<OfflinePlayer>(relaxed = true)
        val viewerUuid = UUID.randomUUID()
        val targetUuid = UUID.randomUUID()
        every { plugin.server } returns server
        every { server.getPlayer(targetUuid) } returns null
        every { server.getPlayer(viewerUuid) } returns viewer
        every { server.whitelistedPlayers } returns emptySet()
        every { server.bannedPlayers } returns emptySet()
        every { server.getOfflinePlayer(targetUuid) } returns target
        every { target.isWhitelisted = any() } just Runs

        val result =
            PaperBackendPlatform(plugin).players().applyAction(
                BackendPlayer(viewerUuid, "Viewer"),
                PlayerActionPacket(
                    "player_action",
                    "req",
                    targetUuid.toString(),
                    "set_whitelisted",
                    kotlinx.serialization.json.JsonPrimitive(true),
                ),
            )

        assertTrue(result.success)
        assertEquals("Player whitelisted.", result.message)
        verify { target.isWhitelisted = true }
    }

    @Test
    fun `offline target rejects online actions and invalid values`() {
        val plugin = mockk<JavaPlugin>()
        val server = mockk<Server>()
        val viewer = mockk<Player>()
        val target = mockk<OfflinePlayer>()
        val viewerUuid = UUID.randomUUID()
        val targetUuid = UUID.randomUUID()
        every { plugin.server } returns server
        every { server.getPlayer(viewerUuid) } returns viewer
        every { server.getPlayer(targetUuid) } returns null
        every { server.whitelistedPlayers } returns setOf(target)
        every { server.bannedPlayers } returns emptySet()
        every { target.uniqueId } returns targetUuid
        every { target.player } returns null
        val players = PaperBackendPlatform(plugin).players()
        val backendViewer = BackendPlayer(viewerUuid, "Viewer")

        listOf("kick", "heal", "feed", "teleport_admin_to_player", "teleport_player_to_admin").forEach { action ->
            val result = players.applyAction(backendViewer, PlayerActionPacket("player_action", action, targetUuid.toString(), action))
            assertFalse(result.success)
            assertEquals("Player must be online.", result.message)
        }

        val invalidActions =
            listOf(
                "set_whitelisted" to "Invalid whitelist value.",
                "set_blocked" to "Invalid blocked value.",
                "set_operator" to "Invalid operator value.",
                "set_game_mode" to "Invalid game mode.",
            )
        invalidActions.forEach { (action, message) ->
            val result =
                players.applyAction(
                    backendViewer,
                    PlayerActionPacket("player_action", action, targetUuid.toString(), action, JsonPrimitive("invalid")),
                )
            assertFalse(result.success)
            assertEquals(message, result.message)
        }

        val unknown =
            players.applyAction(
                backendViewer,
                PlayerActionPacket("player_action", "unknown", targetUuid.toString(), "future_action"),
            )
        assertFalse(unknown.success)
        assertEquals("Unknown player action.", unknown.message)
    }

    @Test
    fun `online actions feed operate and change game mode`() {
        val plugin = mockk<JavaPlugin>()
        val server = mockk<Server>()
        val viewer = mockk<Player>()
        val target = mockk<Player>()
        val viewerUuid = UUID.randomUUID()
        val targetUuid = UUID.randomUUID()
        every { plugin.server } returns server
        every { server.getPlayer(viewerUuid) } returns viewer
        every { server.getPlayer(targetUuid) } returns target
        every { target.player } returns target
        every { target.foodLevel = any() } just Runs
        every { target.isOp = any() } just Runs
        every { target.gameMode = any() } just Runs
        val players = PaperBackendPlatform(plugin).players()
        val backendViewer = BackendPlayer(viewerUuid, "Viewer")

        val fed = players.applyAction(backendViewer, PlayerActionPacket("player_action", "feed", targetUuid.toString(), "feed"))
        val operated =
            players.applyAction(
                backendViewer,
                PlayerActionPacket("player_action", "op", targetUuid.toString(), "set_operator", JsonPrimitive(true)),
            )
        val creative =
            players.applyAction(
                backendViewer,
                PlayerActionPacket("player_action", "mode", targetUuid.toString(), "set_game_mode", JsonPrimitive("creative")),
            )

        assertTrue(fed.success)
        assertTrue(operated.success)
        assertTrue(creative.success)
        verify { target.foodLevel = 20 }
        verify { target.isOp = true }
        verify { target.gameMode = GameMode.CREATIVE }
    }
}
