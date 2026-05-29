package dev.ilgax.venus.gui.tabs

import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayersTabTest {
    private fun createSummary(
        uuid: String,
        name: String,
    ) = PlayerSummaryPacket(
        uuid = uuid,
        name = name,
        displayName = name,
        online = true,
        operator = false,
        whitelisted = false,
        blocked = false,
    )

    private val testList =
        PlayerListPacket(
            type = "player_list",
            onlineCount = 1,
            maxPlayers = 20,
            onlinePlayers = listOf(createSummary("uuid-1", "OnlinePlayer")),
            whitelistedPlayers = listOf(createSummary("uuid-2", "WhitelistedPlayer")),
            blockedPlayers = listOf(createSummary("uuid-3", "BlockedPlayer")),
        )

    @Test
    fun `playersForCategory returns correct list for ONLINE`() {
        val players = PlayersTab.playersForCategory(testList, PlayerCategory.ONLINE)
        assertEquals(1, players.size)
        assertEquals("OnlinePlayer", players[0].name)
    }

    @Test
    fun `playersForCategory returns correct list for WHITELIST`() {
        val players = PlayersTab.playersForCategory(testList, PlayerCategory.WHITELIST)
        assertEquals(1, players.size)
        assertEquals("WhitelistedPlayer", players[0].name)
    }

    @Test
    fun `playersForCategory returns correct list for BLOCKED`() {
        val players = PlayersTab.playersForCategory(testList, PlayerCategory.BLOCKED)
        assertEquals(1, players.size)
        assertEquals("BlockedPlayer", players[0].name)
    }

    @Test
    fun `playersForCategory returns empty list if packet is null`() {
        val players = PlayersTab.playersForCategory(null, PlayerCategory.ONLINE)
        assertTrue(players.isEmpty())
    }

    @Test
    fun `maxScrollOffset computes correct bounds`() {
        // 24 is row height. 4 is padding/borders.
        // listHeight = 100 -> (100 - 4) / 24 = 96 / 24 = 4 visible rows
        // 10 items total. max scroll = 10 - 4 = 6.
        val maxOffset = PlayersTab.maxScrollOffset(100, 10)
        assertEquals(6, maxOffset)

        // 3 items total, 4 visible rows. max scroll = 0.
        val maxOffsetZero = PlayersTab.maxScrollOffset(100, 3)
        assertEquals(0, maxOffsetZero)
    }
}
