package dev.ilgax.venus.channel

import dev.ilgax.venus.protocol.VenusChannels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChannelListenerTest {
    @Test
    fun `registered inbound channels map to their handlers`() {
        assertEquals(IncomingChannel.HELLO, IncomingChannel.fromChannel(VenusChannels.HELLO))
        assertEquals(IncomingChannel.KEY, IncomingChannel.fromChannel(VenusChannels.KEY))
        assertEquals(IncomingChannel.AUTH, IncomingChannel.fromChannel(VenusChannels.AUTH))
        assertEquals(IncomingChannel.ERROR, IncomingChannel.fromChannel(VenusChannels.ERROR))
        assertEquals(IncomingChannel.CMD, IncomingChannel.fromChannel(VenusChannels.CMD))
    }

    @Test
    fun `unknown inbound channel is ignored`() {
        assertNull(IncomingChannel.fromChannel("venus:transfer"))
    }

    @Test
    fun `onPluginMessageReceived routes hello`() {
        val authHandler = io.mockk.mockk<dev.ilgax.venus.handlers.AuthHandler>(relaxed = true)
        val packetRouter = io.mockk.mockk<PacketRouter>(relaxed = true)
        val player = io.mockk.mockk<org.bukkit.entity.Player>(relaxed = true)
        val listener = ChannelListener(authHandler, packetRouter)

        val message = "hello data".toByteArray(Charsets.UTF_8)
        listener.onPluginMessageReceived(VenusChannels.HELLO, player, message)
        io.mockk.verify { authHandler.handleHello(player) }
    }

    @Test
    fun `onPluginMessageReceived routes cmd`() {
        val authHandler = io.mockk.mockk<dev.ilgax.venus.handlers.AuthHandler>(relaxed = true)
        val packetRouter = io.mockk.mockk<PacketRouter>(relaxed = true)
        val player = io.mockk.mockk<org.bukkit.entity.Player>(relaxed = true)
        val listener = ChannelListener(authHandler, packetRouter)

        val data = "cmd data"
        val message = data.toByteArray(Charsets.UTF_8)
        listener.onPluginMessageReceived(VenusChannels.CMD, player, message)
        io.mockk.verify { packetRouter.handleCommand(player, data) }
    }
}
