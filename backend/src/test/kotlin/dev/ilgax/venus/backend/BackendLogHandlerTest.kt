package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.protocol.ConsoleLogPacket
import dev.ilgax.venus.protocol.ConsoleLogSubscribePacket
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendLogHandlerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `subscribed active player receives queued lines`() {
        val fixture = fixture()
        fixture.subscribe(fixture.first)
        fixture.handler.queueFormattedLine("line one")
        fixture.handler.queueFormattedLine("line two")

        fixture.handler.flush()

        assertEquals(listOf("line one", "line two"), fixture.packetsFor(fixture.first).single().lines)
    }

    @Test
    fun `suppressed marker hides one line only from its owner`() {
        val fixture = fixture(twoPlayers = true)
        fixture.subscribe(fixture.first)
        fixture.subscribe(fixture.second)
        fixture.handler.suppressNextFor(fixture.first.uuid, "venus-marker")
        fixture.handler.queueFormattedLine("command venus-marker")

        fixture.handler.flush()

        assertTrue(fixture.packetsFor(fixture.first).isEmpty())
        assertEquals(listOf("command venus-marker"), fixture.packetsFor(fixture.second).single().lines)
    }

    @Test
    fun `inactive subscriber is removed during flush`() {
        val fixture = fixture(active = false)
        fixture.subscribe(fixture.first)
        fixture.handler.queueFormattedLine("first")
        fixture.handler.flush()
        every { fixture.sessions.isActive(fixture.first.uuid) } returns true
        fixture.handler.queueFormattedLine("second")

        fixture.handler.flush()

        assertTrue(fixture.packetsFor(fixture.first).isEmpty())
    }

    @Test
    fun `malformed and wrong type subscriptions are rejected`() {
        val fixture = fixture()

        fixture.handler.handleSubscribe(fixture.first, "bad-json")
        fixture.handler.handleSubscribe(
            fixture.first,
            json.encodeToString(ConsoleLogSubscribePacket.serializer(), ConsoleLogSubscribePacket("wrong")),
        )
        fixture.handler.queueFormattedLine("hidden")
        fixture.handler.flush()

        assertTrue(fixture.sent.isEmpty())
        verify(exactly = 2) { fixture.platform.logger.warning(any()) }
    }

    @Test
    fun `queue keeps latest thousand lines and flushes fifty at a time`() {
        val fixture = fixture()
        fixture.subscribe(fixture.first)
        repeat(1005) { fixture.handler.queueFormattedLine("line-$it") }

        fixture.handler.flush()

        val lines = fixture.packetsFor(fixture.first).single().lines
        assertEquals(50, lines.size)
        assertEquals("line-5", lines.first())
        assertEquals("line-54", lines.last())
    }

    @Test
    fun `formatted line sanitizes control characters`() {
        val fixture = fixture()

        val line = fixture.handler.formatLine("Server", "hello\nworld", Instant.EPOCH)

        assertTrue(line.contains("[Server]"))
        assertTrue(line.contains("hello\\nworld"))
        assertFalse(line.contains('\n'))
    }

    private fun fixture(
        active: Boolean = true,
        twoPlayers: Boolean = false,
    ): LogFixture {
        val platform = mockk<BackendPlatform>(relaxed = true)
        val sessions = mockk<SessionManager>(relaxed = true)
        val first = BackendPlayer(UUID.randomUUID(), "First")
        val second = BackendPlayer(UUID.randomUUID(), "Second")
        val sent = mutableListOf<Pair<UUID, String>>()
        every { platform.player(first.uuid) } returns first
        every { platform.player(second.uuid) } returns second.takeIf { twoPlayers }
        every { sessions.isActive(any()) } returns active
        every { platform.sendData(any(), any()) } answers {
            sent += firstArg<BackendPlayer>().uuid to secondArg()
        }
        return LogFixture(BackendLogHandler(platform, json, sessions), platform, sessions, first, second, sent)
    }

    private inner class LogFixture(
        val handler: BackendLogHandler,
        val platform: BackendPlatform,
        val sessions: SessionManager,
        val first: BackendPlayer,
        val second: BackendPlayer,
        val sent: MutableList<Pair<UUID, String>>,
    ) {
        fun subscribe(player: BackendPlayer) {
            handler.handleSubscribe(
                player,
                json.encodeToString(ConsoleLogSubscribePacket.serializer(), ConsoleLogSubscribePacket("log_subscribe")),
            )
        }

        fun packetsFor(player: BackendPlayer): List<ConsoleLogPacket> =
            sent
                .filter { it.first == player.uuid }
                .map { json.decodeFromString(ConsoleLogPacket.serializer(), it.second) }
    }
}
