package dev.xcyn.venus

import dev.xcyn.venus.auth.Handshake
import dev.xcyn.venus.auth.KeyManager
import dev.xcyn.venus.auth.ServerKeyStore
import dev.xcyn.venus.network.VenusRawAuthPayload
import dev.xcyn.venus.network.VenusRawDataPayload
import dev.xcyn.venus.network.VenusRawPayload
import dev.xcyn.venus.network.VenusRawReadyPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
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
        var sessionActive = false
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

    data class ErrorPayload(val reason: String) : CustomPacketPayload {
        companion object {
            val TYPE = CustomPacketPayload.Type<ErrorPayload>(
                Identifier.fromNamespaceAndPath("venus", "error")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, ErrorPayload> = StreamCodec.of(
                { buf, payload -> buf.writeBytes(payload.reason.toByteArray(Charsets.UTF_8)) },
                { buf ->
                    val bytes = ByteArray(buf.readableBytes())
                    buf.readBytes(bytes)
                    ErrorPayload(bytes.toString(Charsets.UTF_8))
                }
            )
        }
        override fun type() = TYPE
    }

    data class CmdPayload(val command: String) : CustomPacketPayload {
        companion object {
            val TYPE = CustomPacketPayload.Type<CmdPayload>(
                Identifier.fromNamespaceAndPath("venus", "cmd")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, CmdPayload> = StreamCodec.of(
                { buf, payload -> buf.writeBytes(payload.command.toByteArray(Charsets.UTF_8)) },
                { buf ->
                    val bytes = ByteArray(buf.readableBytes())
                    buf.readBytes(bytes)
                    CmdPayload(bytes.toString(Charsets.UTF_8))
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
        ServerKeyStore.init(venusFolder)

        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ClientKeyPayload.TYPE, ClientKeyPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(AuthResponsePayload.TYPE, AuthResponsePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ErrorPayload.TYPE, ErrorPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawPayload.TYPE, VenusRawPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawAuthPayload.TYPE, VenusRawAuthPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawReadyPayload.TYPE, VenusRawReadyPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(CmdPayload.TYPE, CmdPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawDataPayload.TYPE, VenusRawDataPayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(VenusRawPayload.TYPE) { payload, _ ->
            val serverKeyBase64 = payload.bytes().toString(Charsets.UTF_8)
            println("Received server public key: $serverKeyBase64")

            val (host, port) = getServerAddress() ?: run {
                println("Venus: could not determine server address - aborting")
                return@registerGlobalReceiver
            }

            val storedKey = ServerKeyStore.getStoredKey(host, port)

            if (storedKey == null) {
                println("Venus: first connection to $host:$port - trusting and storing key")
                ServerKeyStore.storeKey(host, port, serverKeyBase64)
            } else if (storedKey != serverKeyBase64) {
                println("Venus: WARNING - server key mismatch for $host:$port! Possible MITM. Aborting.")
                ClientPlayNetworking.send(ErrorPayload("mitm_key_mismatch"))
                return@registerGlobalReceiver
            } else {
                println("Venus: server key verified for $host:$port")
            }

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
            val serverSigB64 = parts[1]
            val challenge = Base64.getDecoder().decode(challengeB64)
            val serverSig = Base64.getDecoder().decode(serverSigB64)

            val (host, port) = getServerAddress() ?: run {
                println("Venus: could not determine server address - aborting")
                return@registerGlobalReceiver
            }

            val storedKeyB64 = ServerKeyStore.getStoredKey(host, port)
            if (storedKeyB64 == null) {
                println("Venus: no stored server key - aborting")
                return@registerGlobalReceiver
            }

            val serverPublicKey = Handshake.decodePublicKey(storedKeyB64)
            if (!Handshake.verify(challenge, serverSig, serverPublicKey)) {
                println("Venus: WARNING - server signature verification failed! Possible MITM. Aborting.")
                ClientPlayNetworking.send(ErrorPayload("mitm_sig_fail"))
                return@registerGlobalReceiver
            }

            val clientSig = Handshake.sign(challenge, keyManager.privateKey)
            val response = "$challengeB64.${Base64.getEncoder().encodeToString(clientSig)}"
            ClientPlayNetworking.send(AuthResponsePayload(response))
            println("Venus: sent auth response")
        }

        ClientPlayNetworking.registerGlobalReceiver(VenusRawReadyPayload.TYPE) { _, _ ->
            println("Venus: session active!")
            sessionActive = true
            ClientPlayNetworking.send(CmdPayload("""{"type":"stat_subscribe","interval_seconds":2,"stats":["tps","ram","mspt","uptime"]}"""))
            // TODO: Open gui
        }

        ClientPlayNetworking.registerGlobalReceiver(VenusRawDataPayload.TYPE) { payload, _ ->
            val data = payload.bytes().toString(Charsets.UTF_8)
            println("Venus data received: $data")
            // TODO: parse and store in SessionState for GUI
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            ClientPlayNetworking.send(HelloPayload)
        }

        ClientSendMessageEvents.ALLOW_CHAT.register { message ->
            if (sessionActive && message.startsWith("$")) {
                val command = message.removePrefix("$").trim()
                ClientPlayNetworking.send(CmdPayload(command))
                false
            } else {
                true
            }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            sessionActive = false
        }
    }

    private fun getServerAddress(): Pair<String, Int>? {
        val serverInfo = Minecraft.getInstance().currentServer ?: return null
        val parts = serverInfo.ip.split(":")
        val host = parts[0]
        val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 25565 else 25565
        return Pair(host, port)
    }
}