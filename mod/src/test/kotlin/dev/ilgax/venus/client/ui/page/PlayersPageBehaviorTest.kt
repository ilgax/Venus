package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.client.ui.UiTestFixture
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.protocol.PlayerActionResultPacket
import dev.ilgax.venus.protocol.PlayerDetail
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import dev.ilgax.venus.state.SessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayersPageBehaviorTest : UiTestFixture() {
    @Test
    fun `players request once then deduplicate filter search scroll and select`() {
        var listRequests = 0
        val detailRequests = mutableListOf<String>()
        val page = PlayersPage({ listRequests++ }, detailRequests::add, { _, _, _ -> "action-1" })
        val search = page.searchField()!!
        page.layout(Bounds(0, 0, 600, 400))

        page.onEnter()
        assertEquals(0, listRequests)
        SessionState.markActive()
        page.onEnter()
        page.onEnter()
        assertEquals(1, listRequests)

        SessionState.updatePlayerList(playerList())
        page.render(graphics, font, 0, 0, 0f)
        assertEquals(listOf("u1", "u2", "u3", "u4"), page.currentUiState().visiblePlayerUuids)

        assertTrue(page.mouseClicked(350.0, 15.0, 0))
        search.editBox().setValue("ali")
        assertEquals(PlayersPage.PlayerFilter.ONLINE, page.currentUiState().filter)
        assertEquals("ali", page.currentUiState().query)
        assertEquals(listOf("u1"), page.currentUiState().visiblePlayerUuids)

        assertFalse(page.mouseScrolled(0.0, 0.0, 0.0, -1.0))
        assertTrue(page.mouseScrolled(20.0, 60.0, 0.0, -1.0))
        assertFalse(page.mouseClicked(20.0, 40.0, 1))
        assertTrue(page.mouseClicked(20.0, 40.0, 0))
        assertEquals("u1", page.currentUiState().selectedUuid)
        assertEquals(listOf("u1"), detailRequests)

        page.onLeave()
        assertFalse(search.editBox().visible)
    }

    @Test
    fun `player detail dispatches supported action and clears only correlated result`() {
        val actions = mutableListOf<Triple<String, String, Any?>>()
        val page =
            PlayersPage({}, {}, { uuid, action, value ->
                actions += Triple(uuid, action, value)
                "action-1"
            })
        page.searchField()
        page.layout(Bounds(0, 0, 600, 400))
        SessionState.markActive()
        SessionState.updatePlayerList(playerList())
        SessionState.updatePlayerDetail(detail())
        page.selectAndNavigate("u1")
        page.render(graphics, font, 0, 0, 0f)

        assertTrue(page.mouseClicked(30.0, 212.0, 0))
        assertEquals(1, actions.size)
        assertEquals("u1", actions.single().first)
        assertEquals("heal", actions.single().second)
        assertNull(actions.single().third)
        assertEquals("heal", page.currentUiState().pendingAction)
        assertEquals("action-1", page.currentUiState().pendingRequestId)
        assertFalse(page.mouseClicked(30.0, 212.0, 0))

        SessionState.updatePlayerActionResult(PlayerActionResultPacket("player_action_result", "other", "u1", "heal", true, "ok"))
        page.render(graphics, font, 0, 0, 0f)
        assertEquals("action-1", page.currentUiState().pendingRequestId)

        SessionState.updatePlayerActionResult(PlayerActionResultPacket("player_action_result", "action-1", "u1", "heal", true, "ok"))
        page.render(graphics, font, 0, 0, 0f)
        assertNull(page.currentUiState().pendingAction)
        assertNull(page.currentUiState().pendingRequestId)
    }

    private fun playerList(): PlayerListPacket =
        PlayerListPacket(
            type = "player_list",
            requestId = "players-1",
            onlineCount = 2,
            maxPlayers = 20,
            onlinePlayers = listOf(player("u1", "Alice", true), player("u2", "Bob", true)),
            whitelistedPlayers = listOf(player("u1", "Alice", true), player("u3", "Carol", false)),
            blockedPlayers = listOf(player("u2", "Bob", true), player("u4", "Dave", false, blocked = true)),
        )

    private fun detail(): PlayerDetail =
        PlayerDetail(
            uuid = "u1",
            name = "Alice",
            displayName = "Alice",
            online = true,
            operator = false,
            whitelisted = true,
            blocked = false,
            gameMode = "survival",
            health = 18.0,
            maxHealth = 20.0,
            foodLevel = 19,
            level = 7,
            world = "world",
            x = 1.0,
            y = 64.0,
            z = 2.0,
        )

    private fun player(
        uuid: String,
        name: String,
        online: Boolean,
        blocked: Boolean = false,
    ): PlayerSummaryPacket = PlayerSummaryPacket(uuid, name, name, online, false, !blocked, blocked)
}
