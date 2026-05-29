package dev.ilgax.venus.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HelloPacket(
    val type: String,
)

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
    @SerialName("interval_seconds") val intervalSeconds: Int = 2,
    val stats: List<String> = listOf("tps", "ram"),
)

@Serializable
data class StatGetPacket(
    val type: String,
    val stats: List<String> = listOf("tps", "ram"),
)

@Serializable
data class ConsoleCmdPacket(
    val type: String,
    val command: String,
)

@Serializable
data class ConsoleLogSubscribePacket(
    val type: String,
)

@Serializable
data class ConsoleLogPacket(
    val type: String,
    val lines: List<String>,
)

@Serializable
data class CmdResponsePacket(
    val type: String,
    val command: String,
    val lines: List<String>,
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
