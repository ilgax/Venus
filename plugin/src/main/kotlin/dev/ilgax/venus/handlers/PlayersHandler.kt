package dev.ilgax.venus.handlers

import dev.ilgax.venus.protocol.PlayerActionPacket
import dev.ilgax.venus.protocol.PlayerActionResultPacket
import dev.ilgax.venus.protocol.PlayerDetail
import dev.ilgax.venus.protocol.PlayerDetailGetPacket
import dev.ilgax.venus.protocol.PlayerDetailPacket
import dev.ilgax.venus.protocol.PlayerListGetPacket
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import io.papermc.paper.ban.BanListType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import net.kyori.adventure.text.Component
import org.bukkit.GameMode
import org.bukkit.OfflinePlayer
import org.bukkit.attribute.Attribute
import org.bukkit.ban.ProfileBanList
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.Date
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

        sendListSnapshot(player)
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

        sendDetailSnapshot(player, target)
    }

    fun handleAction(
        player: Player,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<PlayerActionPacket>(data)
            } catch (e: SerializationException) {
                plugin.logger.warning("${player.name} sent malformed player_action packet: ${e.message}")
                return
            }
        val uuid =
            try {
                UUID.fromString(packet.uuid)
            } catch (_: IllegalArgumentException) {
                sendActionResult(player, packet, success = false, message = "Invalid player UUID.")
                return
            }
        val target = resolvePlayer(uuid)
        if (target == null) {
            sendActionResult(player, packet, success = false, message = "Unknown player.")
            return
        }

        val result = executeAction(player, target, packet)
        sendActionResult(player, packet, result.success, result.message)
        if (result.success) {
            sendDetailSnapshot(player, target)
            sendListSnapshot(player)
        }
    }

    private fun sendListSnapshot(player: Player) {
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

    private fun sendDetailSnapshot(
        player: Player,
        target: OfflinePlayer,
    ) {
        val response =
            PlayerDetailPacket(
                type = "player_detail",
                player = target.toDetail(blocked = isBlocked(target)),
            )
        sendData(player, json.encodeToString(PlayerDetailPacket.serializer(), response))
    }

    private fun sendActionResult(
        player: Player,
        packet: PlayerActionPacket,
        success: Boolean,
        message: String,
    ) {
        val response =
            PlayerActionResultPacket(
                type = "player_action_result",
                requestId = packet.requestId,
                uuid = packet.uuid,
                action = packet.action,
                success = success,
                message = message,
            )
        sendData(player, json.encodeToString(PlayerActionResultPacket.serializer(), response))
    }

    private fun executeAction(
        player: Player,
        target: OfflinePlayer,
        packet: PlayerActionPacket,
    ): ActionResult =
        when (packet.action) {
            "kick" ->
                withOnlineTarget(target) { onlineTarget ->
                    onlineTarget.kick(Component.text("Kicked by Venus."))
                    ActionResult.success("Player kicked.")
                }
            "kill" ->
                withOnlineTarget(target) { onlineTarget ->
                    onlineTarget.health = 0.0
                    ActionResult.success("Player killed.")
                }
            "heal" ->
                withOnlineTarget(target) { onlineTarget ->
                    onlineTarget.health = onlineTarget.maxHealthValue()
                    ActionResult.success("Player healed.")
                }
            "feed" ->
                withOnlineTarget(target) { onlineTarget ->
                    onlineTarget.foodLevel = 20
                    ActionResult.success("Player fed.")
                }
            "set_whitelisted" -> {
                val value = packet.booleanValue() ?: return ActionResult.failure("Invalid whitelist value.")
                target.isWhitelisted = value
                ActionResult.success(if (value) "Player whitelisted." else "Player removed from whitelist.")
            }
            "set_blocked" -> {
                val value = packet.booleanValue() ?: return ActionResult.failure("Invalid blocked value.")
                if (value) {
                    profileBanList().addBan(target.playerProfile, "Banned by Venus.", null as Date?, "Venus")
                    target.player?.kick(Component.text("Banned by Venus."))
                    ActionResult.success("Player banned.")
                } else {
                    pardon(target)
                    ActionResult.success("Player unbanned.")
                }
            }
            "set_operator" -> {
                val value = packet.booleanValue() ?: return ActionResult.failure("Invalid operator value.")
                target.isOp = value
                ActionResult.success(if (value) "Player made operator." else "Player removed from operators.")
            }
            "set_game_mode" -> {
                val gameMode = packet.gameModeValue() ?: return ActionResult.failure("Invalid game mode.")
                withOnlineTarget(target) { onlineTarget ->
                    onlineTarget.gameMode = gameMode
                    ActionResult.success("Game mode set to ${gameMode.name.lowercase()}.")
                }
            }
            "teleport_admin_to_player" ->
                withOnlineTarget(target) { onlineTarget ->
                    if (player.teleport(onlineTarget.location)) {
                        ActionResult.success("Teleported to player.")
                    } else {
                        ActionResult.failure("Teleport failed.")
                    }
                }
            "teleport_player_to_admin" ->
                withOnlineTarget(target) { onlineTarget ->
                    if (onlineTarget.teleport(player.location)) {
                        ActionResult.success("Teleported player to you.")
                    } else {
                        ActionResult.failure("Teleport failed.")
                    }
                }
            else -> ActionResult.failure("Unknown player action.")
        }

    private fun withOnlineTarget(
        target: OfflinePlayer,
        action: (Player) -> ActionResult,
    ): ActionResult {
        val onlineTarget = target.player ?: return ActionResult.failure("Player must be online.")
        return action(onlineTarget)
    }

    private fun PlayerActionPacket.booleanValue(): Boolean? = value?.jsonPrimitive?.booleanOrNull

    private fun PlayerActionPacket.gameModeValue(): GameMode? =
        when (value?.jsonPrimitive?.contentOrNull?.lowercase()) {
            "survival" -> GameMode.SURVIVAL
            "creative" -> GameMode.CREATIVE
            "adventure" -> GameMode.ADVENTURE
            "spectator" -> GameMode.SPECTATOR
            else -> null
        }

    private fun profileBanList(): ProfileBanList = plugin.server.getBanList(BanListType.PROFILE)

    private fun pardon(target: OfflinePlayer) {
        profileBanList().pardon(target.playerProfile)
    }

    private fun resolvePlayer(uuid: UUID): OfflinePlayer? =
        plugin.server.getPlayer(uuid)
            ?: plugin.server.whitelistedPlayers.firstOrNull { it.uniqueId == uuid }
            ?: plugin.server.bannedPlayers.firstOrNull { it.uniqueId == uuid }
            ?: plugin.server.offlinePlayers.firstOrNull { it.uniqueId == uuid }
            ?: plugin.server.getOfflinePlayer(uuid)

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
            maxHealth = onlinePlayer?.maxHealthValue(),
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

    private fun Player.maxHealthValue(): Double =
        try {
            getAttribute(Attribute.MAX_HEALTH)?.value ?: deprecatedMaxHealthValue()
        } catch (_: LinkageError) {
            deprecatedMaxHealthValue()
        } catch (_: IllegalArgumentException) {
            deprecatedMaxHealthValue()
        }

    @Suppress("DEPRECATION")
    private fun Player.deprecatedMaxHealthValue(): Double = maxHealth

    private fun isBlocked(player: OfflinePlayer): Boolean = plugin.server.bannedPlayers.any { it.uniqueId == player.uniqueId }

    private fun playerSummaryComparator(): Comparator<PlayerSummaryPacket> =
        compareBy<PlayerSummaryPacket> { it.name.lowercase() }
            .thenBy { it.uuid }

    private data class ActionResult(
        val success: Boolean,
        val message: String,
    ) {
        companion object {
            fun success(message: String): ActionResult = ActionResult(success = true, message = message)

            fun failure(message: String): ActionResult = ActionResult(success = false, message = message)
        }
    }
}
