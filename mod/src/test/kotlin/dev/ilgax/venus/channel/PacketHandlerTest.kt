package dev.ilgax.venus.channel

import dev.ilgax.venus.protocol.CmdResponsePacket
import dev.ilgax.venus.protocol.ConsoleLogPacket
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
        val handler = PacketHandler(json, sent::add) {}

        handler.handleReady(json.encodeToString(ReadyPacket.serializer(), ReadyPacket("ready")))

        assertTrue(SessionState.sessionActive)
        val subscription = json.decodeFromString(StatSubscribePacket.serializer(), sent.single())
        assertEquals("stat_subscribe", subscription.type)
        assertEquals(1, subscription.intervalSeconds)
        assertEquals(listOf("tps", "ram", "mspt", "uptime"), subscription.stats)
    }

    @Test
    fun `data packets update stats and command response history`() {
        val handler = PacketHandler(json, {}) {}
        val stats = StatsPacket(type = "stats", tps = 19.9, mspt = 23.4, uptime = 30)
        val response = CmdResponsePacket(type = "cmd_response", command = "say hi", lines = listOf("hi"))
        val consoleLog = ConsoleLogPacket(type = "console_log", lines = listOf("[INFO]: started"))

        handler.handleData(json.encodeToString(StatsPacket.serializer(), stats))
        handler.handleData(json.encodeToString(ConsoleLogPacket.serializer(), consoleLog))
        handler.handleData(json.encodeToString(CmdResponsePacket.serializer(), response))

        assertEquals(stats, SessionState.latestStats)
        assertEquals(listOf(response), SessionState.commandResponses)
        assertEquals(listOf("[INFO]: started", "> say hi", "hi"), SessionState.consoleLines)
    }

    @Test
    fun `invalid and unexpected ready packets do not activate session or subscribe`() {
        val sent = mutableListOf<String>()
        val handler = PacketHandler(json, sent::add) {}

        handler.handleReady("""{"type":"stats"}""")
        handler.handleReady("""{"type":""")

        assertFalse(SessionState.sessionActive)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `invalid and unknown data packets do not mutate state`() {
        val handler = PacketHandler(json, {}) {}
        val stats = StatsPacket(type = "stats", tps = 20.0)
        val response = CmdResponsePacket(type = "cmd_response", command = "say hi", lines = listOf("hi"))
        SessionState.updateStats(stats)
        SessionState.addCommandResponse(response)

        handler.handleData("""{"type":"unknown","value":1}""")
        handler.handleData("""{"type":""")
        handler.handleData("""{"type":"stats","tps":"bad"}""")
        handler.handleData("""{"type":"cmd_response","command":"say hi"}""")

        assertEquals(stats, SessionState.latestStats)
        assertEquals(listOf(response), SessionState.commandResponses)
    }

    @Test
    fun `unknown data packet does not create session state`() {
        val handler = PacketHandler(json, {}) {}

        handler.handleData("""{"type":"unknown"}""")

        assertFalse(SessionState.sessionActive)
        assertNull(SessionState.latestStats)
        assertTrue(SessionState.commandResponses.isEmpty())
    }
}
