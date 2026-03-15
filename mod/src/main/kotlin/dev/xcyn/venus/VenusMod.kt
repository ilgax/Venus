package dev.xcyn.venus

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class VenusMod : ClientModInitializer {
    data object HelloPayload : CustomPacketPayload {
        val TYPE = CustomPacketPayload.Type<HelloPayload>(
            Identifier.fromNamespaceAndPath("venus", "hello")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, HelloPayload> =
            StreamCodec.unit(HelloPayload)

        override fun type(): CustomPacketPayload.Type<HelloPayload> = TYPE
    }


    override fun onInitializeClient() {
        println("Venus mod loaded!")

        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC)
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            ClientPlayNetworking.send(HelloPayload)
        }
    }
}