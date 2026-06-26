package dev.ilgax.venus.state

import dev.ilgax.venus.protocol.CmdResponsePacket
import dev.ilgax.venus.protocol.PlayerActionResultPacket
import dev.ilgax.venus.protocol.PlayerDetail
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.StatsPacket
import dev.ilgax.venus.state.SessionState.HandshakeState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStateTest {
    @AfterTest
    fun resetState() {
        SessionState.reset()
    }

    @Test
    fun `state retains active session stats and console output`() {
        val stats = StatsPacket(type = "stats", tps = 19.9, mspt = 23.4)
        val response = CmdResponsePacket(type = "cmd_response", command = "say hi", lines = listOf("hi"))

        SessionState.activate()
        SessionState.updateStats(stats)
        SessionState.addCommandResponse(response)

        assertTrue(SessionState.sessionActive)
        assertEquals(stats, SessionState.latestStats)
        assertEquals(listOf("> say hi", "hi"), SessionState.consoleLines)
    }

    @Test
    fun `reset clears session data`() {
        SessionState.activate()
        SessionState.updateStats(StatsPacket(type = "stats", uptime = 30))
        SessionState.updatePlayerList(
            PlayerListPacket(
                type = "player_list",
                onlineCount = 1,
                maxPlayers = 20,
                onlinePlayers = emptyList(),
                whitelistedPlayers = emptyList(),
                blockedPlayers = emptyList(),
            ),
        )
        SessionState.updatePlayerDetail(
            PlayerDetail(
                uuid = "1",
                name = "Alice",
                displayName = "Alice",
                online = true,
                operator = false,
                whitelisted = true,
                blocked = false,
            ),
        )
        SessionState.updatePlayerActionResult(
            PlayerActionResultPacket(
                type = "player_action_result",
                requestId = "req-1",
                uuid = "1",
                action = "heal",
                success = true,
                message = "Player healed.",
            ),
        )
        SessionState.addCommandResponse(CmdResponsePacket("cmd_response", "say hi", listOf("hi")))
        SessionState.setServerInfo("play.venustest.com", "My Server")

        SessionState.reset()

        assertFalse(SessionState.sessionActive)
        assertNull(SessionState.latestStats)
        assertTrue(SessionState.consoleLines.isEmpty())
        assertNull(SessionState.serverAddress)
        assertNull(SessionState.serverListName)
        assertNull(SessionState.latestPlayerList)
        assertNull(SessionState.latestPlayerDetail)
        assertNull(SessionState.latestPlayerActionResult)
    }

    @Test
    fun `setServerInfo stores address and name`() {
        SessionState.setServerInfo("play.venustest.com", "My Server")
        assertEquals("play.venustest.com", SessionState.serverAddress)
        assertEquals("My Server", SessionState.serverListName)
    }

    @Test
    fun `command response appends to console history until reset`() {
        val first = CmdResponsePacket("cmd_response", "say one", listOf("one"))
        val second = CmdResponsePacket("cmd_response", "say two", listOf("two"))

        SessionState.addCommandResponse(first)
        SessionState.addCommandResponse(second)

        assertEquals(listOf("> say one", "one", "> say two", "two"), SessionState.consoleLines)

        SessionState.reset()

        assertTrue(SessionState.consoleLines.isEmpty())
    }

    @Test
    fun `console line history retains only latest entries until reset`() {
        SessionState.addConsoleLines((1..600).map { "line $it" })

        assertEquals(500, SessionState.consoleLines.size)
        assertEquals("line 101", SessionState.consoleLines.first())
        assertEquals("line 600", SessionState.consoleLines.last())
    }

    @Test
    fun `addConsoleLines with oversized batch is bounded to max`() {
        SessionState.addConsoleLines((1..1000).map { "line $it" })

        assertEquals(500, SessionState.consoleLines.size)
        assertEquals("line 501", SessionState.consoleLines.first())
        assertEquals("line 1000", SessionState.consoleLines.last())
    }

    @Test
    fun `handshake state starts idle`() {
        assertEquals(HandshakeState.IDLE, SessionState.handshakeState)
    }

    @Test
    fun `markExpectingReady transitions to expecting`() {
        SessionState.markExpectingReady()

        assertEquals(HandshakeState.EXPECTING_READY, SessionState.handshakeState)
        assertFalse(SessionState.sessionActive)
    }

    @Test
    fun `markActive transitions to active`() {
        SessionState.markExpectingReady()
        SessionState.markActive()

        assertEquals(HandshakeState.ACTIVE, SessionState.handshakeState)
        assertTrue(SessionState.sessionActive)
    }

    @Test
    fun `markIdle resets to idle`() {
        SessionState.markActive()
        SessionState.markIdle()

        assertEquals(HandshakeState.IDLE, SessionState.handshakeState)
        assertFalse(SessionState.sessionActive)
    }

    @Test
    fun `reset transitions to idle`() {
        SessionState.markActive()
        SessionState.reset()

        assertEquals(HandshakeState.IDLE, SessionState.handshakeState)
        assertFalse(SessionState.sessionActive)
    }
}
