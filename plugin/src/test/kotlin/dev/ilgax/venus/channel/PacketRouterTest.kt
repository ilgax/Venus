package dev.ilgax.venus.channel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PacketRouterTest {
    @Test
    fun `known command packet types map to handlers`() {
        assertEquals(CommandRoute.CONSOLE_CMD, CommandRoute.fromPacketType("console_cmd"))
        assertEquals(CommandRoute.STAT_SUBSCRIBE, CommandRoute.fromPacketType("stat_subscribe"))
        assertEquals(CommandRoute.STAT_GET, CommandRoute.fromPacketType("stat_get"))
    }

    @Test
    fun `unknown command packet type has no route`() {
        assertNull(CommandRoute.fromPacketType("file_get"))
    }
}
