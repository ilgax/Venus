package dev.ilgax.venus

import dev.ilgax.venus.auth.Handshake
import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.ServerKeyStore
import dev.ilgax.venus.network.VenusRawAuthPayload
import dev.ilgax.venus.network.VenusRawDataPayload
import dev.ilgax.venus.network.VenusRawPayload
import dev.ilgax.venus.network.VenusRawReadyPayload
import dev.ilgax.venus.protocol.AuthChallengePacket
import dev.ilgax.venus.protocol.AuthResponsePacket
import dev.ilgax.venus.protocol.ClientKeyPacket
import dev.ilgax.venus.protocol.ConsoleCmdPacket
import dev.ilgax.venus.protocol.ErrorPacket
import dev.ilgax.venus.protocol.ReadyPacket
import dev.ilgax.venus.protocol.ServerKeyPacket
import kotlinx.serialization.json.Json
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

    private val json = Json { ignoreUnknownKeys = true }

    data object HelloPayload : CustomPacketPayload {
        val TYPE =
            CustomPacketPayload.Type<HelloPayload>(
                Identifier.fromNamespaceAndPath("venus", "hello"),
            )
        val CODEC: StreamCodec<FriendlyByteBuf, HelloPayload> =
            StreamCodec.unit(HelloPayload)

        override fun type(): CustomPacketPayload.Type<HelloPayload> = TYPE
    }

    data class ClientKeyPayload(
        val data: String,
    ) : CustomPacketPayload {
        companion object {
            val TYPE =
                CustomPacketPayload.Type<ClientKeyPayload>(
                    Identifier.fromNamespaceAndPath("venus", "key"),
                )
            val CODEC: StreamCodec<FriendlyByteBuf, ClientKeyPayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeBytes(payload.data.toByteArray(Charsets.UTF_8)) },
                    { buf ->
                        val bytes = ByteArray(buf.readableBytes())
                        buf.readBytes(bytes)
                        ClientKeyPayload(bytes.toString(Charsets.UTF_8))
                    },
                )
        }

        override fun type() = TYPE
    }

    data class AuthResponsePayload(
        val data: String,
    ) : CustomPacketPayload {
        companion object {
            val TYPE =
                CustomPacketPayload.Type<AuthResponsePayload>(
                    Identifier.fromNamespaceAndPath("venus", "auth"),
                )
            val CODEC: StreamCodec<FriendlyByteBuf, AuthResponsePayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeBytes(payload.data.toByteArray(Charsets.UTF_8)) },
                    { buf ->
                        val bytes = ByteArray(buf.readableBytes())
                        buf.readBytes(bytes)
                        AuthResponsePayload(bytes.toString(Charsets.UTF_8))
                    },
                )
        }

        override fun type() = TYPE
    }

    data class ErrorPayload(
        val data: String,
    ) : CustomPacketPayload {
        companion object {
            val TYPE =
                CustomPacketPayload.Type<ErrorPayload>(
                    Identifier.fromNamespaceAndPath("venus", "error"),
                )
            val CODEC: StreamCodec<FriendlyByteBuf, ErrorPayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeBytes(payload.data.toByteArray(Charsets.UTF_8)) },
                    { buf ->
                        val bytes = ByteArray(buf.readableBytes())
                        buf.readBytes(bytes)
                        ErrorPayload(bytes.toString(Charsets.UTF_8))
                    },
                )
        }

        override fun type() = TYPE
    }

    data class CmdPayload(
        val command: String,
    ) : CustomPacketPayload {
        companion object {
            val TYPE =
                CustomPacketPayload.Type<CmdPayload>(
                    Identifier.fromNamespaceAndPath("venus", "cmd"),
                )
            val CODEC: StreamCodec<FriendlyByteBuf, CmdPayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeBytes(payload.command.toByteArray(Charsets.UTF_8)) },
                    { buf ->
                        val bytes = ByteArray(buf.readableBytes())
                        buf.readBytes(bytes)
                        CmdPayload(bytes.toString(Charsets.UTF_8))
                    },
                )
        }

        override fun type() = TYPE
    }

    override fun onInitializeClient() {
        println("Venus mod loaded!")

        val venusFolder = File(Minecraft.getInstance().gameDirectory, "config/venus")
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
            val packet = try {
                json.decodeFromString(ServerKeyPacket.serializer(), payload.bytes().toString(Charsets.UTF_8))
            } catch (e: Exception) {
                println("Venus: invalid server key packet - ${e.message}")
                return@registerGlobalReceiver
            }
            if (packet.type != "server_key") {
                println("Venus: unexpected server key packet type: ${packet.type}")
                return@registerGlobalReceiver
            }
            val serverKeyBase64 = packet.publicKey
            println("Received server public key: $serverKeyBase64")

            val (host, port) =
                getServerAddress() ?: run {
                    println("Venus: could not determine server address - aborting")
                    return@registerGlobalReceiver
                }

            val storedKey = ServerKeyStore.getStoredKey(host, port)

            if (storedKey == null) {
                println("Venus: first connection to $host:$port - trusting and storing key")
                ServerKeyStore.storeKey(host, port, serverKeyBase64)
            } else if (storedKey != serverKeyBase64) {
                println("Venus: WARNING - server key mismatch for $host:$port! Possible MITM. Aborting.")
                sendError("mitm_key_mismatch")
                return@registerGlobalReceiver
            } else {
                println("Venus: server key verified for $host:$port")
            }

            val keyPacket =
                json.encodeToString(
                    ClientKeyPacket.serializer(),
                    ClientKeyPacket(type = "client_key", publicKey = keyManager.publicKeyBase64),
                )
            ClientPlayNetworking.send(ClientKeyPayload(keyPacket))
        }

        ClientPlayNetworking.registerGlobalReceiver(VenusRawAuthPayload.TYPE) { payload, _ ->
            val packet = try {
                json.decodeFromString(AuthChallengePacket.serializer(), payload.bytes().toString(Charsets.UTF_8))
            } catch (e: Exception) {
                println("Venus: invalid auth challenge packet - ${e.message}")
                return@registerGlobalReceiver
            }
            if (packet.type != "auth_challenge") {
                println("Venus: unexpected auth challenge packet type: ${packet.type}")
                return@registerGlobalReceiver
            }
            val challengeB64 = packet.challenge
            val challenge = try {
                Base64.getDecoder().decode(challengeB64)
            } catch (_: IllegalArgumentException) {
                println("Venus: invalid Base64 in auth challenge")
                return@registerGlobalReceiver
            }
            val serverSig = try {
                Base64.getDecoder().decode(packet.serverSignature)
            } catch (_: IllegalArgumentException) {
                println("Venus: invalid Base64 in auth signature")
                return@registerGlobalReceiver
            }

            val (host, port) =
                getServerAddress() ?: run {
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
                sendError("mitm_sig_fail")
                return@registerGlobalReceiver
            }

            val clientSig = Handshake.sign(challenge, keyManager.privateKey)
            val response =
                json.encodeToString(
                    AuthResponsePacket.serializer(),
                    AuthResponsePacket(
                        type = "auth_response",
                        challenge = challengeB64,
                        clientSignature = Base64.getEncoder().encodeToString(clientSig),
                    ),
                )
            ClientPlayNetworking.send(AuthResponsePayload(response))
            println("Venus: sent auth response")
        }

        ClientPlayNetworking.registerGlobalReceiver(VenusRawReadyPayload.TYPE) { payload, _ ->
            val packet = try {
                json.decodeFromString(ReadyPacket.serializer(), payload.bytes().toString(Charsets.UTF_8))
            } catch (e: Exception) {
                println("Venus: invalid ready packet - ${e.message}")
                return@registerGlobalReceiver
            }
            if (packet.type != "ready") {
                println("Venus: unexpected ready packet type: ${packet.type}")
                return@registerGlobalReceiver
            }
            println("Venus: session active!")
            sessionActive = true
            ClientPlayNetworking.send(
                CmdPayload("""{"type":"stat_subscribe","interval_seconds":2,"stats":["tps","ram","mspt","uptime"]}"""),
            )
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
                val packet =
                    json.encodeToString(
                        ConsoleCmdPacket.serializer(),
                        ConsoleCmdPacket(type = "console_cmd", command = command),
                    )
                ClientPlayNetworking.send(CmdPayload(packet))
                false
            } else {
                true
            }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            sessionActive = false
        }
    }

    private fun sendError(reason: String) {
        val data =
            json.encodeToString(
                ErrorPacket.serializer(),
                ErrorPacket(type = "error", reason = reason),
            )
        ClientPlayNetworking.send(ErrorPayload(data))
    }

    private fun getServerAddress(): Pair<String, Int>? {
        val serverInfo = Minecraft.getInstance().currentServer ?: return null
        val parts = serverInfo.ip.split(":")
        val host = parts[0]
        val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 25565 else 25565
        return Pair(host, port)
    }
}
