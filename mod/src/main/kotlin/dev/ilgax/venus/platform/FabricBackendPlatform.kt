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
import dev.ilgax.venus.protocol.PlayerDetailPacket
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import dev.ilgax.venus.protocol.StatsPacket
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.Logger
import java.lang.management.ManagementFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class FabricBackendPlatform(
    private val serverProvider: () -> MinecraftServer?,
    private val loggerDelegate: Logger,
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
    override val config: BackendConfig = BackendConfig()
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
    ): Boolean {
        val server = serverProvider() ?: return false
        val source = server.createCommandSourceStack()
        server.commands.performPrefixedCommand(source, command)
        return true
    }

    override fun buildStatsJson(requestedStats: List<String>): String {
        val server = serverProvider()
        val packet =
            StatsPacket(
                type = "stats",
                tps = if ("tps" in requestedStats) 20.0 else null,
                mspt = if ("mspt" in requestedStats) null else null,
                cpuLoad = if ("cpu" in requestedStats) cpuLoad() else null,
                ramUsed = if ("ram" in requestedStats) ramUsed() else null,
                ramMax = if ("ram" in requestedStats) ramMax() else null,
                uptime = if ("uptime" in requestedStats) uptime() else null,
                onlinePlayers = if ("players" in requestedStats) server?.playerCount else null,
                maxPlayers = if ("players" in requestedStats) server?.maxPlayers else null,
                serverName = if ("server" in requestedStats) server?.serverModName else null,
                minecraftVersion = if ("server" in requestedStats) server?.serverVersion else null,
            )
        return json.encodeToString(packet)
    }

    override fun players(): BackendPlayers = players

    private fun send(
        player: BackendPlayer,
        payload: CustomPacketPayload,
    ) {
        serverProvider()?.playerList?.getPlayer(player.uuid)?.let {
            ServerPlayNetworking.send(it, payload)
        }
    }

    private fun ramUsed(): Long = (Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }) / 1024 / 1024

    private fun ramMax(): Long = Runtime.getRuntime().maxMemory() / 1024 / 1024

    private fun uptime(): Long = ManagementFactory.getRuntimeMXBean().uptime / 1000

    private fun cpuLoad(): Double? {
        val bean = ManagementFactory.getOperatingSystemMXBean()
        if (bean !is com.sun.management.OperatingSystemMXBean) return null
        val load = bean.processCpuLoad
        if (load < 0.0) return null
        return BigDecimal.valueOf((load * 100.0).coerceIn(0.0, 100.0)).setScale(1, RoundingMode.HALF_UP).toDouble()
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
        val onlinePlayers =
            server
                ?.playerList
                ?.players
                ?.map { PlayerSummaryPacket(it.uuid.toString(), it.name.string, it.name.string, true, false, false, false) }
                ?: emptyList()
        return PlayerListPacket(
            type = "player_list",
            onlineCount = onlinePlayers.size,
            maxPlayers = server?.maxPlayers ?: 0,
            onlinePlayers = onlinePlayers,
            whitelistedPlayers = emptyList(),
            blockedPlayers = emptyList(),
        )
    }

    override fun detail(
        viewer: BackendPlayer,
        uuid: UUID,
    ): PlayerDetailPacket? = null

    override fun applyAction(
        viewer: BackendPlayer,
        packet: PlayerActionPacket,
    ): PlayerActionResultPacket =
        PlayerActionResultPacket(
            type = "player_action_result",
            requestId = packet.requestId,
            uuid = packet.uuid,
            action = packet.action,
            success = false,
            message = "Fabric player actions are not implemented yet.",
        )
}
