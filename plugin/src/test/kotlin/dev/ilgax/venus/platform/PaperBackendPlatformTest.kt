package dev.ilgax.venus.platform

import dev.ilgax.venus.backend.BackendPlayer
import dev.ilgax.venus.protocol.PlayerActionPacket
import io.mockk.every
import io.mockk.mockk
import org.bukkit.Server
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaperBackendPlatformTest {
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
}
