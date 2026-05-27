package dev.ilgax.venus.channel

import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import org.junit.Test
import kotlin.test.assertEquals

class ChannelClientTest {
    @Test
    fun `HelloPayload codec works`() {
        val payload = ChannelClient.HelloPayload
        assertEquals("venus:hello", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        ChannelClient.HelloPayload.CODEC.encode(buf, payload)
        val decoded = ChannelClient.HelloPayload.CODEC.decode(buf)
        assertEquals(payload, decoded)
    }

    @Test
    fun `ClientKeyPayload codec works`() {
        val payload = ChannelClient.ClientKeyPayload("test_key")
        assertEquals("venus:key", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        ChannelClient.ClientKeyPayload.CODEC.encode(buf, payload)
        val decoded = ChannelClient.ClientKeyPayload.CODEC.decode(buf)
        assertEquals(payload.data, decoded.data)
    }

    @Test
    fun `AuthResponsePayload codec works`() {
        val payload = ChannelClient.AuthResponsePayload("test_auth")
        assertEquals("venus:auth", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        ChannelClient.AuthResponsePayload.CODEC.encode(buf, payload)
        val decoded = ChannelClient.AuthResponsePayload.CODEC.decode(buf)
        assertEquals(payload.data, decoded.data)
    }

    @Test
    fun `ErrorPayload codec works`() {
        val payload = ChannelClient.ErrorPayload("test_error")
        assertEquals("venus:error", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        ChannelClient.ErrorPayload.CODEC.encode(buf, payload)
        val decoded = ChannelClient.ErrorPayload.CODEC.decode(buf)
        assertEquals(payload.data, decoded.data)
    }

    @Test
    fun `CmdPayload codec works`() {
        val payload = ChannelClient.CmdPayload("test_cmd")
        assertEquals("venus:cmd", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        ChannelClient.CmdPayload.CODEC.encode(buf, payload)
        val decoded = ChannelClient.CmdPayload.CODEC.decode(buf)
        assertEquals(payload.data, decoded.data)
    }
}
