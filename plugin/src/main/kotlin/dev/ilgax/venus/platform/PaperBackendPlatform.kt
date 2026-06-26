package dev.ilgax.venus.platform

import dev.ilgax.venus.backend.BackendConfig
import dev.ilgax.venus.backend.BackendLogger
import dev.ilgax.venus.backend.BackendPlatform
import dev.ilgax.venus.backend.BackendPlayer
import dev.ilgax.venus.backend.BackendPlayers
import dev.ilgax.venus.backend.BackendScheduler
import dev.ilgax.venus.backend.BackendTask
import dev.ilgax.venus.config.VenusConfig
import dev.ilgax.venus.protocol.MAX_PLAYERS_PER_LIST
import dev.ilgax.venus.protocol.PlayerActionPacket
import dev.ilgax.venus.protocol.PlayerActionResultPacket
import dev.ilgax.venus.protocol.PlayerDetail
import dev.ilgax.venus.protocol.PlayerDetailPacket
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import dev.ilgax.venus.stats.StatsCollector
import io.papermc.paper.ban.BanListType
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.GameMode
import org.bukkit.OfflinePlayer
import org.bukkit.attribute.Attribute
import org.bukkit.ban.ProfileBanList
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.Date
import java.util.UUID

class PaperBackendPlatform(
    private val plugin: JavaPlugin,
    private val sendKeyPacket: (Player, String) -> Unit = { _, _ -> },
    private val sendAuthPacket: (Player, String) -> Unit = { _, _ -> },
    private val sendReadyPacket: (Player, String) -> Unit = { _, _ -> },
    private val sendErrorPacket: (Player, String) -> Unit = { _, _ -> },
    private val sendDataPacket: (Player, String) -> Unit = { _, _ -> },
) : BackendPlatform {
    override val logger: BackendLogger =
        object : BackendLogger {
            override fun info(message: String) {
                plugin.logger.info(message)
            }

            override fun warning(message: String) {
                plugin.logger.warning(message)
            }
        }

    override val scheduler: BackendScheduler =
        object : BackendScheduler {
            override fun runLater(
                delayTicks: Long,
                task: () -> Unit,
            ): BackendTask {
                val bukkitTask = plugin.server.scheduler.runTaskLater(plugin, Runnable(task), delayTicks)
                return BackendTask { bukkitTask.cancel() }
            }

            override fun runRepeating(
                delayTicks: Long,
                periodTicks: Long,
                task: () -> Unit,
            ): BackendTask {
                val bukkitTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable(task), delayTicks, periodTicks)
                return BackendTask { bukkitTask.cancel() }
            }
        }

    override val config: BackendConfig
        get() =
            BackendConfig(
                maxUsers = VenusConfig.maxUsers,
                authTimeoutSeconds = VenusConfig.authTimeoutSeconds,
            )

    private val players = PaperBackendPlayers(plugin)

    override fun player(uuid: UUID): BackendPlayer? = plugin.server.getPlayer(uuid)?.toBackendPlayer()

    override fun sendKey(
        player: BackendPlayer,
        data: String,
    ) {
        plugin.server.getPlayer(player.uuid)?.let { sendKeyPacket(it, data) }
    }

    override fun sendAuth(
        player: BackendPlayer,
        data: String,
    ) {
        plugin.server.getPlayer(player.uuid)?.let { sendAuthPacket(it, data) }
    }

    override fun sendReady(
        player: BackendPlayer,
        data: String,
    ) {
        plugin.server.getPlayer(player.uuid)?.let { sendReadyPacket(it, data) }
    }

    override fun sendError(
        player: BackendPlayer,
        data: String,
    ) {
        plugin.server.getPlayer(player.uuid)?.let { sendErrorPacket(it, data) }
    }

    override fun sendData(
        player: BackendPlayer,
        data: String,
    ) {
        plugin.server.getPlayer(player.uuid)?.let { sendDataPacket(it, data) }
    }

    override fun executeCommand(
        player: BackendPlayer,
        command: String,
        output: (String) -> Unit,
    ): Boolean {
        val sender =
            plugin.server.createCommandSender { component ->
                output(PlainTextComponentSerializer.plainText().serialize(component))
            }
        return plugin.server.dispatchCommand(sender, command)
    }

    override fun buildStatsJson(requestedStats: List<String>): String = StatsCollector.buildStatsJson(plugin.server, requestedStats)

    override fun players(): BackendPlayers = players
}

fun Player.toBackendPlayer(): BackendPlayer = BackendPlayer(uniqueId, name)

private class PaperBackendPlayers(
    private val plugin: JavaPlugin,
) : BackendPlayers {
    override fun list(viewer: BackendPlayer): PlayerListPacket {
        val bannedUuids =
            plugin.server.bannedPlayers
                .map { it.uniqueId }
                .toSet()
        val onlinePlayers =
            plugin.server.onlinePlayers
                .mapNotNull { onlinePlayer -> onlinePlayer.toSummary(blocked = onlinePlayer.uniqueId in bannedUuids) }
                .sortedWith(playerSummaryComparator())
        val whitelistedPlayers =
            plugin.server.whitelistedPlayers
                .mapNotNull { offlinePlayer -> offlinePlayer.toSummary(blocked = offlinePlayer.uniqueId in bannedUuids) }
                .sortedWith(playerSummaryComparator())
        val blockedPlayers =
            plugin.server.bannedPlayers
                .mapNotNull { offlinePlayer -> offlinePlayer.toSummary(blocked = true) }
                .sortedWith(playerSummaryComparator())

        return PlayerListPacket(
            type = "player_list",
            onlineCount = plugin.server.onlinePlayers.size,
            maxPlayers = plugin.server.maxPlayers,
            onlinePlayers = onlinePlayers.take(MAX_PLAYERS_PER_LIST),
            whitelistedPlayers = whitelistedPlayers.take(MAX_PLAYERS_PER_LIST),
            blockedPlayers = blockedPlayers.take(MAX_PLAYERS_PER_LIST),
        )
    }

    override fun detail(
        viewer: BackendPlayer,
        uuid: UUID,
    ): PlayerDetailPacket? {
        val target = resolvePlayer(uuid) ?: return null
        return PlayerDetailPacket(
            type = "player_detail",
            player = target.toDetail(blocked = isBlocked(target)),
        )
    }

    override fun applyAction(
        viewer: BackendPlayer,
        packet: PlayerActionPacket,
    ): PlayerActionResultPacket {
        val uuid =
            try {
                UUID.fromString(packet.uuid)
            } catch (_: IllegalArgumentException) {
                return packet.toResult(false, "Invalid player uuid.")
            }
        val target = resolvePlayer(uuid) ?: return packet.toResult(false, "Player not found.")
        val viewerPlayer = plugin.server.getPlayer(viewer.uuid) ?: return packet.toResult(false, "Player not found.")
        val result = executeAction(viewerPlayer, target, packet)
        return packet.toResult(result.success, result.message)
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
                    if (plugin.server.dispatchCommand(plugin.server.consoleSender, "kill ${onlineTarget.name}")) {
                        ActionResult.success("Player killed.")
                    } else {
                        ActionResult.failure("Kill command failed.")
                    }
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
            ?: tryGetOfflinePlayer(uuid)

    private fun tryGetOfflinePlayer(uuid: UUID): OfflinePlayer? =
        try {
            plugin.server.getOfflinePlayer(uuid)
        } catch (_: Exception) {
            null
        }

    private fun PlayerActionPacket.toResult(
        success: Boolean,
        message: String,
    ): PlayerActionResultPacket =
        PlayerActionResultPacket(
            type = "player_action_result",
            requestId = requestId,
            uuid = uuid,
            action = action,
            success = success,
            message = message,
        )

    private fun OfflinePlayer.toSummary(blocked: Boolean): PlayerSummaryPacket? {
        val playerName = playerName() ?: return null
        return PlayerSummaryPacket(
            uuid = uniqueId.toString(),
            name = playerName,
            displayName = playerName,
            online = isOnline,
            operator = isOp,
            whitelisted = isWhitelisted,
            blocked = blocked,
        )
    }

    private fun OfflinePlayer.toDetail(blocked: Boolean): PlayerDetail {
        val playerName = playerName() ?: uniqueId.toString()
        val onlinePlayer = player
        return PlayerDetail(
            uuid = uniqueId.toString(),
            name = playerName,
            displayName = playerName,
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
            fun success(message: String) = ActionResult(true, message)

            fun failure(message: String) = ActionResult(false, message)
        }
    }
}
