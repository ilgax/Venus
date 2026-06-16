package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.SessionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
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
        val router = createRouter()
        mockkObject(SessionManager)
        every { SessionManager.isActive(player.uuid) } returns false

        router.handleCommand(player, """{"type":"console_cmd"}""")

        verify(exactly = 0) { router.console.handle(any(), any()) }
    }

    @Test
    fun `handleCommand routes console command`() {
        val router = createRouter()
        mockkObject(SessionManager)
        every { SessionManager.isActive(player.uuid) } returns true
        val data = """{"type":"console_cmd"}"""

        router.handleCommand(player, data)

        verify { router.console.handle(player, data) }
    }

    @Test
    fun `handleCommand routes log subscription`() {
        val router = createRouter()
        mockkObject(SessionManager)
        every { SessionManager.isActive(player.uuid) } returns true
        val data = """{"type":"log_subscribe"}"""

        router.handleCommand(player, data)

        verify { router.log.handleSubscribe(player, data) }
    }

    @Test
    fun `handleCommand routes player action`() {
        val router = createRouter()
        mockkObject(SessionManager)
        every { SessionManager.isActive(player.uuid) } returns true
        val data = """{"type":"player_action"}"""

        router.handleCommand(player, data)

        verify { router.players.handleAction(player, data) }
    }

    private fun createRouter(): RouterFixture {
        val platform = mockk<BackendPlatform>(relaxed = true)
        val logger = mockk<BackendLogger>(relaxed = true)
        every { platform.logger } returns logger
        val console = mockk<BackendConsoleHandler>(relaxed = true)
        val stats = mockk<BackendStatsHandler>(relaxed = true)
        val log = mockk<BackendLogHandler>(relaxed = true)
        val players = mockk<BackendPlayersHandler>(relaxed = true)
        return RouterFixture(
            console = console,
            log = log,
            players = players,
            router = BackendPacketRouter(platform, json, console, stats, log, players),
        )
    }

    private data class RouterFixture(
        val console: BackendConsoleHandler,
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
