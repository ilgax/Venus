package dev.ilgax.venus.handlers

import dev.ilgax.venus.protocol.PlayerActionResultPacket
import dev.ilgax.venus.protocol.PlayerDetailPacket
import dev.ilgax.venus.protocol.PlayerListPacket
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayersHandlerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `handleListGet ignores malformed json`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val sendData: (Player, String) -> Unit = mockk(relaxed = true)

        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { player.name } returns "Viewer"

        val handler = PlayersHandler(plugin, json, sendData)
        handler.handleListGet(player, "{ malformed ")

        verify(exactly = 0) { sendData(any(), any()) }
    }

    @Test
    fun `handleListGet sends grouped player snapshot`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        val viewer = mockk<Player>(relaxed = true)
        val sendCalls = mutableListOf<String>()

        val onlinePlayer =
            mockPlayer(
                name = "Alice",
                uuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            )
        val whitelistedPlayer =
            mockOfflinePlayer(
                name = "Bob",
                uuid = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                whitelisted = true,
            )
        val bannedPlayer =
            mockOfflinePlayer(
                name = "Carol",
                uuid = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
            )

        every { plugin.server } returns server
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { server.onlinePlayers } returns listOf(onlinePlayer)
        every { server.maxPlayers } returns 20
        every { server.whitelistedPlayers } returns linkedSetOf(whitelistedPlayer)
        every { server.bannedPlayers } returns linkedSetOf(bannedPlayer)

        val handler = PlayersHandler(plugin, json) { _, data -> sendCalls.add(data) }
        handler.handleListGet(viewer, """{"type":"player_list_get"}""")

        val packet = json.decodeFromString(PlayerListPacket.serializer(), sendCalls.single())
        assertEquals(1, packet.onlineCount)
        assertEquals(20, packet.maxPlayers)
        assertEquals(listOf("Alice"), packet.onlinePlayers.map { it.name })
        assertEquals(listOf("Bob"), packet.whitelistedPlayers.map { it.name })
        assertEquals(listOf("Carol"), packet.blockedPlayers.map { it.name })
        assertEquals(true, packet.whitelistedPlayers.single().whitelisted)
        assertEquals(true, packet.blockedPlayers.single().blocked)
    }

    @Test
    fun `handleDetailGet sends online player detail`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        val viewer = mockk<Player>(relaxed = true)
        val sendCalls = mutableListOf<String>()
        val targetUuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val target = mockPlayer(name = "Alice", uuid = targetUuid)

        every { plugin.server } returns server
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { server.getPlayer(targetUuid) } returns target
        every { server.whitelistedPlayers } returns emptySet()
        every { server.bannedPlayers } returns emptySet()
        every { server.offlinePlayers } returns emptyArray()

        val handler = PlayersHandler(plugin, json) { _, data -> sendCalls.add(data) }
        handler.handleDetailGet(viewer, """{"type":"player_detail_get","uuid":"$targetUuid"}""")

        val packet = json.decodeFromString(PlayerDetailPacket.serializer(), sendCalls.single())
        assertEquals("Alice", packet.player.name)
        assertEquals("survival", packet.player.gameMode)
        assertEquals("minecraft:overworld", packet.player.world)
        assertEquals(20.0, packet.player.health)
    }

    @Test
    fun `handleDetailGet ignores invalid uuid`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val viewer = mockk<Player>(relaxed = true)
        val sendData: (Player, String) -> Unit = mockk(relaxed = true)

        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { viewer.name } returns "Viewer"

        val handler = PlayersHandler(plugin, json, sendData)
        handler.handleDetailGet(viewer, """{"type":"player_detail_get","uuid":"bad"}""")

        verify(exactly = 0) { sendData(any(), any()) }
    }

    @Test
    fun `handleAction heals online player and sends refreshed snapshots`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        val viewer = mockPlayer("Viewer", UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val targetUuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val target = mockPlayer("Alice", targetUuid)
        val sendCalls = mutableListOf<String>()

        every { plugin.server } returns server
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { server.getPlayer(targetUuid) } returns target
        every { server.onlinePlayers } returns listOf(viewer, target)
        every { server.maxPlayers } returns 20
        every { server.whitelistedPlayers } returns emptySet()
        every { server.bannedPlayers } returns emptySet()
        every { server.offlinePlayers } returns emptyArray()

        val handler = PlayersHandler(plugin, json) { _, data -> sendCalls.add(data) }
        handler.handleAction(
            viewer,
            json.encodeToString(
                dev.ilgax.venus.protocol.PlayerActionPacket(
                    type = "player_action",
                    requestId = "req-1",
                    uuid = targetUuid.toString(),
                    action = "heal",
                ),
            ),
        )

        val result = json.decodeFromString(PlayerActionResultPacket.serializer(), sendCalls[0])
        assertEquals(true, result.success)
        assertEquals("Player healed.", result.message)
        assertEquals("player_detail", json.decodeFromString(PlayerDetailPacket.serializer(), sendCalls[1]).type)
        assertEquals("player_list", json.decodeFromString(PlayerListPacket.serializer(), sendCalls[2]).type)
        verify { target.health = 20.0 }
    }

    @Test
    fun `handleAction fails online-only action for offline player without refresh`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        val viewer = mockPlayer("Viewer", UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val targetUuid = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        val target = mockOfflinePlayer("Bob", targetUuid)
        val sendCalls = mutableListOf<String>()

        every { plugin.server } returns server
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { server.getPlayer(targetUuid) } returns null
        every { server.whitelistedPlayers } returns linkedSetOf(target)
        every { server.bannedPlayers } returns emptySet()
        every { server.offlinePlayers } returns arrayOf(target)

        val handler = PlayersHandler(plugin, json) { _, data -> sendCalls.add(data) }
        handler.handleAction(
            viewer,
            json.encodeToString(
                dev.ilgax.venus.protocol.PlayerActionPacket(
                    type = "player_action",
                    requestId = "req-2",
                    uuid = targetUuid.toString(),
                    action = "kill",
                ),
            ),
        )

        val result = json.decodeFromString(PlayerActionResultPacket.serializer(), sendCalls.single())
        assertEquals(false, result.success)
        assertEquals("Player must be online.", result.message)
    }

    @Test
    fun `handleAction applies boolean and game mode actions`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        val viewer = mockPlayer("Viewer", UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val targetUuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val target = mockPlayer("Alice", targetUuid)
        val sendCalls = mutableListOf<String>()

        every { plugin.server } returns server
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { server.getPlayer(targetUuid) } returns target
        every { server.onlinePlayers } returns listOf(target)
        every { server.maxPlayers } returns 20
        every { server.whitelistedPlayers } returns emptySet()
        every { server.bannedPlayers } returns emptySet()
        every { server.offlinePlayers } returns emptyArray()

        val handler = PlayersHandler(plugin, json) { _, data -> sendCalls.add(data) }
        handler.handleAction(
            viewer,
            json.encodeToString(
                dev.ilgax.venus.protocol.PlayerActionPacket(
                    type = "player_action",
                    requestId = "req-3",
                    uuid = targetUuid.toString(),
                    action = "set_operator",
                    value = JsonPrimitive(true),
                ),
            ),
        )
        handler.handleAction(
            viewer,
            json.encodeToString(
                dev.ilgax.venus.protocol.PlayerActionPacket(
                    type = "player_action",
                    requestId = "req-4",
                    uuid = targetUuid.toString(),
                    action = "set_game_mode",
                    value = JsonPrimitive("creative"),
                ),
            ),
        )

        verify { target.isOp = true }
        verify { target.gameMode = GameMode.CREATIVE }
        assertEquals(6, sendCalls.size)
    }

    @Test
    fun `handleAction can re-whitelist uuid-only offline player after removal`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        val viewer = mockPlayer("Viewer", UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val targetUuid = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")
        val target = mockOfflinePlayer("NeverJoined", targetUuid)
        val sendCalls = mutableListOf<String>()

        every { plugin.server } returns server
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { server.getPlayer(targetUuid) } returns null
        every { server.whitelistedPlayers } returns emptySet()
        every { server.bannedPlayers } returns emptySet()
        every { server.offlinePlayers } returns emptyArray()
        every { server.getOfflinePlayer(targetUuid) } returns target
        every { server.onlinePlayers } returns listOf(viewer)
        every { server.maxPlayers } returns 20

        val handler = PlayersHandler(plugin, json) { _, data -> sendCalls.add(data) }
        handler.handleAction(
            viewer,
            json.encodeToString(
                dev.ilgax.venus.protocol.PlayerActionPacket(
                    type = "player_action",
                    requestId = "req-5",
                    uuid = targetUuid.toString(),
                    action = "set_whitelisted",
                    value = JsonPrimitive(true),
                ),
            ),
        )

        val result = json.decodeFromString(PlayerActionResultPacket.serializer(), sendCalls[0])
        assertEquals(true, result.success)
        assertEquals("Player whitelisted.", result.message)
        verify { target.isWhitelisted = true }
    }

    @Suppress("DEPRECATION")
    private fun mockPlayer(
        name: String,
        uuid: UUID,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val location = mockk<Location>(relaxed = true)

        every { player.uniqueId } returns uuid
        every { player.name } returns name
        every { player.isOnline } returns true
        every { player.isOp } returns false
        every { player.isWhitelisted } returns true
        every { player.player } returns player
        every { player.gameMode } returns GameMode.SURVIVAL
        every { player.health } returns 20.0
        every { player.maxHealth } returns 20.0
        every { player.foodLevel } returns 20
        every { player.level } returns 3
        every { player.exp } returns 0.5f
        every { player.world } returns world
        every { player.location } returns location
        every { world.key } returns NamespacedKey.minecraft("overworld")
        every { location.x } returns 1.0
        every { location.y } returns 64.0
        every { location.z } returns -2.0

        return player
    }

    private fun mockOfflinePlayer(
        name: String,
        uuid: UUID,
        whitelisted: Boolean = false,
    ): OfflinePlayer {
        val offlinePlayer = mockk<OfflinePlayer>(relaxed = true)
        every { offlinePlayer.uniqueId } returns uuid
        every { offlinePlayer.name } returns name
        every { offlinePlayer.player } returns null
        every { offlinePlayer.isOnline } returns false
        every { offlinePlayer.isOp } returns false
        every { offlinePlayer.isWhitelisted } returns whitelisted
        return offlinePlayer
    }
}
