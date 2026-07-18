package dev.ilgax.venus.platform

import dev.ilgax.venus.backend.BackendPlayer
import dev.ilgax.venus.backend.BackendPlayers
import dev.ilgax.venus.protocol.PlayerActionPacket
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.NameAndId
import net.minecraft.server.players.PlayerList
import net.minecraft.server.players.UserBanList
import net.minecraft.server.players.UserWhiteList
import net.minecraft.server.players.UserWhiteListEntry
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FabricBackendPlayersTest {
    companion object {
        init {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `unavailable server returns empty reads and correlated action failure`() {
        val players = playersWith { null }
        val viewer = BackendPlayer(UUID.randomUUID(), "Viewer")
        val packet = PlayerActionPacket("player_action", "req-1", UUID.randomUUID().toString(), "heal")

        val snapshot = players.list(viewer)
        val result = players.applyAction(viewer, packet)

        assertEquals(0, snapshot.onlineCount)
        assertEquals(0, snapshot.maxPlayers)
        assertTrue(snapshot.onlinePlayers.isEmpty())
        assertTrue(snapshot.whitelistedPlayers.isEmpty())
        assertTrue(snapshot.blockedPlayers.isEmpty())
        assertNull(players.detail(viewer, UUID.randomUUID()))
        assertFalse(result.success)
        assertEquals("req-1", result.requestId)
        assertEquals("Server unavailable.", result.message)
    }

    @Test
    fun `invalid uuid and missing viewer are rejected`() {
        val playerList = mockk<PlayerList>()
        val server = mockk<MinecraftServer>()
        val viewer = BackendPlayer(UUID.randomUUID(), "Viewer")
        val targetUuid = UUID.randomUUID()
        every { server.playerList } returns playerList
        every { playerList.getPlayer(viewer.uuid) } returns null
        val players = playersWith { server }

        val invalid = players.applyAction(viewer, PlayerActionPacket("player_action", "bad", "invalid", "heal"))
        val missingViewer =
            players.applyAction(
                viewer,
                PlayerActionPacket("player_action", "missing", targetUuid.toString(), "heal"),
            )

        assertFalse(invalid.success)
        assertEquals("Invalid player uuid.", invalid.message)
        assertFalse(missingViewer.success)
        assertEquals("Player not found.", missingViewer.message)
    }

    @Test
    fun `online actions mutate health and food and reject invalid values`() {
        val fixture = onlineFixture()
        every { fixture.target.maxHealth } returns 18.0f
        every { fixture.target.health = any() } returns Unit
        every { fixture.target.foodData.foodLevel = any() } returns Unit

        val healed = fixture.action("heal")
        val fed = fixture.action("feed")
        val invalidWhitelist = fixture.action("set_whitelisted", JsonPrimitive("invalid"))
        val invalidBlocked = fixture.action("set_blocked", JsonPrimitive("invalid"))
        val invalidOperator = fixture.action("set_operator", JsonPrimitive("invalid"))
        val invalidGameMode = fixture.action("set_game_mode", JsonPrimitive("builder"))
        val unknown = fixture.action("future_action")

        assertTrue(healed.success)
        assertEquals("Player healed.", healed.message)
        assertTrue(fed.success)
        assertEquals("Player fed.", fed.message)
        assertEquals("Invalid whitelist value.", invalidWhitelist.message)
        assertEquals("Invalid blocked value.", invalidBlocked.message)
        assertEquals("Invalid operator value.", invalidOperator.message)
        assertEquals("Invalid game mode.", invalidGameMode.message)
        assertEquals("Unknown player action.", unknown.message)
    }

    @Test
    fun `list and offline detail expose player identity and flags`() {
        val targetUuid = UUID.randomUUID()
        val online = mockk<ServerPlayer>()
        val onlineIdentity = mockk<NameAndId>()
        val offlineUuid = UUID.randomUUID()
        val offlineIdentity = mockk<NameAndId>()
        val whitelistEntry = mockk<UserWhiteListEntry>()
        val whiteList = mockk<UserWhiteList>()
        val bans = mockk<UserBanList>()
        val playerList = mockk<PlayerList>()
        val server = mockk<MinecraftServer>()
        every { server.playerList } returns playerList
        every { server.maxPlayers } returns 30
        every { playerList.players } returns listOf(online)
        every { playerList.whiteList } returns whiteList
        every { playerList.bans } returns bans
        every { whiteList.entries } returns listOf(whitelistEntry)
        every { bans.entries } returns emptyList()
        every { whitelistEntry.user } returns offlineIdentity
        every { online.nameAndId() } returns onlineIdentity
        every { onlineIdentity.id() } returns targetUuid
        every { onlineIdentity.name() } returns "Online"
        every { offlineIdentity.id() } returns offlineUuid
        every { offlineIdentity.name() } returns "Offline"
        every { playerList.isWhiteListed(any()) } returns false
        every { playerList.isWhiteListed(offlineIdentity) } returns true
        every { playerList.isOp(any()) } returns false
        every { bans.isBanned(any()) } returns false
        every { playerList.getPlayer(offlineUuid) } returns null
        val players = playersWith { server }
        val viewer = BackendPlayer(UUID.randomUUID(), "Viewer")

        val snapshot = players.list(viewer)
        val detail = players.detail(viewer, offlineUuid)

        assertEquals(1, snapshot.onlineCount)
        assertEquals(30, snapshot.maxPlayers)
        assertEquals("Online", snapshot.onlinePlayers.single().name)
        assertEquals("Offline", snapshot.whitelistedPlayers.single().name)
        assertTrue(detail!!.player.whitelisted)
        assertFalse(detail.player.online)
        assertEquals(offlineUuid.toString(), detail.player.uuid)
    }

    private fun onlineFixture(): OnlineFixture {
        val viewerUuid = UUID.randomUUID()
        val targetUuid = UUID.randomUUID()
        val viewer = mockk<ServerPlayer>()
        val target = mockk<ServerPlayer>()
        val identity = mockk<NameAndId>()
        val playerList = mockk<PlayerList>()
        val server = mockk<MinecraftServer>()
        every { server.playerList } returns playerList
        every { playerList.getPlayer(viewerUuid) } returns viewer
        every { playerList.getPlayer(targetUuid) } returns target
        every { target.nameAndId() } returns identity
        every { identity.id() } returns targetUuid
        every { identity.name() } returns "Target"
        return OnlineFixture(playersWith { server }, BackendPlayer(viewerUuid, "Viewer"), targetUuid, target)
    }

    @Suppress("UNCHECKED_CAST")
    private fun playersWith(serverProvider: () -> MinecraftServer?): BackendPlayers {
        val constructor =
            Class
                .forName("dev.ilgax.venus.platform.FabricBackendPlayers")
                .declaredConstructors
                .single()
        constructor.isAccessible = true
        return constructor.newInstance(serverProvider) as BackendPlayers
    }

    private data class OnlineFixture(
        val players: BackendPlayers,
        val viewer: BackendPlayer,
        val targetUuid: UUID,
        val target: ServerPlayer,
    ) {
        fun action(
            action: String,
            value: JsonPrimitive? = null,
        ) = players.applyAction(
            viewer,
            PlayerActionPacket("player_action", "req-$action", targetUuid.toString(), action, value),
        )
    }
}
