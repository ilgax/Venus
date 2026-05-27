package dev.ilgax.venus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public record VenusRawReadyPayload(byte[] bytes) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("venus", "ready");
    public static final Type<VenusRawReadyPayload> TYPE = new Type<>(ID);
    @SuppressWarnings("unused")
    public static final StreamCodec<RegistryFriendlyByteBuf, VenusRawReadyPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.bytes()),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new VenusRawReadyPayload(bytes);
            }
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
