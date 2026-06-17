package dev.ilgax.venus.platform

import dev.ilgax.venus.backend.BackendConfig
import dev.ilgax.venus.backend.BackendLogger
import dev.ilgax.venus.backend.BackendPlatform
import dev.ilgax.venus.backend.BackendPlayer
import dev.ilgax.venus.backend.BackendPlayers
import dev.ilgax.venus.backend.BackendScheduler
import dev.ilgax.venus.backend.BackendTask
import dev.ilgax.venus.network.ErrorPayload
import dev.ilgax.venus.network.VenusRawAuthPayload
import dev.ilgax.venus.network.VenusRawDataPayload
import dev.ilgax.venus.network.VenusRawPayload
import dev.ilgax.venus.network.VenusRawReadyPayload
import dev.ilgax.venus.protocol.PlayerActionPacket
import dev.ilgax.venus.protocol.PlayerActionResultPacket
import dev.ilgax.venus.protocol.PlayerDetail
import dev.ilgax.venus.protocol.PlayerDetailPacket
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.players.NameAndId
import net.minecraft.server.players.UserBanListEntry
import net.minecraft.server.players.UserWhiteListEntry
import net.minecraft.world.level.GameType
import org.slf4j.Logger
import java.io.IOException
import java.util.Date
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class FabricBackendPlatform(
    private val serverProvider: () -> MinecraftServer?,
    private val loggerDelegate: Logger,
    private val configProvider: () -> BackendConfig,
) : BackendPlatform {
    override val logger: BackendLogger =
        object : BackendLogger {
            override fun info(message: String) {
                loggerDelegate.info(message)
            }

            override fun warning(message: String) {
                loggerDelegate.warn(message)
            }
        }

    private val schedulerImpl = FabricBackendScheduler()
    override val scheduler: BackendScheduler = schedulerImpl
    override val config: BackendConfig
        get() = configProvider()
    private val players = FabricBackendPlayers(serverProvider)
    private val json = Json { explicitNulls = false }

    init {
        ServerTickEvents.END_SERVER_TICK.register { schedulerImpl.tick() }
    }

    override fun player(uuid: UUID): BackendPlayer? = serverProvider()?.playerList?.getPlayer(uuid)?.toBackendPlayer()

    override fun sendKey(
        player: BackendPlayer,
        data: String,
    ) = send(player, VenusRawPayload(data.toByteArray(Charsets.UTF_8)))

    override fun sendAuth(
        player: BackendPlayer,
        data: String,
    ) = send(player, VenusRawAuthPayload(data.toByteArray(Charsets.UTF_8)))

    override fun sendReady(
        player: BackendPlayer,
        data: String,
    ) = send(player, VenusRawReadyPayload(data.toByteArray(Charsets.UTF_8)))

    override fun sendError(
        player: BackendPlayer,
        data: String,
    ) = send(player, ErrorPayload(data))

    override fun sendData(
        player: BackendPlayer,
        data: String,
    ) = send(player, VenusRawDataPayload(data.toByteArray(Charsets.UTF_8)))

    override fun executeCommand(
        player: BackendPlayer,
        command: String,
        output: (String) -> Unit,
    ): Boolean =
        serverProvider()?.let {
            FabricCommandExecutor.execute(it, command, output)
        } ?: false

    override fun buildStatsJson(requestedStats: List<String>): String =
        FabricStatsCollector.buildStatsJson(serverProvider(), requestedStats)

    override fun players(): BackendPlayers = players

    private fun send(
        player: BackendPlayer,
        payload: CustomPacketPayload,
    ) {
        serverProvider()?.playerList?.getPlayer(player.uuid)?.let {
            ServerPlayNetworking.send(it, payload)
        }
    }
}

fun ServerPlayer.toBackendPlayer(): BackendPlayer = BackendPlayer(uuid, name.string)

private class FabricBackendScheduler : BackendScheduler {
    private val tasks = CopyOnWriteArrayList<ScheduledTask>()
    private var tick = 0L

    override fun runLater(
        delayTicks: Long,
        task: () -> Unit,
    ): BackendTask {
        val scheduled = ScheduledTask(nextTick = tick + delayTicks, periodTicks = null, task = task)
        tasks.add(scheduled)
        return scheduled
    }

    override fun runRepeating(
        delayTicks: Long,
        periodTicks: Long,
        task: () -> Unit,
    ): BackendTask {
        val scheduled = ScheduledTask(nextTick = tick + delayTicks, periodTicks = periodTicks, task = task)
        tasks.add(scheduled)
        return scheduled
    }

    fun tick() {
        tick += 1
        tasks.forEach { scheduled ->
            if (scheduled.cancelled || scheduled.nextTick > tick) return@forEach
            scheduled.task()
            if (scheduled.cancelled) {
                tasks.remove(scheduled)
            } else if (scheduled.periodTicks == null) {
                scheduled.cancel()
                tasks.remove(scheduled)
            } else {
                scheduled.nextTick = tick + scheduled.periodTicks
            }
        }
    }

    private data class ScheduledTask(
        var nextTick: Long,
        val periodTicks: Long?,
        val task: () -> Unit,
        var cancelled: Boolean = false,
    ) : BackendTask {
        override fun cancel() {
            cancelled = true
        }
    }
}

private class FabricBackendPlayers(
    private val serverProvider: () -> MinecraftServer?,
) : BackendPlayers {
    override fun list(viewer: BackendPlayer): PlayerListPacket {
        val server = serverProvider()
        val playerList = server?.playerList
        val onlinePlayers =
            playerList
                ?.players
                ?.map { it.toSummary(playerList.isWhiteListed(it.nameAndId()), playerList.isOp(it.nameAndId()), isBlocked(it.nameAndId())) }
                ?.sortedWith(playerSummaryComparator())
                ?: emptyList()
        val whitelistedPlayers =
            playerList
                ?.whiteList
                ?.entries
                ?.mapNotNull { entry ->
                    entry.user?.let { identity ->
                        identity.toSummary(
                            online = false,
                            whitelisted = true,
                            operator = playerList.isOp(identity),
                            blocked = isBlocked(identity),
                        )
                    }
                }?.sortedWith(playerSummaryComparator())
                ?: emptyList()
        val blockedPlayers =
            playerList
                ?.bans
                ?.entries
                ?.mapNotNull { entry ->
                    entry.user?.let { identity ->
                        identity.toSummary(
                            online = false,
                            whitelisted = playerList.isWhiteListed(identity),
                            operator = playerList.isOp(identity),
                            blocked = true,
                        )
                    }
                }?.sortedWith(playerSummaryComparator())
                ?: emptyList()
        return PlayerListPacket(
            type = "player_list",
            onlineCount = onlinePlayers.size,
            maxPlayers = server?.maxPlayers ?: 0,
            onlinePlayers = onlinePlayers,
            whitelistedPlayers = whitelistedPlayers,
            blockedPlayers = blockedPlayers,
        )
    }

    override fun detail(
        viewer: BackendPlayer,
        uuid: UUID,
    ): PlayerDetailPacket? {
        val playerList = serverProvider()?.playerList ?: return null
        val onlineTarget = playerList.getPlayer(uuid)
        val identity =
            onlineTarget?.nameAndId()
                ?: playerList.whiteList.entries
                    .firstOrNull { it.user?.id() == uuid }
                    ?.user
                ?: playerList.bans.entries
                    .firstOrNull { it.user?.id() == uuid }
                    ?.user
                ?: return null
        return PlayerDetailPacket(
            type = "player_detail",
            player =
                PlayerDetail(
                    uuid = identity.id().toString(),
                    name = identity.name(),
                    displayName = identity.name(),
                    online = onlineTarget != null,
                    operator = playerList.isOp(identity),
                    whitelisted = playerList.isWhiteListed(identity),
                    blocked = isBlocked(identity),
                    gameMode =
                        onlineTarget
                            ?.gameMode
                            ?.gameModeForPlayer
                            ?.name
                            ?.lowercase(),
                    health = onlineTarget?.health?.toDouble(),
                    maxHealth = onlineTarget?.maxHealth?.toDouble(),
                    foodLevel = onlineTarget?.foodData?.foodLevel,
                    level = onlineTarget?.experienceLevel,
                    experienceProgress = onlineTarget?.experienceProgress,
                    world =
                        onlineTarget?.let { target ->
                            target
                                .level()
                                .dimension()
                                .identifier()
                                .toString()
                        },
                    x = onlineTarget?.x,
                    y = onlineTarget?.y,
                    z = onlineTarget?.z,
                ),
        )
    }

    override fun applyAction(
        viewer: BackendPlayer,
        packet: PlayerActionPacket,
    ): PlayerActionResultPacket {
        val playerList = serverProvider()?.playerList ?: return packet.toResult(false, "Server unavailable.")
        val uuid =
            try {
                UUID.fromString(packet.uuid)
            } catch (_: IllegalArgumentException) {
                return packet.toResult(false, "Invalid player uuid.")
            }
        val viewerPlayer = playerList.getPlayer(viewer.uuid) ?: return packet.toResult(false, "Player not found.")
        val targetIdentity =
            playerList.getPlayer(uuid)?.nameAndId()
                ?: playerList.whiteList.entries
                    .firstOrNull { it.user?.id() == uuid }
                    ?.user
                ?: playerList.bans.entries
                    .firstOrNull { it.user?.id() == uuid }
                    ?.user
                ?: return packet.toResult(false, "Player not found.")
        val result = executeAction(playerList, viewerPlayer, targetIdentity, packet)
        return packet.toResult(result.success, result.message)
    }

    private fun executeAction(
        playerList: net.minecraft.server.players.PlayerList,
        viewer: ServerPlayer,
        targetIdentity: NameAndId,
        packet: PlayerActionPacket,
    ): ActionResult {
        val targetPlayer = playerList.getPlayer(targetIdentity.id())
        return when (packet.action) {
            "kick" ->
                withOnlineTarget(targetPlayer) { onlineTarget ->
                    onlineTarget.connection.disconnect(Component.literal("Kicked by Venus."))
                    ActionResult.success("Player kicked.")
                }
            "kill" ->
                withOnlineTarget(targetPlayer) { onlineTarget ->
                    executeServerCommand("kill ${onlineTarget.scoreboardName}")
                    ActionResult.success("Player killed.")
                }
            "heal" ->
                withOnlineTarget(targetPlayer) { onlineTarget ->
                    onlineTarget.health = onlineTarget.maxHealth
                    ActionResult.success("Player healed.")
                }
            "feed" ->
                withOnlineTarget(targetPlayer) { onlineTarget ->
                    onlineTarget.foodData.foodLevel = 20
                    ActionResult.success("Player fed.")
                }
            "set_whitelisted" -> {
                val value = packet.booleanValue() ?: return ActionResult.failure("Invalid whitelist value.")
                if (value) {
                    playerList.whiteList.add(UserWhiteListEntry(targetIdentity))
                    persistWhitelist(playerList) ?: return ActionResult.failure("Failed to save whitelist.")
                    ActionResult.success("Player whitelisted.")
                } else {
                    playerList.whiteList.remove(targetIdentity)
                    persistWhitelist(playerList) ?: return ActionResult.failure("Failed to save whitelist.")
                    ActionResult.success("Player removed from whitelist.")
                }
            }
            "set_blocked" -> {
                val value = packet.booleanValue() ?: return ActionResult.failure("Invalid blocked value.")
                if (value) {
                    playerList.bans.add(UserBanListEntry(targetIdentity, Date(), "Venus", null, "Banned by Venus."))
                    persistBans(playerList) ?: return ActionResult.failure("Failed to save ban list.")
                    targetPlayer?.connection?.disconnect(Component.literal("Banned by Venus."))
                    ActionResult.success("Player banned.")
                } else {
                    playerList.bans.remove(targetIdentity)
                    persistBans(playerList) ?: return ActionResult.failure("Failed to save ban list.")
                    ActionResult.success("Player unbanned.")
                }
            }
            "set_operator" -> {
                val value = packet.booleanValue() ?: return ActionResult.failure("Invalid operator value.")
                if (value) {
                    playerList.op(targetIdentity, java.util.Optional.of(LevelBasedPermissionSet.ADMIN), java.util.Optional.of(false))
                    persistOps(playerList) ?: return ActionResult.failure("Failed to save operator list.")
                    ActionResult.success("Player made operator.")
                } else {
                    playerList.deop(targetIdentity)
                    persistOps(playerList) ?: return ActionResult.failure("Failed to save operator list.")
                    ActionResult.success("Player removed from operators.")
                }
            }
            "set_game_mode" -> {
                val gameMode = packet.gameModeValue() ?: return ActionResult.failure("Invalid game mode.")
                withOnlineTarget(targetPlayer) { onlineTarget ->
                    if (onlineTarget.setGameMode(gameMode)) {
                        ActionResult.success("Game mode set to ${gameMode.name.lowercase()}.")
                    } else {
                        ActionResult.failure("Game mode change failed.")
                    }
                }
            }
            "teleport_admin_to_player" ->
                withOnlineTarget(targetPlayer) { onlineTarget ->
                    viewer.teleportTo(
                        onlineTarget.level(),
                        onlineTarget.x,
                        onlineTarget.y,
                        onlineTarget.z,
                        setOf(),
                        onlineTarget.yRot,
                        onlineTarget.xRot,
                        false,
                    )
                    ActionResult.success("Teleported to player.")
                }
            "teleport_player_to_admin" ->
                withOnlineTarget(targetPlayer) { onlineTarget ->
                    onlineTarget.teleportTo(
                        viewer.level(),
                        viewer.x,
                        viewer.y,
                        viewer.z,
                        setOf(),
                        viewer.yRot,
                        viewer.xRot,
                        false,
                    )
                    ActionResult.success("Teleported player to you.")
                }
            else -> ActionResult.failure("Unknown player action.")
        }
    }

    private fun executeServerCommand(command: String) {
        val server = serverProvider() ?: return
        server.commands.performPrefixedCommand(server.createCommandSourceStack(), command)
    }

    private fun persistWhitelist(playerList: net.minecraft.server.players.PlayerList): Unit? =
        try {
            playerList.whiteList.save()
            playerList.reloadWhiteList()
        } catch (_: IOException) {
            null
        }

    private fun persistBans(playerList: net.minecraft.server.players.PlayerList): Unit? =
        try {
            playerList.bans.save()
        } catch (_: IOException) {
            null
        }

    private fun persistOps(playerList: net.minecraft.server.players.PlayerList): Unit? =
        try {
            playerList.ops.save()
        } catch (_: IOException) {
            null
        }

    private fun withOnlineTarget(
        target: ServerPlayer?,
        action: (ServerPlayer) -> ActionResult,
    ): ActionResult {
        val onlineTarget = target ?: return ActionResult.failure("Player must be online.")
        return action(onlineTarget)
    }

    private fun PlayerActionPacket.booleanValue(): Boolean? = value?.jsonPrimitive?.booleanOrNull

    private fun PlayerActionPacket.gameModeValue(): GameType? =
        when (value?.jsonPrimitive?.contentOrNull?.lowercase()) {
            "survival" -> GameType.SURVIVAL
            "creative" -> GameType.CREATIVE
            "adventure" -> GameType.ADVENTURE
            "spectator" -> GameType.SPECTATOR
            else -> null
        }

    private fun NameAndId.toSummary(
        online: Boolean,
        whitelisted: Boolean,
        operator: Boolean,
        blocked: Boolean,
    ): PlayerSummaryPacket? {
        val playerName = name().takeIf { it.isNotBlank() } ?: return null
        return PlayerSummaryPacket(
            uuid = id().toString(),
            name = playerName,
            displayName = playerName,
            online = online,
            operator = operator,
            whitelisted = whitelisted,
            blocked = blocked,
        )
    }

    private fun ServerPlayer.toSummary(
        whitelisted: Boolean,
        operator: Boolean,
        blocked: Boolean,
    ): PlayerSummaryPacket = nameAndId().toSummary(true, whitelisted, operator, blocked)!!

    private fun isBlocked(identity: NameAndId): Boolean = serverProvider()?.playerList?.bans?.isBanned(identity) == true

    private fun playerSummaryComparator(): Comparator<PlayerSummaryPacket> =
        compareBy<PlayerSummaryPacket> { it.name.lowercase() }
            .thenBy { it.uuid }

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
