package dev.ilgax.venus.channel

import dev.ilgax.venus.protocol.CmdResponsePacket
import dev.ilgax.venus.protocol.ConsoleLogPacket
import dev.ilgax.venus.protocol.ErrorPacket
import dev.ilgax.venus.protocol.PlayerActionResultPacket
import dev.ilgax.venus.protocol.PlayerDetail
import dev.ilgax.venus.protocol.PlayerDetailPacket
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import dev.ilgax.venus.protocol.ReadyPacket
import dev.ilgax.venus.protocol.StatSubscribePacket
import dev.ilgax.venus.protocol.StatsPacket
import dev.ilgax.venus.state.SessionState
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacketHandlerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @AfterTest
    fun resetState() {
        SessionState.reset()
    }

    @Test
    fun `ready activates session and sends shared subscription packet`() {
        val sent = mutableListOf<String>()
        var successToasts = 0
        val handler = PacketHandler(json, sent::add, {}, { successToasts++ })
        SessionState.markExpectingReady()

        handler.handleReady(json.encodeToString(ReadyPacket.serializer(), ReadyPacket("ready")))

        assertTrue(SessionState.sessionActive)
        assertEquals(1, successToasts)
        val subscription = json.decodeFromString(StatSubscribePacket.serializer(), sent.single())
        assertEquals("stat_subscribe", subscription.type)
        assertEquals(1, subscription.intervalSeconds)
        assertEquals(listOf("tps", "ram", "mspt", "uptime", "players", "server", "cpu"), subscription.stats)
    }

    @Test
    fun `data packets update stats and command response history`() {
        val handler = PacketHandler(json, {}) {}
        SessionState.markActive()
        val stats =
            StatsPacket(
                type = "stats",
                tps = 19.9,
                mspt = 23.4,
                uptime = 30,
                cpuLoad = 12.3,
                onlinePlayers = 10,
                maxPlayers = 20,
                serverName = "Paper",
                minecraftVersion = "1.21.1",
            )
        val response = CmdResponsePacket(type = "cmd_response", command = "say hi", lines = listOf("hi"))
        val consoleLog = ConsoleLogPacket(type = "console_log", lines = listOf("[INFO]: started"))

        handler.handleData(json.encodeToString(StatsPacket.serializer(), stats))
        handler.handleData(json.encodeToString(ConsoleLogPacket.serializer(), consoleLog))
        handler.handleData(json.encodeToString(CmdResponsePacket.serializer(), response))

        assertEquals(stats, SessionState.latestStats)
        assertEquals(listOf("[INFO]: started", "> say hi", "hi"), SessionState.consoleLines)
    }

    @Test
    fun `player list and detail packets update player state`() {
        val handler = PacketHandler(json, {}) {}
        SessionState.markActive()
        val playerList =
            PlayerListPacket(
                type = "player_list",
                onlineCount = 1,
                maxPlayers = 20,
                onlinePlayers =
                    listOf(
                        PlayerSummaryPacket(
                            uuid = "1",
                            name = "Alice",
                            displayName = "Alice",
                            online = true,
                            operator = false,
                            whitelisted = true,
                            blocked = false,
                        ),
                    ),
                whitelistedPlayers = emptyList(),
                blockedPlayers = emptyList(),
            )
        val playerDetail =
            PlayerDetailPacket(
                type = "player_detail",
                player =
                    PlayerDetail(
                        uuid = "1",
                        name = "Alice",
                        displayName = "Alice",
                        online = true,
                        operator = false,
                        whitelisted = true,
                        blocked = false,
                        gameMode = "survival",
                    ),
            )

        handler.handleData(json.encodeToString(PlayerListPacket.serializer(), playerList))
        handler.handleData(json.encodeToString(PlayerDetailPacket.serializer(), playerDetail))
        handler.handleData(
            json.encodeToString(
                PlayerActionResultPacket(
                    type = "player_action_result",
                    requestId = "req-1",
                    uuid = "1",
                    action = "heal",
                    success = true,
                    message = "Player healed.",
                ),
            ),
        )

        assertEquals(playerList, SessionState.latestPlayerList)
        assertEquals(playerDetail.player, SessionState.latestPlayerDetail)
        assertEquals("heal", SessionState.latestPlayerActionResult?.action)
    }

    @Test
    fun `invalid and unexpected ready packets do not activate session or subscribe`() {
        val sent = mutableListOf<String>()
        var successToasts = 0
        val handler = PacketHandler(json, sent::add, {}, { successToasts++ })
        SessionState.markExpectingReady()

        handler.handleReady("""{"type":"stats"}""")
        handler.handleReady("""{"type":""")

        assertFalse(SessionState.sessionActive)
        assertTrue(sent.isEmpty())
        assertEquals(0, successToasts)
    }

    @Test
    fun `ready is ignored when not expecting handshake completion`() {
        val sent = mutableListOf<String>()
        var successToasts = 0
        val handler = PacketHandler(json, sent::add, {}, { successToasts++ })

        handler.handleReady(json.encodeToString(ReadyPacket.serializer(), ReadyPacket("ready")))

        assertFalse(SessionState.sessionActive)
        assertTrue(sent.isEmpty())
        assertEquals(0, successToasts)
    }

    @Test
    fun `invalid and unknown data packets do not mutate state`() {
        val handler = PacketHandler(json, {}) {}
        val stats = StatsPacket(type = "stats", tps = 20.0)
        val response = CmdResponsePacket(type = "cmd_response", command = "say hi", lines = listOf("hi"))
        SessionState.updateStats(stats)
        SessionState.addCommandResponse(response)
        SessionState.markActive()

        handler.handleData("""{"type":"unknown","value":1}""")
        handler.handleData("""{"type":""")
        handler.handleData("""{"type":"stats","tps":"bad"}""")
        handler.handleData("""{"type":"cmd_response","command":"say hi"}""")

        assertEquals(stats, SessionState.latestStats)
        assertEquals(listOf("> say hi", "hi"), SessionState.consoleLines)
    }

    @Test
    fun `error packet shows auth failure toast without activating session`() {
        val failures = mutableListOf<String>()
        val handler = PacketHandler(json, {}, {}, {}, failures::add)

        handler.handleError(json.encodeToString(ErrorPacket.serializer(), ErrorPacket("error", "auth_denied")))

        assertEquals(listOf("Server denied access."), failures)
        assertFalse(SessionState.sessionActive)
    }

    @Test
    fun `auth error clears expecting ready state`() {
        val sent = mutableListOf<String>()
        val handler = PacketHandler(json, sent::add, {}, {}, {})
        SessionState.markExpectingReady()

        handler.handleError(json.encodeToString(ErrorPacket.serializer(), ErrorPacket("error", "auth_denied")))
        handler.handleReady(json.encodeToString(ReadyPacket.serializer(), ReadyPacket("ready")))

        assertFalse(SessionState.sessionActive)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `invalid error packet does not show auth failure toast`() {
        val failures = mutableListOf<String>()
        val handler = PacketHandler(json, {}, {}, {}, failures::add)

        handler.handleError("""{"type":"ready"}""")
        handler.handleError("""{"type":"""")

        assertTrue(failures.isEmpty())
    }

    @Test
    fun `unknown data packet does not create session state`() {
        val handler = PacketHandler(json, {}) {}

        handler.handleData("""{"type":"unknown"}""")

        assertFalse(SessionState.sessionActive)
        assertNull(SessionState.latestStats)
        assertTrue(SessionState.consoleLines.isEmpty())
    }

    @Test
    fun `data packets before active session do not mutate state`() {
        val handler = PacketHandler(json, {}) {}

        handler.handleData(json.encodeToString(StatsPacket.serializer(), StatsPacket(type = "stats", tps = 20.0)))

        assertNull(SessionState.latestStats)
        assertTrue(SessionState.consoleLines.isEmpty())
    }
}
