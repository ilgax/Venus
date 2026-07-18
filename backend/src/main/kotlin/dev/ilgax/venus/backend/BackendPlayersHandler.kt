package dev.ilgax.venus.backend

import dev.ilgax.venus.protocol.MAX_PACKET_SIZE
import dev.ilgax.venus.protocol.MAX_PLAYERS_PER_LIST
import dev.ilgax.venus.protocol.PlayerActionPacket
import dev.ilgax.venus.protocol.PlayerDetailGetPacket
import dev.ilgax.venus.protocol.PlayerListGetPacket
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BackendPlayersHandler(
    private val platform: BackendPlatform,
    private val json: Json,
) {
    private val listSnapshots = ConcurrentHashMap<UUID, PlayerListCursorState>()

    fun handleListGet(
        player: BackendPlayer,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<PlayerListGetPacket>(data)
            } catch (e: Exception) {
                platform.logger.warning("${player.name} sent malformed player_list_get packet: ${e.message}")
                return
            }
        val state =
            if (packet.cursor == null) {
                createCursorState(packet.requestId, platform.players().list(player)).also { listSnapshots[player.uuid] = it }
            } else {
                listSnapshots[player.uuid]?.takeIf {
                    it.requestId == packet.requestId && it.nextCursor == packet.cursor
                }
            }
        if (state == null) {
            platform.logger.warning("${player.name} sent stale player list cursor")
            return
        }
        val page = synchronized(state) { buildPage(packet, state) }
        if (page == null) {
            listSnapshots.remove(player.uuid, state)
            platform.logger.warning("Player list entry for ${player.name} exceeded the packet limit")
            return
        }
        if (page.nextCursor == null) listSnapshots.remove(player.uuid, state)
        platform.sendData(player, json.encodeToString(PlayerListPacket.serializer(), page))
    }

    fun handleDetailGet(
        player: BackendPlayer,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<PlayerDetailGetPacket>(data)
            } catch (e: Exception) {
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
            } catch (e: Exception) {
                platform.logger.warning("${player.name} sent malformed player_action packet: ${e.message}")
                return
            }
        try {
            UUID.fromString(packet.uuid)
        } catch (_: IllegalArgumentException) {
            sendActionResult(player, packet, success = false, message = "Invalid player uuid.")
            return
        }
        val result =
            try {
                platform.players().applyAction(player, packet)
            } catch (e: Exception) {
                platform.logger.warning("${player.name} sent invalid player_action value: ${e.message}")
                sendActionResult(player, packet, success = false, message = "Invalid player action value.")
                return
            }
        platform.sendData(player, json.encodeToString(result))
        if (result.success) {
            cleanupPlayer(player.uuid)
            sendDetailSnapshot(player, UUID.fromString(packet.uuid))
        }
    }

    fun cleanupPlayer(uuid: UUID) {
        listSnapshots.remove(uuid)
    }

    private fun createCursorState(
        requestId: String,
        snapshot: BackendPlayerListSnapshot,
    ): PlayerListCursorState =
        PlayerListCursorState(
            requestId = requestId,
            snapshot = snapshot,
            entries =
                snapshot.onlinePlayers.map { CategorizedPlayer(PlayerCategory.ONLINE, it) } +
                    snapshot.whitelistedPlayers.map { CategorizedPlayer(PlayerCategory.WHITELISTED, it) } +
                    snapshot.blockedPlayers.map { CategorizedPlayer(PlayerCategory.BLOCKED, it) },
        )

    private fun buildPage(
        request: PlayerListGetPacket,
        state: PlayerListCursorState,
    ): PlayerListPacket? {
        val online = mutableListOf<PlayerSummaryPacket>()
        val whitelisted = mutableListOf<PlayerSummaryPacket>()
        val blocked = mutableListOf<PlayerSummaryPacket>()
        val start = state.index
        while (state.index < state.entries.size && state.index - start < MAX_PLAYERS_PER_LIST) {
            val entry = state.entries[state.index]
            val destination =
                when (entry.category) {
                    PlayerCategory.ONLINE -> online
                    PlayerCategory.WHITELISTED -> whitelisted
                    PlayerCategory.BLOCKED -> blocked
                }
            destination += entry.player
            val candidate =
                pagePacket(
                    request,
                    state,
                    online,
                    whitelisted,
                    blocked,
                    "00000000-0000-0000-0000-000000000000",
                )
            if (json.encodeToString(PlayerListPacket.serializer(), candidate).toByteArray(Charsets.UTF_8).size > MAX_PACKET_SIZE) {
                destination.removeLast()
                break
            }
            state.index += 1
        }
        if (state.index == start && state.index < state.entries.size) return null
        val nextCursor = if (state.index < state.entries.size) UUID.randomUUID().toString() else null
        state.nextCursor = nextCursor
        return pagePacket(request, state, online, whitelisted, blocked, nextCursor)
    }

    private fun pagePacket(
        request: PlayerListGetPacket,
        state: PlayerListCursorState,
        online: List<PlayerSummaryPacket>,
        whitelisted: List<PlayerSummaryPacket>,
        blocked: List<PlayerSummaryPacket>,
        nextCursor: String?,
    ) = PlayerListPacket(
        type = "player_list",
        requestId = request.requestId,
        onlineCount = state.snapshot.onlineCount,
        maxPlayers = state.snapshot.maxPlayers,
        onlinePlayers = online,
        whitelistedPlayers = whitelisted,
        blockedPlayers = blocked,
        cursor = request.cursor,
        nextCursor = nextCursor,
    )

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

private data class PlayerListCursorState(
    val requestId: String,
    val snapshot: BackendPlayerListSnapshot,
    val entries: List<CategorizedPlayer>,
    var index: Int = 0,
    var nextCursor: String? = null,
)

private data class CategorizedPlayer(
    val category: PlayerCategory,
    val player: PlayerSummaryPacket,
)

private enum class PlayerCategory { ONLINE, WHITELISTED, BLOCKED }
