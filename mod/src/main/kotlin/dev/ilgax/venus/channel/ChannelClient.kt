package dev.ilgax.venus.channel

import dev.ilgax.venus.auth.Handshake
import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.ServerKeyStore
import dev.ilgax.venus.network.AuthResponsePayload
import dev.ilgax.venus.network.ClientKeyPayload
import dev.ilgax.venus.network.CmdPayload
import dev.ilgax.venus.network.ErrorPayload
import dev.ilgax.venus.network.HelloPayload
import dev.ilgax.venus.network.TransferPayload
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
import dev.ilgax.venus.protocol.FileActionPacket
import dev.ilgax.venus.protocol.FileDownloadStartPacket
import dev.ilgax.venus.protocol.FileListGetPacket
import dev.ilgax.venus.protocol.FileRootsGetPacket
import dev.ilgax.venus.protocol.FileUploadStartPacket
import dev.ilgax.venus.protocol.PlayerActionPacket
import dev.ilgax.venus.protocol.PlayerDetailGetPacket
import dev.ilgax.venus.protocol.PlayerListGetPacket
import dev.ilgax.venus.protocol.ServerKeyPacket
import dev.ilgax.venus.state.SessionState
import dev.ilgax.venus.transfer.ClientFileTransferManager
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
    val fileTransfers = ClientFileTransferManager(json, ::sendTransfer, log)

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
        ClientPlayNetworking.registerGlobalReceiver(TransferPayload.TYPE) { payload, _ ->
            fileTransfers.handle(payload.data)
        }
    }

    fun sendHello() {
        ClientPlayNetworking.send(HelloPayload)
    }

    fun canSendHello(): Boolean = ClientPlayNetworking.canSend(HelloPayload.TYPE)

    fun sendCommand(data: String) {
        ClientPlayNetworking.send(CmdPayload(data))
    }

    private fun sendTransfer(data: String) {
        ClientPlayNetworking.send(TransferPayload(data))
    }

    fun requestFileRoots(): String {
        val requestId = UUID.randomUUID().toString()
        sendCommand(json.encodeToString(FileRootsGetPacket.serializer(), FileRootsGetPacket("file_roots_get", requestId)))
        return requestId
    }

    fun requestFileList(
        rootId: String,
        path: String,
        offset: Int = 0,
    ): String {
        val requestId = UUID.randomUUID().toString()
        sendCommand(
            json.encodeToString(
                FileListGetPacket.serializer(),
                FileListGetPacket("file_list_get", requestId, rootId, path, offset),
            ),
        )
        return requestId
    }

    fun sendFileAction(
        rootId: String,
        action: String,
        path: String,
        destination: String? = null,
        overwrite: Boolean = false,
    ): String {
        val requestId = UUID.randomUUID().toString()
        sendCommand(
            json.encodeToString(
                FileActionPacket.serializer(),
                FileActionPacket("file_action", requestId, rootId, action, path, destination, overwrite),
            ),
        )
        return requestId
    }

    fun uploadFile(
        localSource: String,
        rootId: String,
        destination: String,
        overwrite: Boolean = false,
        expectedSha256: String? = null,
    ): String {
        val requestId = UUID.randomUUID().toString()
        val size = fileTransfers.prepareUpload(requestId, localSource, destination)
        sendCommand(
            json.encodeToString(
                FileUploadStartPacket.serializer(),
                FileUploadStartPacket("file_upload_start", requestId, rootId, destination, size, overwrite, expectedSha256),
            ),
        )
        return requestId
    }

    fun downloadFile(
        rootId: String,
        source: String,
        localDestination: String,
        overwrite: Boolean = false,
    ): String {
        val requestId = UUID.randomUUID().toString()
        fileTransfers.prepareDownload(requestId, localDestination, source, overwrite)
        sendCommand(
            json.encodeToString(
                FileDownloadStartPacket.serializer(),
                FileDownloadStartPacket("file_download_start", requestId, rootId, source),
            ),
        )
        return requestId
    }

    fun openFileEditor(
        rootId: String,
        source: String,
    ): String {
        val requestId = UUID.randomUUID().toString()
        fileTransfers.prepareEditorDownload(requestId, rootId, source)
        sendCommand(
            json.encodeToString(
                FileDownloadStartPacket.serializer(),
                FileDownloadStartPacket("file_download_start", requestId, rootId, source),
            ),
        )
        return requestId
    }

    fun saveEditedFile(
        rootId: String,
        destination: String,
        content: String,
        expectedSha256: String,
        force: Boolean = false,
    ): String {
        val requestId = UUID.randomUUID().toString()
        val size = fileTransfers.prepareEditorUpload(requestId, content, destination)
        sendCommand(
            json.encodeToString(
                FileUploadStartPacket.serializer(),
                FileUploadStartPacket(
                    "file_upload_start",
                    requestId,
                    rootId,
                    destination,
                    size,
                    overwrite = true,
                    expectedSha256 = if (force) null else expectedSha256,
                ),
            ),
        )
        return requestId
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

    fun sendStatSubscribe() {
        val data =
            json.encodeToString(
                dev.ilgax.venus.protocol.StatSubscribePacket
                    .serializer(),
                dev.ilgax.venus.protocol.StatSubscribePacket(
                    type = "stat_subscribe",
                    intervalSeconds = 1,
                    stats = listOf("tps", "ram", "mspt", "uptime", "players", "server", "cpu"),
                ),
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
            try {
                ServerKeyStore.storeKey(identity, serverKeyBase64)
            } catch (e: Exception) {
                failAuth("Invalid server key.", "Venus: invalid server key from $identity - ${e.message}")
                return
            }
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
        if (!Handshake.verifyTranscript(
                serverPublicKey,
                keyManager.publicKey,
                challenge,
                Handshake.ROLE_SERVER,
                serverSig,
                serverPublicKey,
            )
        ) {
            failAuth("Server signature verification failed.", "Venus: WARNING - server signature verification failed")
            sendError("mitm_sig_fail")
            return
        }

        val clientSig =
            Handshake.signTranscript(
                serverPublicKey,
                keyManager.publicKey,
                challenge,
                Handshake.ROLE_CLIENT,
                keyManager.privateKey,
            )
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
        SessionState.markExpectingReady()
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
        SessionState.markIdle()
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
