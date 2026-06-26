package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.SessionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackendPacketRouterTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")

    @AfterTest
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `known command packet types map to backend handlers`() {
        assertEquals(BackendCommandRoute.CONSOLE_CMD, BackendCommandRoute.fromPacketType("console_cmd"))
        assertEquals(BackendCommandRoute.LOG_SUBSCRIBE, BackendCommandRoute.fromPacketType("log_subscribe"))
        assertEquals(BackendCommandRoute.STAT_SUBSCRIBE, BackendCommandRoute.fromPacketType("stat_subscribe"))
        assertEquals(BackendCommandRoute.STAT_GET, BackendCommandRoute.fromPacketType("stat_get"))
        assertEquals(BackendCommandRoute.PLAYER_LIST_GET, BackendCommandRoute.fromPacketType("player_list_get"))
        assertEquals(BackendCommandRoute.PLAYER_DETAIL_GET, BackendCommandRoute.fromPacketType("player_detail_get"))
        assertEquals(BackendCommandRoute.PLAYER_ACTION, BackendCommandRoute.fromPacketType("player_action"))
    }

    @Test
    fun `unknown command packet type has no backend route`() {
        assertNull(BackendCommandRoute.fromPacketType("file_get"))
    }

    @Test
    fun `handleCommand ignores packet without active session`() {
        val router = createRouter(activeSession = false)

        router.handleCommand(player, """{"type":"console_cmd"}""")

        verify(exactly = 0) { router.console.handle(any(), any()) }
    }

    @Test
    fun `handleCommand routes console command`() {
        val router = createRouter(activeSession = true)
        val data = """{"type":"console_cmd"}"""

        router.handleCommand(player, data)

        verify { router.console.handle(player, data) }
    }

    @Test
    fun `handleCommand routes log subscription`() {
        val router = createRouter(activeSession = true)
        val data = """{"type":"log_subscribe"}"""

        router.handleCommand(player, data)

        verify { router.log.handleSubscribe(player, data) }
    }

    @Test
    fun `handleCommand routes player action`() {
        val router = createRouter(activeSession = true)
        val data = """{"type":"player_action"}"""

        router.handleCommand(player, data)

        verify { router.players.handleAction(player, data) }
    }

    @Test
    fun `handleCommand ignores valid json with invalid type shape`() {
        val router = createRouter(activeSession = true)
        val data = """{"type":{}}"""

        router.handleCommand(player, data)

        verify(exactly = 0) { router.console.handle(any(), any()) }
        verify(exactly = 0) { router.log.handleSubscribe(any(), any()) }
        verify(exactly = 0) { router.players.handleAction(any(), any()) }
    }

    @Test
    fun `inactive session blocks all seven routes`() {
        val router = createRouter(activeSession = false)

        router.handleCommand(player, """{"type":"console_cmd"}""")
        router.handleCommand(player, """{"type":"log_subscribe"}""")
        router.handleCommand(player, """{"type":"stat_subscribe"}""")
        router.handleCommand(player, """{"type":"stat_get"}""")
        router.handleCommand(player, """{"type":"player_list_get"}""")
        router.handleCommand(player, """{"type":"player_detail_get"}""")
        router.handleCommand(player, """{"type":"player_action"}""")

        verify(exactly = 0) { router.console.handle(any(), any()) }
        verify(exactly = 0) { router.log.handleSubscribe(any(), any()) }
        verify(exactly = 0) { router.stats.handleSubscribe(any(), any()) }
        verify(exactly = 0) { router.stats.handleGet(any(), any()) }
        verify(exactly = 0) { router.players.handleListGet(any(), any()) }
        verify(exactly = 0) { router.players.handleDetailGet(any(), any()) }
        verify(exactly = 0) { router.players.handleAction(any(), any()) }
    }

    @Test
    fun `handleCommand handles deeply nested JSON without crashing`() {
        val router = createRouter(activeSession = true)
        val deeplyNested = """{"type":""" + "{\"a:".repeat(500) + "1" + "}".repeat(500) + "}"

        router.handleCommand(player, deeplyNested)

        verify(exactly = 0) { router.console.handle(any(), any()) }
    }

    @Test
    fun `handleCommand handles missing type field`() {
        val router = createRouter(activeSession = true)

        router.handleCommand(player, """{"data":"value"}""")

        verify(exactly = 0) { router.console.handle(any(), any()) }
    }

    @Test
    fun `handleCommand handles non-object JSON`() {
        val router = createRouter(activeSession = true)

        router.handleCommand(player, """"just a string"""")

        verify(exactly = 0) { router.console.handle(any(), any()) }
    }

    @Test
    fun `handleCommand handles unknown packet type gracefully`() {
        val router = createRouter(activeSession = true)

        router.handleCommand(player, """{"type":"file_get"}""")

        verify(exactly = 0) { router.console.handle(any(), any()) }
    }

    private fun createRouter(activeSession: Boolean): RouterFixture {
        val platform = mockk<BackendPlatform>(relaxed = true)
        val logger = mockk<BackendLogger>(relaxed = true)
        every { platform.logger } returns logger
        val console = mockk<BackendConsoleHandler>(relaxed = true)
        val stats = mockk<BackendStatsHandler>(relaxed = true)
        val log = mockk<BackendLogHandler>(relaxed = true)
        val players = mockk<BackendPlayersHandler>(relaxed = true)
        val sessionManager = mockk<SessionManager>(relaxed = true)
        every { sessionManager.isActive(player.uuid) } returns activeSession
        return RouterFixture(
            console = console,
            stats = stats,
            log = log,
            players = players,
            router = BackendPacketRouter(platform, json, console, stats, log, players, sessionManager),
        )
    }

    private data class RouterFixture(
        val console: BackendConsoleHandler,
        val stats: BackendStatsHandler,
        val log: BackendLogHandler,
        val players: BackendPlayersHandler,
        val router: BackendPacketRouter,
    ) {
        fun handleCommand(
            player: BackendPlayer,
            data: String,
        ) = router.handleCommand(player, data)
    }
}
