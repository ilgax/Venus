package dev.ilgax.venus.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ServerKeyPacket(
    val type: String,
    @SerialName("public_key") val publicKey: String,
)

@Serializable
data class ClientKeyPacket(
    val type: String,
    @SerialName("public_key") val publicKey: String,
)

@Serializable
data class AuthChallengePacket(
    val type: String,
    val challenge: String,
    @SerialName("server_sig") val serverSignature: String,
)

@Serializable
data class AuthResponsePacket(
    val type: String,
    val challenge: String,
    @SerialName("client_sig") val clientSignature: String,
)

@Serializable
data class ReadyPacket(
    val type: String,
)

@Serializable
data class ErrorPacket(
    val type: String,
    val reason: String,
)

@Serializable
data class StatSubscribePacket(
    val type: String,
    @SerialName("interval_seconds") val intervalSeconds: Int = 1,
    val stats: List<String> = listOf("tps", "ram"),
) {
    init {
        require(intervalSeconds in 1..300) { "interval_seconds must be in 1..300" }
        require(stats.size <= MAX_STATS_ENTRIES) { "stats list must have at most $MAX_STATS_ENTRIES entries" }
    }
}

@Serializable
data class StatGetPacket(
    val type: String,
    val stats: List<String> = listOf("tps", "ram"),
) {
    init {
        require(stats.size <= MAX_STATS_ENTRIES) { "stats list must have at most $MAX_STATS_ENTRIES entries" }
    }
}

@Serializable
data class ConsoleCmdPacket(
    val type: String,
    val command: String,
) {
    init {
        require(command.length <= MAX_COMMAND_LENGTH) { "command must be at most $MAX_COMMAND_LENGTH chars" }
    }
}

@Serializable
data class ConsoleLogSubscribePacket(
    val type: String,
)

@Serializable
data class ConsoleLogPacket(
    val type: String,
    val lines: List<String>,
) {
    init {
        require(lines.size <= MAX_LINES_PER_PACKET) { "lines must have at most $MAX_LINES_PER_PACKET entries" }
        requireEncodedPacketSize("console_log", consoleLogPacketSize(lines))
    }
}

@Serializable
data class CmdResponsePacket(
    val type: String,
    val command: String,
    val lines: List<String>,
) {
    init {
        require(command.length <= MAX_COMMAND_LENGTH) { "command must be at most $MAX_COMMAND_LENGTH chars" }
        require(lines.size <= MAX_LINES_PER_PACKET) { "lines must have at most $MAX_LINES_PER_PACKET entries" }
        requireEncodedPacketSize("cmd_response", cmdResponsePacketSize(command, lines))
    }
}

@Serializable
data class PlayerListGetPacket(
    val type: String,
)

@Serializable
data class PlayerDetailGetPacket(
    val type: String,
    val uuid: String,
)

@Serializable
data class PlayerActionPacket(
    val type: String,
    @SerialName("request_id") val requestId: String,
    val uuid: String,
    val action: String,
    val value: JsonElement? = null,
)

@Serializable
data class PlayerActionResultPacket(
    val type: String,
    @SerialName("request_id") val requestId: String,
    val uuid: String,
    val action: String,
    val success: Boolean,
    val message: String,
)

@Serializable
data class PlayerSummaryPacket(
    val uuid: String,
    val name: String,
    @SerialName("display_name") val displayName: String,
    val online: Boolean,
    val operator: Boolean,
    val whitelisted: Boolean,
    val blocked: Boolean,
)

@Serializable
data class PlayerListPacket(
    val type: String,
    @SerialName("online_count") val onlineCount: Int,
    @SerialName("max_players") val maxPlayers: Int,
    @SerialName("online_players") val onlinePlayers: List<PlayerSummaryPacket>,
    @SerialName("whitelisted_players") val whitelistedPlayers: List<PlayerSummaryPacket>,
    @SerialName("blocked_players") val blockedPlayers: List<PlayerSummaryPacket>,
) {
    init {
        require(onlinePlayers.size <= MAX_PLAYERS_PER_LIST) { "online_players must have at most $MAX_PLAYERS_PER_LIST entries" }
        require(whitelistedPlayers.size <= MAX_PLAYERS_PER_LIST) { "whitelisted_players must have at most $MAX_PLAYERS_PER_LIST entries" }
        require(blockedPlayers.size <= MAX_PLAYERS_PER_LIST) { "blocked_players must have at most $MAX_PLAYERS_PER_LIST entries" }
    }
}

@Serializable
data class PlayerDetailPacket(
    val type: String,
    val player: PlayerDetail,
)

@Serializable
data class PlayerDetail(
    val uuid: String,
    val name: String,
    @SerialName("display_name") val displayName: String,
    val online: Boolean,
    val operator: Boolean,
    val whitelisted: Boolean,
    val blocked: Boolean,
    @SerialName("game_mode") val gameMode: String? = null,
    val health: Double? = null,
    @SerialName("max_health") val maxHealth: Double? = null,
    @SerialName("food_level") val foodLevel: Int? = null,
    val level: Int? = null,
    @SerialName("experience_progress") val experienceProgress: Float? = null,
    val world: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
)

@Serializable
data class StatsPacket(
    val type: String,
    val tps: Double? = null,
    val mspt: Double? = null,
    @SerialName("cpu_load") val cpuLoad: Double? = null,
    @SerialName("ram_used") val ramUsed: Long? = null,
    @SerialName("ram_max") val ramMax: Long? = null,
    val uptime: Long? = null,
    @SerialName("online_players") val onlinePlayers: Int? = null,
    @SerialName("max_players") val maxPlayers: Int? = null,
    @SerialName("server_name") val serverName: String? = null,
    @SerialName("minecraft_version") val minecraftVersion: String? = null,
)

const val MAX_PACKET_SIZE: Int = 16_384
const val MAX_STATS_ENTRIES: Int = 32
const val MAX_COMMAND_LENGTH: Int = 256
const val MAX_LINES_PER_PACKET: Int = 100
const val MAX_PLAYERS_PER_LIST: Int = 200
const val PRE_AUTH_RATE_LIMIT: Int = 5
const val PRE_AUTH_RATE_WINDOW_MS: Long = 10_000

private fun requireEncodedPacketSize(
    packetType: String,
    size: Int,
) {
    require(size <= MAX_PACKET_SIZE) { "$packetType packet must be at most $MAX_PACKET_SIZE bytes when encoded" }
}

private fun consoleLogPacketSize(lines: List<String>): Int =
    """{"type":"console_log","lines":[]}""".toByteArray(Charsets.UTF_8).size +
        lines.sumOf(::jsonStringSize) +
        (lines.size - 1).coerceAtLeast(0)

private fun cmdResponsePacketSize(
    command: String,
    lines: List<String>,
): Int =
    """{"type":"cmd_response","command":,"lines":[]}""".toByteArray(Charsets.UTF_8).size +
        jsonStringSize(command) +
        lines.sumOf(::jsonStringSize) +
        (lines.size - 1).coerceAtLeast(0)

private fun jsonStringSize(value: String): Int {
    var size = 2
    value.forEach { char ->
        size +=
            when (char) {
                '\\', '"', '\n', '\r', '\t', '\b', '\u000C' -> 2
                in '\u0000'..'\u001F' -> 6
                else -> char.toString().toByteArray(Charsets.UTF_8).size
            }
    }
    return size
}
