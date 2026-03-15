package dev.xcyn.venus.mixin;

import dev.xcyn.venus.network.VenusRawAuthPayload;
import dev.xcyn.venus.network.VenusRawPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
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
        return new CustomPacketPayload.FallbackProvider<>() {
            @Override
            @SuppressWarnings({"NullableProblems", "DataFlowIssue"})
            public @Nullable StreamCodec<FriendlyByteBuf, ? extends CustomPacketPayload> create(@Nullable Identifier identifier) {
                if (identifier == null) {
                    //noinspection unchecked
                    return (StreamCodec<FriendlyByteBuf, ? extends CustomPacketPayload>) original.create(null);
                }
                System.out.println("Venus FallbackProvider called for: " + identifier);
                if (identifier.equals(Identifier.fromNamespaceAndPath("venus", "key"))) {
                    //noinspection unchecked
                    return (StreamCodec<FriendlyByteBuf, ? extends CustomPacketPayload>) (StreamCodec<?, ?>) VenusRawPayload.CODEC;
                }
                if (identifier.equals(Identifier.fromNamespaceAndPath("venus", "auth"))) {
                    //noinspection unchecked
                    return (StreamCodec<FriendlyByteBuf, ? extends CustomPacketPayload>) (StreamCodec<?, ?>) VenusRawAuthPayload.CODEC;
                }
                //noinspection unchecked
                return ((CustomPacketPayload.FallbackProvider<FriendlyByteBuf>) original).create(identifier);
            }
        };
    }
}