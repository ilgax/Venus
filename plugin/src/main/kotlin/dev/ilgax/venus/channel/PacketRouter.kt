package dev.ilgax.venus.channel

import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.handlers.ConsoleHandler
import dev.ilgax.venus.handlers.LogHandler
import dev.ilgax.venus.handlers.PlayersHandler
import dev.ilgax.venus.handlers.StatsHandler
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        fun fromPacketType(packetType: String): CommandRoute? = entries.firstOrNull { it.packetType == packetType }
    }
}

class PacketRouter(
    private val plugin: JavaPlugin,
    private val json: Json,
    private val consoleHandler: ConsoleHandler,
    private val statsHandler: StatsHandler,
    private val logHandler: LogHandler,
    private val playersHandler: PlayersHandler,
) {
    fun handleCommand(
        player: Player,
        data: String,
    ) {
        if (!SessionManager.isActive(player.uniqueId)) {
            plugin.logger.warning("${player.name} sent cmd packet without active session - ignoring")
            return
        }

        val jsonElement =
            try {
                json.parseToJsonElement(data)
            } catch (e: SerializationException) {
                plugin.logger.warning("${player.name} sent malformed cmd packet: ${e.message}")
                return
            }
        val type =
            jsonElement.jsonObject["type"]?.jsonPrimitive?.content
                ?: run {
                    plugin.logger.warning("${player.name} sent cmd packet without type field")
                    return
                }

        when (CommandRoute.fromPacketType(type)) {
            CommandRoute.CONSOLE_CMD -> consoleHandler.handle(player, data)
            CommandRoute.LOG_SUBSCRIBE -> logHandler.handleSubscribe(player, data)
            CommandRoute.STAT_SUBSCRIBE -> statsHandler.handleSubscribe(player, data)
            CommandRoute.STAT_GET -> statsHandler.handleGet(player, data)
            CommandRoute.PLAYER_LIST_GET -> playersHandler.handleListGet(player, data)
            CommandRoute.PLAYER_DETAIL_GET -> playersHandler.handleDetailGet(player, data)
            CommandRoute.PLAYER_ACTION -> playersHandler.handleAction(player, data)
            null -> plugin.logger.warning("${player.name} sent unknown cmd packet type: $type")
        }
    }
}
