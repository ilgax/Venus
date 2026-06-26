package dev.ilgax.venus.backend

import dev.ilgax.venus.protocol.PlayerActionPacket
import dev.ilgax.venus.protocol.PlayerActionResultPacket
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
}
