package dev.ilgax.venus.channel

import dev.ilgax.venus.network.AuthResponsePayload
import dev.ilgax.venus.network.ClientKeyPayload
import dev.ilgax.venus.network.CmdPayload
import dev.ilgax.venus.network.ErrorPayload
import dev.ilgax.venus.network.HelloPayload
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import org.junit.Test
import kotlin.test.assertEquals

class ChannelClientTest {
    @Test
    fun `HelloPayload codec works`() {
        val payload = HelloPayload
        assertEquals("venus:hello", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        HelloPayload.CODEC.encode(buf, payload)
        val decoded = HelloPayload.CODEC.decode(buf)
        assertEquals(payload, decoded)
    }

    @Test
    fun `ClientKeyPayload codec works`() {
        val payload = ClientKeyPayload("test_key")
        assertEquals("venus:key", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        ClientKeyPayload.CODEC.encode(buf, payload)
        val decoded = ClientKeyPayload.CODEC.decode(buf)
        assertEquals(payload.data, decoded.data)
    }

    @Test
    fun `AuthResponsePayload codec works`() {
        val payload = AuthResponsePayload("test_auth")
        assertEquals("venus:auth", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        AuthResponsePayload.CODEC.encode(buf, payload)
        val decoded = AuthResponsePayload.CODEC.decode(buf)
        assertEquals(payload.data, decoded.data)
    }

    @Test
    fun `ErrorPayload codec works`() {
        val payload = ErrorPayload("test_error")
        assertEquals("venus:error", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        ErrorPayload.CODEC.encode(buf, payload)
        val decoded = ErrorPayload.CODEC.decode(buf)
        assertEquals(payload.data, decoded.data)
    }

    @Test
    fun `CmdPayload codec works`() {
        val payload = CmdPayload("test_cmd")
        assertEquals("venus:cmd", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        CmdPayload.CODEC.encode(buf, payload)
        val decoded = CmdPayload.CODEC.decode(buf)
        assertEquals(payload.data, decoded.data)
    }
}
