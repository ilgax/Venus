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
import dev.ilgax.venus.protocol.ConsoleLogSubscribePacket
import dev.ilgax.venus.protocol.ErrorPacket
import dev.ilgax.venus.protocol.PlayerDetailGetPacket
import dev.ilgax.venus.protocol.PlayerListGetPacket
import dev.ilgax.venus.protocol.ServerKeyPacket
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import java.util.Base64

class ChannelClient(
    private val json: Json,
    private val keyManager: KeyManager,
    private val log: (String) -> Unit,
    private val showAuthFailure: (String) -> Unit = {},
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
        PayloadTypeRegistry.playS2C().register(ErrorPayload.TYPE, ErrorPayload.CODEC)
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
        ClientPlayNetworking.registerGlobalReceiver(ErrorPayload.TYPE) { payload, _ ->
            packetHandler.handleError(payload.data)
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

    fun sendLogSubscribe() {
        val data =
            json.encodeToString(
                ConsoleLogSubscribePacket.serializer(),
                ConsoleLogSubscribePacket(type = "log_subscribe"),
            )
        sendCommand(data)
    }

    fun sendPlayerListGet() {
        val data =
            json.encodeToString(
                PlayerListGetPacket.serializer(),
                PlayerListGetPacket(type = "player_list_get"),
            )
        sendCommand(data)
    }

    fun sendPlayerDetailGet(uuid: String) {
        val data =
            json.encodeToString(
                PlayerDetailGetPacket.serializer(),
                PlayerDetailGetPacket(type = "player_detail_get", uuid = uuid),
            )
        sendCommand(data)
    }

    private fun handleServerKey(data: String) {
        val packet =
            try {
                json.decodeFromString(ServerKeyPacket.serializer(), data)
            } catch (e: Exception) {
                failAuth("Invalid server key packet.", "Venus: invalid server key packet - ${e.message}")
                return
            }
        if (packet.type != "server_key") {
            failAuth(
                "Unexpected server key packet.",
                "Venus: unexpected server key packet type: ${packet.type}",
            )
            return
        }
        val serverKeyBase64 = packet.publicKey

        val identity =
            getServerAddress() ?: run {
                failAuth("Could not determine server address.", "Venus: could not determine server address")
                return
            }
        val storedKey = ServerKeyStore.getStoredKey(identity)
        if (storedKey == null) {
            log("Venus: first connection to $identity")
            ServerKeyStore.storeKey(identity, serverKeyBase64)
        } else if (storedKey != serverKeyBase64) {
            failAuth("Server key mismatch.", "Venus: WARNING server key mismatch for $identity")
            sendError("mitm_key_mismatch")
            return
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
                failAuth("Invalid auth challenge packet.", "Venus: invalid auth challenge packet - ${e.message}")
                return
            }
        if (packet.type != "auth_challenge") {
            failAuth(
                "Unexpected auth challenge packet.",
                "Venus: unexpected auth challenge packet type: ${packet.type}",
            )
            return
        }
        val challenge =
            try {
                Base64.getDecoder().decode(packet.challenge)
            } catch (_: IllegalArgumentException) {
                failAuth("Invalid auth challenge.", "Venus: invalid Base64 in auth challenge")
                return
            }
        val serverSig =
            try {
                Base64.getDecoder().decode(packet.serverSignature)
            } catch (_: IllegalArgumentException) {
                failAuth("Invalid auth signature.", "Venus: invalid Base64 in auth signature")
                return
            }

        val identity =
            getServerAddress() ?: run {
                failAuth("Could not determine server address.", "Venus: could not determine server address")
                return
            }
        val storedKeyB64 = ServerKeyStore.getStoredKey(identity)
        if (storedKeyB64 == null) {
            failAuth("No trusted server key was found.", "Venus: no stored server key")
            return
        }
        val serverPublicKey =
            try {
                Handshake.decodePublicKey(storedKeyB64)
            } catch (e: Exception) {
                failAuth("Stored server key is invalid.", "Venus: invalid stored server key - ${e.message}")
                return
            }
        if (!Handshake.verify(challenge, serverSig, serverPublicKey)) {
            failAuth("Server signature verification failed.", "Venus: WARNING - server signature verification failed")
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
    }

    private fun sendError(reason: String) {
        val data =
            json.encodeToString(
                ErrorPacket.serializer(),
                ErrorPacket(type = "error", reason = reason),
            )
        ClientPlayNetworking.send(ErrorPayload(data))
    }

    private fun failAuth(
        toastMessage: String,
        logMessage: String,
    ) {
        log(logMessage)
        showAuthFailure(toastMessage)
    }

    private fun getServerAddress(): ServerKeyStore.ServerIdentity? {
        val serverInfo = Minecraft.getInstance().currentServer ?: return null
        if (!ServerAddress.isValidAddress(serverInfo.ip)) return null
        val address = ServerAddress.parseString(serverInfo.ip)
        val host = address.host
        return ServerKeyStore.ServerIdentity(
            host = ServerKeyStore.normalizeHost(host),
            port = address.port,
        )
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
