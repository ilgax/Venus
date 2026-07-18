package dev.ilgax.venus.backend

import dev.ilgax.venus.protocol.MAX_PACKET_SIZE
import dev.ilgax.venus.protocol.PlayerActionPacket
import dev.ilgax.venus.protocol.PlayerActionResultPacket
import dev.ilgax.venus.protocol.PlayerDetail
import dev.ilgax.venus.protocol.PlayerDetailGetPacket
import dev.ilgax.venus.protocol.PlayerDetailPacket
import dev.ilgax.venus.protocol.PlayerListGetPacket
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackendPlayersHandlerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `handleAction sends failed result when platform rejects malformed value`() {
        val player = BackendPlayer(UUID.randomUUID(), "Admin")
        val targetUuid = UUID.randomUUID()
        val sent = mutableListOf<String>()
        val players = mockk<BackendPlayers>()
        val platform = mockk<BackendPlatform>(relaxed = true)
        every { platform.players() } returns players
        every { platform.sendData(player, any()) } answers {
            sent.add(secondArg())
            Unit
        }
        every { players.applyAction(eq(player), any()) } throws IllegalArgumentException("value must be primitive")
        val handler = BackendPlayersHandler(platform, json)
        val packet =
            PlayerActionPacket(
                type = "player_action",
                requestId = "req-1",
                uuid = targetUuid.toString(),
                action = "set_game_mode",
                value = buildJsonObject { put("bad", "shape") },
            )

        handler.handleAction(player, json.encodeToString(PlayerActionPacket.serializer(), packet))

        val result = json.decodeFromString<PlayerActionResultPacket>(sent.single())
        assertEquals("req-1", result.requestId)
        assertEquals(targetUuid.toString(), result.uuid)
        assertEquals("set_game_mode", result.action)
        assertFalse(result.success)
        assertEquals("Invalid player action value.", result.message)
    }

    @Test
    fun `V36 player list cursors return every entry in packet safe pages`() {
        val player = BackendPlayer(UUID.randomUUID(), "Admin")
        val sent = ArrayDeque<String>()
        val summaries =
            (1..300).map { index ->
                PlayerSummaryPacket(
                    uuid = UUID.randomUUID().toString(),
                    name = "player-$index-${"x".repeat(80)}",
                    displayName = "Player $index ${"y".repeat(80)}",
                    online = true,
                    operator = false,
                    whitelisted = false,
                    blocked = false,
                )
            }
        val players = mockk<BackendPlayers>()
        val platform = mockk<BackendPlatform>(relaxed = true)
        every { platform.players() } returns players
        every { players.list(player) } returns BackendPlayerListSnapshot(300, 500, summaries, emptyList(), emptyList())
        every { platform.sendData(player, any()) } answers { sent.addLast(secondArg()) }
        val handler = BackendPlayersHandler(platform, json)
        val received = mutableListOf<PlayerSummaryPacket>()
        var cursor: String? = null

        do {
            val request = PlayerListGetPacket("player_list_get", "request-1", cursor)
            handler.handleListGet(player, json.encodeToString(PlayerListGetPacket.serializer(), request))
            val encoded = sent.removeFirst()
            assertTrue(encoded.toByteArray().size <= MAX_PACKET_SIZE)
            val page = json.decodeFromString<PlayerListPacket>(encoded)
            assertEquals("request-1", page.requestId)
            assertEquals(cursor, page.cursor)
            received += page.onlinePlayers
            cursor = page.nextCursor
        } while (cursor != null)

        assertEquals(summaries.map { it.uuid }, received.map { it.uuid })
        assertNotNull(received.lastOrNull())
    }

    @Test
    fun `malformed list and stale cursor are ignored`() {
        val player = BackendPlayer(UUID.randomUUID(), "Admin")
        val platform = mockk<BackendPlatform>(relaxed = true)
        val handler = BackendPlayersHandler(platform, json)

        handler.handleListGet(player, "bad-json")
        handler.handleListGet(
            player,
            json.encodeToString(
                PlayerListGetPacket.serializer(),
                PlayerListGetPacket("player_list_get", "request", "stale"),
            ),
        )

        io.mockk.verify(exactly = 0) { platform.sendData(any(), any()) }
        io.mockk.verify(exactly = 2) { platform.logger.warning(any()) }
    }

    @Test
    fun `detail lookup sends known player and ignores invalid uuid`() {
        val player = BackendPlayer(UUID.randomUUID(), "Admin")
        val targetUuid = UUID.randomUUID()
        val sent = mutableListOf<String>()
        val players = mockk<BackendPlayers>()
        val platform = mockk<BackendPlatform>(relaxed = true)
        val detail = PlayerDetail(targetUuid.toString(), "Target", "Target", true, false, true, false)
        every { platform.players() } returns players
        every { players.detail(player, targetUuid) } returns PlayerDetailPacket("player_detail", detail)
        every { platform.sendData(player, any()) } answers { sent += secondArg<String>() }
        val handler = BackendPlayersHandler(platform, json)

        handler.handleDetailGet(
            player,
            json.encodeToString(
                PlayerDetailGetPacket.serializer(),
                PlayerDetailGetPacket("player_detail_get", targetUuid.toString()),
            ),
        )
        handler.handleDetailGet(
            player,
            json.encodeToString(PlayerDetailGetPacket.serializer(), PlayerDetailGetPacket("player_detail_get", "bad")),
        )

        assertEquals(detail, json.decodeFromString<PlayerDetailPacket>(sent.single()).player)
        io.mockk.verify { platform.logger.warning(match { it.contains("invalid player uuid") }) }
    }

    @Test
    fun `invalid action uuid returns correlated failure`() {
        val player = BackendPlayer(UUID.randomUUID(), "Admin")
        val sent = mutableListOf<String>()
        val platform = mockk<BackendPlatform>(relaxed = true)
        every { platform.sendData(player, any()) } answers { sent += secondArg<String>() }
        val handler = BackendPlayersHandler(platform, json)

        handler.handleAction(
            player,
            json.encodeToString(
                PlayerActionPacket.serializer(),
                PlayerActionPacket("player_action", "request", "bad", "heal"),
            ),
        )

        val result = json.decodeFromString<PlayerActionResultPacket>(sent.single())
        assertFalse(result.success)
        assertEquals("request", result.requestId)
        assertEquals("Invalid player uuid.", result.message)
    }

    @Test
    fun `successful action sends result and refreshed detail`() {
        val player = BackendPlayer(UUID.randomUUID(), "Admin")
        val targetUuid = UUID.randomUUID()
        val sent = mutableListOf<String>()
        val players = mockk<BackendPlayers>()
        val platform = mockk<BackendPlatform>(relaxed = true)
        val request = PlayerActionPacket("player_action", "request", targetUuid.toString(), "heal")
        val result = PlayerActionResultPacket("player_action_result", "request", targetUuid.toString(), "heal", true, "healed")
        val detail = PlayerDetailPacket("player_detail", PlayerDetail(targetUuid.toString(), "Target", "Target", true, false, false, false))
        every { platform.players() } returns players
        every { players.applyAction(player, request) } returns result
        every { players.detail(player, targetUuid) } returns detail
        every { platform.sendData(player, any()) } answers { sent += secondArg<String>() }

        BackendPlayersHandler(platform, json).handleAction(
            player,
            json.encodeToString(PlayerActionPacket.serializer(), request),
        )

        assertEquals(2, sent.size)
        assertEquals(result, json.decodeFromString<PlayerActionResultPacket>(sent.first()))
        assertEquals(detail, json.decodeFromString<PlayerDetailPacket>(sent.last()))
        assertNull(sent.firstOrNull { it.contains("player_list") })
    }
}
