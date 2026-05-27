package dev.ilgax.venus.channel

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
import dev.ilgax.venus.protocol.ServerKeyPacket
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import java.util.Base64

class ChannelClient(
    private val json: Json,
    private val keyManager: KeyManager,
    private val log: (String) -> Unit = ::println,
) {
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
            val CODEC: StreamCodec<FriendlyByteBuf, ClientKeyPayload> = textCodec(::ClientKeyPayload) { it.data }
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
            val CODEC: StreamCodec<FriendlyByteBuf, AuthResponsePayload> = textCodec(::AuthResponsePayload) { it.data }
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
            val CODEC: StreamCodec<FriendlyByteBuf, ErrorPayload> = textCodec(::ErrorPayload) { it.data }
        }

        override fun type() = TYPE
    }

    data class CmdPayload(
        val data: String,
    ) : CustomPacketPayload {
        companion object {
            val TYPE =
                CustomPacketPayload.Type<CmdPayload>(
                    Identifier.fromNamespaceAndPath("venus", "cmd"),
                )
            val CODEC: StreamCodec<FriendlyByteBuf, CmdPayload> = textCodec(::CmdPayload) { it.data }
        }

        override fun type() = TYPE
    }

    fun register(packetHandler: PacketHandler) {
        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ClientKeyPayload.TYPE, ClientKeyPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(AuthResponsePayload.TYPE, AuthResponsePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ErrorPayload.TYPE, ErrorPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(CmdPayload.TYPE, CmdPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawPayload.TYPE, VenusRawPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawAuthPayload.TYPE, VenusRawAuthPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawReadyPayload.TYPE, VenusRawReadyPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawDataPayload.TYPE, VenusRawDataPayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(VenusRawPayload.TYPE) { payload, _ ->
            handleServerKey(payload.bytes().toString(Charsets.UTF_8))
        }
        ClientPlayNetworking.registerGlobalReceiver(VenusRawAuthPayload.TYPE) { payload, _ ->
            handleAuthChallenge(payload.bytes().toString(Charsets.UTF_8))
        }
        ClientPlayNetworking.registerGlobalReceiver(VenusRawReadyPayload.TYPE) { payload, _ ->
            packetHandler.handleReady(payload.bytes().toString(Charsets.UTF_8))
        }
        ClientPlayNetworking.registerGlobalReceiver(VenusRawDataPayload.TYPE) { payload, _ ->
            packetHandler.handleData(payload.bytes().toString(Charsets.UTF_8))
        }
    }

    fun sendHello() {
        ClientPlayNetworking.send(HelloPayload)
    }

    fun sendCommand(data: String) {
        ClientPlayNetworking.send(CmdPayload(data))
    }

    fun sendConsoleCommand(command: String) {
        val data =
            json.encodeToString(
                ConsoleCmdPacket.serializer(),
                ConsoleCmdPacket(type = "console_cmd", command = command),
            )
        sendCommand(data)
    }

    private fun handleServerKey(data: String) {
        val packet =
            try {
                json.decodeFromString(ServerKeyPacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid server key packet - ${e.message}")
                return
            }
        if (packet.type != "server_key") {
            log("Venus: unexpected server key packet type: ${packet.type}")
            return
        }
        val serverKeyBase64 = packet.publicKey
        log("Received server public key: $serverKeyBase64")

        val (host, port) =
            getServerAddress() ?: run {
                log("Venus: could not determine server address - aborting")
                return
            }
        val storedKey = ServerKeyStore.getStoredKey(host, port)
        if (storedKey == null) {
            log("Venus: first connection to $host:$port - trusting and storing key")
            ServerKeyStore.storeKey(host, port, serverKeyBase64)
        } else if (storedKey != serverKeyBase64) {
            log("Venus: WARNING - server key mismatch for $host:$port! Possible MITM. Aborting.")
            sendError("mitm_key_mismatch")
            return
        } else {
            log("Venus: server key verified for $host:$port")
        }

        val keyPacket =
            json.encodeToString(
                ClientKeyPacket.serializer(),
                ClientKeyPacket(type = "client_key", publicKey = keyManager.publicKeyBase64),
            )
        ClientPlayNetworking.send(ClientKeyPayload(keyPacket))
    }

    private fun handleAuthChallenge(data: String) {
        val packet =
            try {
                json.decodeFromString(AuthChallengePacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid auth challenge packet - ${e.message}")
                return
            }
        if (packet.type != "auth_challenge") {
            log("Venus: unexpected auth challenge packet type: ${packet.type}")
            return
        }
        val challenge =
            try {
                Base64.getDecoder().decode(packet.challenge)
            } catch (_: IllegalArgumentException) {
                log("Venus: invalid Base64 in auth challenge")
                return
            }
        val serverSig =
            try {
                Base64.getDecoder().decode(packet.serverSignature)
            } catch (_: IllegalArgumentException) {
                log("Venus: invalid Base64 in auth signature")
                return
            }

        val (host, port) =
            getServerAddress() ?: run {
                log("Venus: could not determine server address - aborting")
                return
            }
        val storedKeyB64 = ServerKeyStore.getStoredKey(host, port)
        if (storedKeyB64 == null) {
            log("Venus: no stored server key - aborting")
            return
        }
        val serverPublicKey = Handshake.decodePublicKey(storedKeyB64)
        if (!Handshake.verify(challenge, serverSig, serverPublicKey)) {
            log("Venus: WARNING - server signature verification failed! Possible MITM. Aborting.")
            sendError("mitm_sig_fail")
            return
        }

        val clientSig = Handshake.sign(challenge, keyManager.privateKey)
        val response =
            json.encodeToString(
                AuthResponsePacket.serializer(),
                AuthResponsePacket(
                    type = "auth_response",
                    challenge = packet.challenge,
                    clientSignature = Base64.getEncoder().encodeToString(clientSig),
                ),
            )
        ClientPlayNetworking.send(AuthResponsePayload(response))
        log("Venus: sent auth response")
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

    companion object {
        private fun <T : CustomPacketPayload> textCodec(
            create: (String) -> T,
            extract: (T) -> String,
        ): StreamCodec<FriendlyByteBuf, T> =
            StreamCodec.of(
                { buf, payload -> buf.writeBytes(extract(payload).toByteArray(Charsets.UTF_8)) },
                { buf ->
                    val bytes = ByteArray(buf.readableBytes())
                    buf.readBytes(bytes)
                    create(bytes.toString(Charsets.UTF_8))
                },
            )
    }
}
