package dev.ilgax.venus.handlers

import dev.ilgax.venus.protocol.PlayerDetail
import dev.ilgax.venus.protocol.PlayerDetailGetPacket
import dev.ilgax.venus.protocol.PlayerDetailPacket
import dev.ilgax.venus.protocol.PlayerListGetPacket
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class PlayersHandler(
    private val plugin: JavaPlugin,
    private val json: Json,
    private val sendData: (Player, String) -> Unit,
) {
    fun handleListGet(
        player: Player,
        data: String,
    ) {
        try {
            json.decodeFromString<PlayerListGetPacket>(data)
        } catch (e: SerializationException) {
            plugin.logger.warning("${player.name} sent malformed player_list_get packet: ${e.message}")
            return
        }

        val onlinePlayers =
            plugin.server.onlinePlayers
                .mapNotNull { onlinePlayer -> onlinePlayer.toSummary(blocked = false) }
                .sortedWith(playerSummaryComparator())
        val whitelistedPlayers =
            plugin.server.whitelistedPlayers
                .mapNotNull { offlinePlayer -> offlinePlayer.toSummary(blocked = isBlocked(offlinePlayer)) }
                .sortedWith(playerSummaryComparator())
        val blockedPlayers =
            plugin.server.bannedPlayers
                .mapNotNull { offlinePlayer -> offlinePlayer.toSummary(blocked = true) }
                .sortedWith(playerSummaryComparator())

        val response =
            PlayerListPacket(
                type = "player_list",
                onlineCount = plugin.server.onlinePlayers.size,
                maxPlayers = plugin.server.maxPlayers,
                onlinePlayers = onlinePlayers,
                whitelistedPlayers = whitelistedPlayers,
                blockedPlayers = blockedPlayers,
            )
        sendData(player, json.encodeToString(PlayerListPacket.serializer(), response))
    }

    fun handleDetailGet(
        player: Player,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<PlayerDetailGetPacket>(data)
            } catch (e: SerializationException) {
                plugin.logger.warning("${player.name} sent malformed player_detail_get packet: ${e.message}")
                return
            }
        val uuid =
            try {
                UUID.fromString(packet.uuid)
            } catch (_: IllegalArgumentException) {
                plugin.logger.warning("${player.name} requested player detail with invalid uuid: ${packet.uuid}")
                return
            }
        val target = resolvePlayer(uuid)
        if (target == null) {
            plugin.logger.warning("${player.name} requested unknown player detail for $uuid")
            return
        }

        val response =
            PlayerDetailPacket(
                type = "player_detail",
                player = target.toDetail(blocked = isBlocked(target)),
            )
        sendData(player, json.encodeToString(PlayerDetailPacket.serializer(), response))
    }

    private fun resolvePlayer(uuid: UUID): OfflinePlayer? =
        plugin.server.getPlayer(uuid)
            ?: plugin.server.whitelistedPlayers.firstOrNull { it.uniqueId == uuid }
            ?: plugin.server.bannedPlayers.firstOrNull { it.uniqueId == uuid }
            ?: plugin.server.offlinePlayers.firstOrNull { it.uniqueId == uuid }

    private fun OfflinePlayer.toSummary(blocked: Boolean): PlayerSummaryPacket? {
        val name = playerName() ?: return null
        return PlayerSummaryPacket(
            uuid = uniqueId.toString(),
            name = name,
            displayName = name,
            online = isOnline,
            operator = isOp,
            whitelisted = isWhitelisted,
            blocked = blocked,
        )
    }

    private fun OfflinePlayer.toDetail(blocked: Boolean): PlayerDetail {
        val name = playerName() ?: uniqueId.toString()
        val onlinePlayer = player
        return PlayerDetail(
            uuid = uniqueId.toString(),
            name = name,
            displayName = name,
            online = isOnline,
            operator = isOp,
            whitelisted = isWhitelisted,
            blocked = blocked,
            gameMode = onlinePlayer?.gameMode?.name?.lowercase(),
            health = onlinePlayer?.health,
            maxHealth = onlinePlayer?.maxHealth,
            foodLevel = onlinePlayer?.foodLevel,
            level = onlinePlayer?.level,
            experienceProgress = onlinePlayer?.exp,
            world = onlinePlayer?.world?.key?.toString(),
            x = onlinePlayer?.location?.x,
            y = onlinePlayer?.location?.y,
            z = onlinePlayer?.location?.z,
        )
    }

    private fun OfflinePlayer.playerName(): String? = name ?: player?.name

    private fun isBlocked(player: OfflinePlayer): Boolean = plugin.server.bannedPlayers.any { it.uniqueId == player.uniqueId }

    private fun playerSummaryComparator(): Comparator<PlayerSummaryPacket> =
        compareBy<PlayerSummaryPacket> { it.name.lowercase() }
            .thenBy { it.uuid }
}
