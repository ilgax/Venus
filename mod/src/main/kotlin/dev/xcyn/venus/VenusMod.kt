package dev.xcyn.venus

import dev.xcyn.venus.auth.Handshake
import dev.xcyn.venus.auth.KeyManager
import dev.xcyn.venus.network.VenusRawAuthPayload
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
import java.util.Base64

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

    data class ClientKeyPayload(val keyBase64: String) : CustomPacketPayload {
        companion object {
            val TYPE = CustomPacketPayload.Type<ClientKeyPayload>(
                Identifier.fromNamespaceAndPath("venus", "key")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, ClientKeyPayload> = StreamCodec.of(
                { buf, payload -> buf.writeBytes(payload.keyBase64.toByteArray(Charsets.UTF_8)) },
                { buf ->
                    val bytes = ByteArray(buf.readableBytes())
                    buf.readBytes(bytes)
                    ClientKeyPayload(bytes.toString(Charsets.UTF_8))
                }
            )
        }
        override fun type() = TYPE
    }

    data class AuthResponsePayload(val response: String) : CustomPacketPayload {
        companion object {
            val TYPE = CustomPacketPayload.Type<AuthResponsePayload>(
                Identifier.fromNamespaceAndPath("venus", "auth")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, AuthResponsePayload> = StreamCodec.of(
                { buf, payload -> buf.writeBytes(payload.response.toByteArray(Charsets.UTF_8)) },
                { buf ->
                    val bytes = ByteArray(buf.readableBytes())
                    buf.readBytes(bytes)
                    AuthResponsePayload(bytes.toString(Charsets.UTF_8))
                }
            )
        }
        override fun type() = TYPE
    }

    override fun onInitializeClient() {
        println("Venus mod loaded!")

        val venusFolder = File(Minecraft.getInstance().gameDirectory, "venus")
        keyManager = KeyManager(venusFolder)
        keyManager.loadOrGenerate()
        println("Venus client keypair loaded")

        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ClientKeyPayload.TYPE, ClientKeyPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(AuthResponsePayload.TYPE, AuthResponsePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawPayload.TYPE, VenusRawPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawAuthPayload.TYPE, VenusRawAuthPayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(VenusRawPayload.TYPE) { payload, _ ->
            val serverKeyBase64 = payload.bytes().toString(Charsets.UTF_8)
            println("Received server public key: $serverKeyBase64")
            // TODO: TOFU — store and verify server key
            ClientPlayNetworking.send(ClientKeyPayload(keyManager.publicKeyBase64))
        }

        ClientPlayNetworking.registerGlobalReceiver(VenusRawAuthPayload.TYPE) { payload, _ ->
            val data = payload.bytes().toString(Charsets.UTF_8)
            val parts = data.split(".")
            if (parts.size != 2) {
                println("Venus: invalid auth challenge format")
                return@registerGlobalReceiver
            }

            val challengeB64 = parts[0]
            //val serverSigB64 = parts[1]
            val challenge = Base64.getDecoder().decode(challengeB64)

            // TODO: verify server signature with stored server public key

            val clientSig = Handshake.sign(challenge, keyManager.privateKey)
            val response = "$challengeB64.${Base64.getEncoder().encodeToString(clientSig)}"
            ClientPlayNetworking.send(AuthResponsePayload(response))
            println("Venus: sent auth response")
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            ClientPlayNetworking.send(HelloPayload)
        }
    }
}