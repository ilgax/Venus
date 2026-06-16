package dev.ilgax.venus.backend

import dev.ilgax.venus.protocol.VenusChannels
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackendChannelHandlerTest {
    private val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")

    @Test
    fun `registered inbound channels map to backend handlers`() {
        assertEquals(BackendIncomingChannel.HELLO, BackendIncomingChannel.fromChannel(VenusChannels.HELLO))
        assertEquals(BackendIncomingChannel.KEY, BackendIncomingChannel.fromChannel(VenusChannels.KEY))
        assertEquals(BackendIncomingChannel.AUTH, BackendIncomingChannel.fromChannel(VenusChannels.AUTH))
        assertEquals(BackendIncomingChannel.ERROR, BackendIncomingChannel.fromChannel(VenusChannels.ERROR))
        assertEquals(BackendIncomingChannel.CMD, BackendIncomingChannel.fromChannel(VenusChannels.CMD))
    }

    @Test
    fun `unknown inbound channel is ignored`() {
        assertNull(BackendIncomingChannel.fromChannel("venus:transfer"))
    }

    @Test
    fun `handle routes hello`() {
        val auth = mockk<BackendAuthHandler>(relaxed = true)
        val router = mockk<BackendPacketRouter>(relaxed = true)
        val handler = BackendChannelHandler(auth, router)

        handler.handle(VenusChannels.HELLO, player, "hello data")

        verify { auth.handleHello(player) }
    }

    @Test
    fun `handle routes cmd`() {
        val auth = mockk<BackendAuthHandler>(relaxed = true)
        val router = mockk<BackendPacketRouter>(relaxed = true)
        val handler = BackendChannelHandler(auth, router)

        handler.handle(VenusChannels.CMD, player, "cmd data")

        verify { router.handleCommand(player, "cmd data") }
    }
}
