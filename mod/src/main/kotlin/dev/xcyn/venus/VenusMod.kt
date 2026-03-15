package dev.xcyn.venus

import dev.xcyn.venus.auth.KeyManager
import dev.xcyn.venus.network.VenusRawPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import java.io.File

class VenusMod : ClientModInitializer {

    companion object {
        lateinit var keyManager: KeyManager
    }

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

        val venusFolder = File(Minecraft.getInstance().gameDirectory, "venus")
        keyManager = KeyManager(venusFolder)
        keyManager.loadOrGenerate()
        println("Venus client keypair loaded")

        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawPayload.TYPE, VenusRawPayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(VenusRawPayload.TYPE) { payload, _ ->
            val serverKeyBase64 = payload.bytes().toString(Charsets.UTF_8)
            println("Received server public key: $serverKeyBase64")
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            ClientPlayNetworking.send(HelloPayload)
        }
    }
}