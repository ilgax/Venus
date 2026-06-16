package dev.ilgax.venus.channel

import dev.ilgax.venus.backend.BackendCommandRoute
import dev.ilgax.venus.backend.BackendPacketRouter
import dev.ilgax.venus.handlers.ConsoleHandler
import dev.ilgax.venus.handlers.LogHandler
import dev.ilgax.venus.handlers.PlayersHandler
import dev.ilgax.venus.handlers.StatsHandler
import dev.ilgax.venus.platform.PaperBackendPlatform
import dev.ilgax.venus.platform.toBackendPlayer
import kotlinx.serialization.json.Json
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

internal enum class CommandRoute(
    val packetType: String,
) {
    CONSOLE_CMD("console_cmd"),
    LOG_SUBSCRIBE("log_subscribe"),
    STAT_SUBSCRIBE("stat_subscribe"),
    STAT_GET("stat_get"),
    PLAYER_LIST_GET("player_list_get"),
    PLAYER_DETAIL_GET("player_detail_get"),
    PLAYER_ACTION("player_action"),
    ;

    companion object {
        fun fromPacketType(packetType: String): CommandRoute? =
            BackendCommandRoute.fromPacketType(packetType)?.let { route ->
                entries.firstOrNull { it.packetType == route.packetType }
            }
    }
}

class PacketRouter(
    plugin: JavaPlugin,
    json: Json,
    consoleHandler: ConsoleHandler,
    statsHandler: StatsHandler,
    logHandler: LogHandler,
    playersHandler: PlayersHandler,
) {
    internal val delegate =
        BackendPacketRouter(
            PaperBackendPlatform(plugin),
            json,
            consoleHandler.delegate,
            statsHandler.delegate,
            logHandler.delegate,
            playersHandler.delegate,
        )

    fun handleCommand(
        player: Player,
        data: String,
    ) = delegate.handleCommand(player.toBackendPlayer(), data)
}
