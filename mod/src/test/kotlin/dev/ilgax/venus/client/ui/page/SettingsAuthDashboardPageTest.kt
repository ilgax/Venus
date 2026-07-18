package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.client.ui.UiTestFixture
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.widget.VenusSlider
import dev.ilgax.venus.client.ui.widget.VenusToggle
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import dev.ilgax.venus.protocol.StatsPacket
import dev.ilgax.venus.state.SessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsAuthDashboardPageTest : UiTestFixture() {
    @Test
    fun `settings round trip and controls save current values`() {
        val saved = mutableListOf<SettingsPage.Settings>()
        val page = SettingsPage(saved::add)
        val initial = SettingsPage.Settings(true, false, 0.4f, false, false, 900)

        page.applySettings(initial)
        assertEquals(initial, page.currentSettings())

        val widgets = page.widgets()
        page.layout(Bounds(0, 0, 500, 300))
        page.onEnter()
        assertTrue(widgets.all { it.visible })

        (widgets[0] as VenusToggle).set(false)
        (widgets[2] as VenusToggle).set(true)
        (widgets[4] as VenusSlider).set(1_500.0)
        (widgets[5] as VenusSlider).set(0.65)

        assertEquals(4, saved.size)
        assertEquals(false, saved.last().compactMode)
        assertEquals(true, saved.last().showPlayerHeads)
        assertEquals(1_500, saved.last().consoleHistoryLimit)
        assertEquals(0.65f, saved.last().backgroundOpacity)

        page.render(graphics, font, 0, 0, 0f)
        page.onLeave()
        assertTrue(widgets.none { it.visible })
    }

    @Test
    fun `authentication page presents every handshake state without invented requests`() {
        val page = AuthPage({}, {}, {})
        page.layout(Bounds(0, 0, 500, 300))

        page.render(graphics, font, 0, 0, 0f)
        SessionState.markExpectingReady()
        page.render(graphics, font, 0, 0, 0f)
        SessionState.markActive()
        page.render(graphics, font, 0, 0, 0f)

        assertFalse(page.mouseClicked(20.0, 50.0, 1))
        assertFalse(page.mouseClicked(20.0, 50.0, 0))
    }

    @Test
    fun `dashboard subscribes once and routes only rendered player rows`() {
        var subscriptions = 0
        var listRequests = 0
        val navigated = mutableListOf<String>()
        val page = DashboardPage({ subscriptions++ }, { listRequests++ }, { false }, navigated::add)
        page.layout(Bounds(0, 0, 600, 400))

        page.onEnter()
        assertEquals(0, subscriptions)
        page.render(graphics, font, 0, 0, 0f)

        SessionState.markActive()
        page.onEnter()
        page.onEnter()
        assertEquals(1, subscriptions)
        assertEquals(0, listRequests)
        page.render(graphics, font, 0, 0, 0f)

        SessionState.updateStats(
            StatsPacket(
                type = "stats",
                tps = 14.5,
                mspt = 68.9,
                ramUsed = 2_048,
                ramMax = 4_096,
                uptime = 3_661,
                onlinePlayers = 1,
                maxPlayers = 20,
            ),
        )
        SessionState.updatePlayerList(playerList())
        page.render(graphics, font, 370, 110, 0f)

        assertFalse(page.mouseClicked(370.0, 110.0, 1))
        assertTrue(page.mouseClicked(370.0, 110.0, 0))
        assertEquals(listOf("u1"), navigated)
        assertFalse(page.mouseClicked(10.0, 10.0, 0))
    }

    private fun playerList(): PlayerListPacket =
        PlayerListPacket(
            type = "player_list",
            requestId = "players-1",
            onlineCount = 1,
            maxPlayers = 20,
            onlinePlayers = listOf(player("u1", "Alice", online = true)),
            whitelistedPlayers = emptyList(),
            blockedPlayers = emptyList(),
        )

    private fun player(
        uuid: String,
        name: String,
        online: Boolean,
    ): PlayerSummaryPacket =
        PlayerSummaryPacket(
            uuid = uuid,
            name = name,
            displayName = name,
            online = online,
            operator = false,
            whitelisted = true,
            blocked = false,
        )
}
