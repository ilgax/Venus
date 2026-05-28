package dev.ilgax.venus.state

import dev.ilgax.venus.protocol.CmdResponsePacket
import dev.ilgax.venus.protocol.StatsPacket
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
    fun `state retains active session stats and command responses`() {
        val stats = StatsPacket(type = "stats", tps = 19.9, mspt = 23.4)
        val response = CmdResponsePacket(type = "cmd_response", command = "say hi", lines = listOf("hi"))

        SessionState.activate()
        SessionState.updateStats(stats)
        SessionState.addCommandResponse(response)

        assertTrue(SessionState.sessionActive)
        assertEquals(stats, SessionState.latestStats)
        assertEquals(listOf(response), SessionState.commandResponses)
    }

    @Test
    fun `reset clears session data`() {
        SessionState.activate()
        SessionState.updateStats(StatsPacket(type = "stats", uptime = 30))
        SessionState.addCommandResponse(CmdResponsePacket("cmd_response", "say hi", listOf("hi")))

        SessionState.reset()

        assertFalse(SessionState.sessionActive)
        assertNull(SessionState.latestStats)
        assertTrue(SessionState.commandResponses.isEmpty())
    }

    @Test
    fun `command response history retains multiple entries until reset`() {
        val first = CmdResponsePacket("cmd_response", "say one", listOf("one"))
        val second = CmdResponsePacket("cmd_response", "say two", listOf("two"))

        SessionState.addCommandResponse(first)
        SessionState.addCommandResponse(second)

        assertEquals(listOf(first, second), SessionState.commandResponses)

        SessionState.reset()

        assertTrue(SessionState.commandResponses.isEmpty())
    }
}
