package dev.ilgax.venus.backend

import dev.ilgax.venus.protocol.PlayerActionPacket
import dev.ilgax.venus.protocol.PlayerDetailGetPacket
import dev.ilgax.venus.protocol.PlayerListGetPacket
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.UUID

class BackendPlayersHandler(
    private val platform: BackendPlatform,
    private val json: Json,
) {
    fun handleListGet(
        player: BackendPlayer,
        data: String,
    ) {
        try {
            json.decodeFromString<PlayerListGetPacket>(data)
        } catch (e: SerializationException) {
            platform.logger.warning("${player.name} sent malformed player_list_get packet: ${e.message}")
            return
        }
        sendListSnapshot(player)
    }

    fun handleDetailGet(
        player: BackendPlayer,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<PlayerDetailGetPacket>(data)
            } catch (e: SerializationException) {
                platform.logger.warning("${player.name} sent malformed player_detail_get packet: ${e.message}")
                return
            }
        val uuid =
            try {
                UUID.fromString(packet.uuid)
            } catch (_: IllegalArgumentException) {
                platform.logger.warning("${player.name} requested invalid player uuid: ${packet.uuid}")
                return
            }
        platform.players().detail(player, uuid)?.let {
            platform.sendData(player, json.encodeToString(it))
        }
    }

    fun handleAction(
        player: BackendPlayer,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<PlayerActionPacket>(data)
            } catch (e: SerializationException) {
                platform.logger.warning("${player.name} sent malformed player_action packet: ${e.message}")
                return
            }
        try {
            UUID.fromString(packet.uuid)
        } catch (_: IllegalArgumentException) {
            sendActionResult(player, packet, success = false, message = "Invalid player uuid.")
            return
        }
        val result = platform.players().applyAction(player, packet)
        platform.sendData(player, json.encodeToString(result))
        if (result.success) {
            sendDetailSnapshot(player, UUID.fromString(packet.uuid))
            sendListSnapshot(player)
        }
    }

    private fun sendListSnapshot(player: BackendPlayer) {
        platform.sendData(player, json.encodeToString(platform.players().list(player)))
    }

    private fun sendDetailSnapshot(
        player: BackendPlayer,
        uuid: UUID,
    ) {
        platform.players().detail(player, uuid)?.let {
            platform.sendData(player, json.encodeToString(it))
        }
    }

    private fun sendActionResult(
        player: BackendPlayer,
        packet: PlayerActionPacket,
        success: Boolean,
        message: String,
    ) {
        val response =
            dev.ilgax.venus.protocol.PlayerActionResultPacket(
                type = "player_action_result",
                requestId = packet.requestId,
                uuid = packet.uuid,
                action = packet.action,
                success = success,
                message = message,
            )
        platform.sendData(player, json.encodeToString(response))
    }
}
