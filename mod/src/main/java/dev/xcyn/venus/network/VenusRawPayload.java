package dev.xcyn.venus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public record VenusRawPayload(byte[] bytes) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("venus", "key");
    public static final Type<VenusRawPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, VenusRawPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.bytes()),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new VenusRawPayload(bytes);
            }
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}