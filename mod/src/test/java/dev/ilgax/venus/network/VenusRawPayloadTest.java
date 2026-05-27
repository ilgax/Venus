package dev.ilgax.venus.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class VenusRawPayloadTest {

    @Test
    public void testAuthPayload() {
        byte[] data = new byte[]{1, 2, 3};
        VenusRawAuthPayload payload = new VenusRawAuthPayload(data);
        assertEquals(VenusRawAuthPayload.ID, payload.type().id());
        assertArrayEquals(data, payload.bytes());

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        VenusRawAuthPayload.CODEC.encode(buf, payload);
        VenusRawAuthPayload decoded = VenusRawAuthPayload.CODEC.decode(buf);
        assertArrayEquals(payload.bytes(), decoded.bytes());
    }

    @Test
    public void testDataPayload() {
        byte[] data = new byte[]{4, 5, 6};
        VenusRawDataPayload payload = new VenusRawDataPayload(data);
        assertEquals(VenusRawDataPayload.ID, payload.type().id());
        assertArrayEquals(data, payload.bytes());

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        VenusRawDataPayload.CODEC.encode(buf, payload);
        VenusRawDataPayload decoded = VenusRawDataPayload.CODEC.decode(buf);
        assertArrayEquals(payload.bytes(), decoded.bytes());
    }

    @Test
    public void testKeyPayload() {
        byte[] data = new byte[]{7, 8, 9};
        VenusRawPayload payload = new VenusRawPayload(data);
        assertEquals(VenusRawPayload.ID, payload.type().id());
        assertArrayEquals(data, payload.bytes());

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        VenusRawPayload.CODEC.encode(buf, payload);
        VenusRawPayload decoded = VenusRawPayload.CODEC.decode(buf);
        assertArrayEquals(payload.bytes(), decoded.bytes());
    }

    @Test
    public void testReadyPayload() {
        byte[] data = new byte[]{10, 11, 12};
        VenusRawReadyPayload payload = new VenusRawReadyPayload(data);
        assertEquals(VenusRawReadyPayload.ID, payload.type().id());
        assertArrayEquals(data, payload.bytes());

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        VenusRawReadyPayload.CODEC.encode(buf, payload);
        VenusRawReadyPayload decoded = VenusRawReadyPayload.CODEC.decode(buf);
        assertArrayEquals(payload.bytes(), decoded.bytes());
    }
}
