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
}
