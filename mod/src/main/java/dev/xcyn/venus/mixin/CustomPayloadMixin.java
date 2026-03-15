package dev.xcyn.venus.mixin;

import dev.xcyn.venus.network.VenusRawPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket")
public class CustomPayloadMixin {

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;",
                    ordinal = 0
            ),
            index = 0
    )
    private static CustomPacketPayload.FallbackProvider<?> replaceFallbackProvider(
            CustomPacketPayload.FallbackProvider<?> original
    ) {
        return new CustomPacketPayload.FallbackProvider<FriendlyByteBuf>() {
            @Override
            public StreamCodec<FriendlyByteBuf, ? extends CustomPacketPayload> create(Identifier identifier) {
                System.out.println("Venus FallbackProvider called for: " + identifier);
                if (identifier.equals(Identifier.fromNamespaceAndPath("venus", "key"))) {
                    //noinspection unchecked
                    return (StreamCodec<FriendlyByteBuf, ? extends CustomPacketPayload>) (StreamCodec<?, ?>) VenusRawPayload.CODEC;
                }
                //noinspection unchecked
                return ((CustomPacketPayload.FallbackProvider<FriendlyByteBuf>) original).create(identifier);
            }
        };
    }
}