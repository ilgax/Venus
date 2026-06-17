package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.SessionManager
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class BackendCommandRoute(
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
        fun fromPacketType(packetType: String): BackendCommandRoute? = entries.firstOrNull { it.packetType == packetType }
    }
}

class BackendPacketRouter(
    private val platform: BackendPlatform,
    private val json: Json,
    private val consoleHandler: BackendConsoleHandler,
    private val statsHandler: BackendStatsHandler,
    private val logHandler: BackendLogHandler,
    private val playersHandler: BackendPlayersHandler,
) {
    fun handleCommand(
        player: BackendPlayer,
        data: String,
    ) {
        if (!SessionManager.isActive(player.uuid)) {
            platform.logger.warning("${player.name} sent cmd packet without active session - ignoring")
            return
        }

        val jsonElement =
            try {
                json.parseToJsonElement(data)
            } catch (e: SerializationException) {
                platform.logger.warning("${player.name} sent malformed cmd packet: ${e.message}")
                return
            }
        val type =
            try {
                jsonElement.jsonObject["type"]?.jsonPrimitive?.content
            } catch (e: RuntimeException) {
                platform.logger.warning("${player.name} sent malformed cmd packet: ${e.message}")
                return
            } ?: run {
                platform.logger.warning("${player.name} sent cmd packet without type field")
                return
            }

        when (BackendCommandRoute.fromPacketType(type)) {
            BackendCommandRoute.CONSOLE_CMD -> consoleHandler.handle(player, data)
            BackendCommandRoute.LOG_SUBSCRIBE -> logHandler.handleSubscribe(player, data)
            BackendCommandRoute.STAT_SUBSCRIBE -> statsHandler.handleSubscribe(player, data)
            BackendCommandRoute.STAT_GET -> statsHandler.handleGet(player, data)
            BackendCommandRoute.PLAYER_LIST_GET -> playersHandler.handleListGet(player, data)
            BackendCommandRoute.PLAYER_DETAIL_GET -> playersHandler.handleDetailGet(player, data)
            BackendCommandRoute.PLAYER_ACTION -> playersHandler.handleAction(player, data)
            null -> platform.logger.warning("${player.name} sent unknown cmd packet type: $type")
        }
    }
}
