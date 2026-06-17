package dev.ilgax.venus.channel

import dev.ilgax.venus.auth.Handshake
import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.ServerKeyStore
import dev.ilgax.venus.network.AuthResponsePayload
import dev.ilgax.venus.network.ClientKeyPayload
import dev.ilgax.venus.network.CmdPayload
import dev.ilgax.venus.network.ErrorPayload
import dev.ilgax.venus.network.HelloPayload
import dev.ilgax.venus.network.VenusPayloads
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
import dev.ilgax.venus.protocol.PlayerActionPacket
import dev.ilgax.venus.protocol.PlayerDetailGetPacket
import dev.ilgax.venus.protocol.PlayerListGetPacket
import dev.ilgax.venus.protocol.ServerKeyPacket
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.resolver.ServerAddress
import java.util.Base64
import java.util.UUID

class ChannelClient(
    private val json: Json,
    private val keyManager: KeyManager,
    private val log: (String) -> Unit,
    private val showAuthFailure: (String) -> Unit = {},
) {
    fun register(packetHandler: PacketHandler) {
        VenusPayloads.registerPayloadTypes()

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

    fun canSendHello(): Boolean = ClientPlayNetworking.canSend(HelloPayload.TYPE)

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

    fun sendPlayerAction(
        uuid: String,
        action: String,
        value: JsonElement? = null,
    ): String {
        val requestId =
            UUID
                .randomUUID()
                .toString()
        val data =
            json.encodeToString(
                PlayerActionPacket.serializer(),
                PlayerActionPacket(
                    type = "player_action",
                    requestId = requestId,
                    uuid = uuid,
                    action = action,
                    value = value,
                ),
            )
        sendCommand(data)
        return requestId
    }

    fun sendPlayerAction(
        uuid: String,
        action: String,
        value: Boolean,
    ): String = sendPlayerAction(uuid, action, JsonPrimitive(value))

    fun sendPlayerAction(
        uuid: String,
        action: String,
        value: String,
    ): String = sendPlayerAction(uuid, action, JsonPrimitive(value))

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
}
