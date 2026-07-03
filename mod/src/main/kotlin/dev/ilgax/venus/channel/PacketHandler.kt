package dev.ilgax.venus.channel

import dev.ilgax.venus.protocol.CmdResponsePacket
import dev.ilgax.venus.protocol.ConsoleLogPacket
import dev.ilgax.venus.protocol.ErrorPacket
import dev.ilgax.venus.protocol.FileActionResultPacket
import dev.ilgax.venus.protocol.FileListPacket
import dev.ilgax.venus.protocol.FileRootsPacket
import dev.ilgax.venus.protocol.FileTransferReadyPacket
import dev.ilgax.venus.protocol.LogSanitizer
import dev.ilgax.venus.protocol.PlayerActionResultPacket
import dev.ilgax.venus.protocol.PlayerDetailPacket
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.ReadyPacket
import dev.ilgax.venus.protocol.StatSubscribePacket
import dev.ilgax.venus.protocol.StatsPacket
import dev.ilgax.venus.state.SessionState
import dev.ilgax.venus.state.SessionState.HandshakeState
import dev.ilgax.venus.transfer.ClientFileTransferManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PacketHandler(
    private val json: Json,
    private val sendCommand: (String) -> Unit,
    private val log: (String) -> Unit = ::println,
    private val showAuthSuccess: () -> Unit = {},
    private val showAuthFailure: (String) -> Unit = {},
) {
    private var fileTransfers: ClientFileTransferManager? = null

    fun attachFileTransfers(manager: ClientFileTransferManager): PacketHandler = apply { fileTransfers = manager }

    fun handleReady(data: String) {
        val packet =
            try {
                json.decodeFromString(ReadyPacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid ready packet - ${e.message}")
                return
            }
        if (packet.type != "ready") {
            log("Venus: unexpected ready packet type: ${packet.type}")
            return
        }
        if (SessionState.handshakeState != HandshakeState.EXPECTING_READY) {
            log("Venus: unexpected ready packet - not expecting handshake completion")
            return
        }

        SessionState.markActive()
        showAuthSuccess()
        log("Venus: session ready")
        val subscription =
            json.encodeToString(
                StatSubscribePacket.serializer(),
                StatSubscribePacket(
                    type = "stat_subscribe",
                    intervalSeconds = 1,
                    stats = listOf("tps", "ram", "mspt", "uptime", "players", "server", "cpu"),
                ),
            )
        sendCommand(subscription)
    }

    fun handleData(data: String) {
        if (SessionState.handshakeState != HandshakeState.ACTIVE) {
            log("Venus: ignored data packet before active session")
            return
        }
        val type =
            try {
                json
                    .parseToJsonElement(data)
                    .jsonObject["type"]
                    ?.jsonPrimitive
                    ?.content
            } catch (e: Exception) {
                log("Venus: invalid data packet - ${e.message}")
                return
            }
        when (type) {
            "stats" -> handleStats(data)
            "console_log" -> handleConsoleLog(data)
            "cmd_response" -> handleCommandResponse(data)
            "player_action_result" -> handlePlayerActionResult(data)
            "player_list" -> handlePlayerList(data)
            "player_detail" -> handlePlayerDetail(data)
            "file_roots" -> handleFileRoots(data)
            "file_list" -> handleFileList(data)
            "file_action_result" -> handleFileActionResult(data)
            "file_transfer_ready" -> handleFileTransferReady(data)
            else -> log("Venus: unexpected data packet type: $type")
        }
    }

    fun handleError(data: String) {
        val packet =
            try {
                json.decodeFromString(ErrorPacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid error packet - ${e.message}")
                return
            }
        if (packet.type != "error") {
            log("Venus: unexpected error packet type: ${packet.type}")
            return
        }
        val sanitizedReason = LogSanitizer.sanitize(packet.reason)
        SessionState.markIdle()
        showAuthFailure(authFailureMessage(sanitizedReason))
        log("Venus: auth failed - $sanitizedReason")
    }

    private fun handleStats(data: String) {
        val packet =
            try {
                json.decodeFromString(StatsPacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid stats packet - ${e.message}")
                return
            }
        SessionState.updateStats(packet)
    }

    private fun handleCommandResponse(data: String) {
        val packet =
            try {
                json.decodeFromString(CmdResponsePacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid cmd_response packet - ${e.message}")
                return
            }
        SessionState.addCommandResponse(packet)
    }

    private fun handleConsoleLog(data: String) {
        val packet =
            try {
                json.decodeFromString(ConsoleLogPacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid console_log packet - ${e.message}")
                return
            }
        SessionState.addConsoleLines(packet.lines)
    }

    private fun handlePlayerList(data: String) {
        val packet =
            try {
                json.decodeFromString(PlayerListPacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid player_list packet - ${e.message}")
                return
            }
        SessionState.updatePlayerList(packet)
    }

    private fun handlePlayerDetail(data: String) {
        val packet =
            try {
                json.decodeFromString(PlayerDetailPacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid player_detail packet - ${e.message}")
                return
            }
        SessionState.updatePlayerDetail(packet.player)
    }

    private fun handlePlayerActionResult(data: String) {
        val packet =
            try {
                json.decodeFromString(PlayerActionResultPacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid player_action_result packet - ${e.message}")
                return
            }
        SessionState.updatePlayerActionResult(packet)
    }

    private fun handleFileRoots(data: String) {
        runCatching { json.decodeFromString(FileRootsPacket.serializer(), data) }
            .onSuccess(SessionState::updateFileRoots)
            .onFailure { log("Venus: invalid file_roots packet - ${it.message}") }
    }

    private fun handleFileList(data: String) {
        runCatching { json.decodeFromString(FileListPacket.serializer(), data) }
            .onSuccess(SessionState::updateFileList)
            .onFailure { log("Venus: invalid file_list packet - ${it.message}") }
    }

    private fun handleFileActionResult(data: String) {
        runCatching { json.decodeFromString(FileActionResultPacket.serializer(), data) }
            .onSuccess {
                fileTransfers?.discardPending(it.requestId)
                SessionState.updateFileActionResult(it)
            }.onFailure { log("Venus: invalid file_action_result packet - ${it.message}") }
    }

    private fun handleFileTransferReady(data: String) {
        runCatching { json.decodeFromString(FileTransferReadyPacket.serializer(), data) }
            .onSuccess { fileTransfers?.handleReady(it) }
            .onFailure { log("Venus: invalid file_transfer_ready packet - ${it.message}") }
    }

    private fun authFailureMessage(reason: String): String =
        when (reason) {
            "auth_denied" -> "Server denied access."
            "auth_timeout" -> "Server approval timed out."
            "auth_max_users" -> "Server reached max users."
            "auth_invalid_response" -> "Server rejected auth response."
            else -> reason.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
}
