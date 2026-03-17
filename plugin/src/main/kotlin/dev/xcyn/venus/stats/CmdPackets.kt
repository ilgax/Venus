package dev.xcyn.venus.stats

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StatSubscribePacket(
    val type: String,
    @SerialName("interval_seconds") val intervalSeconds: Int = 2,
    val stats: List<String> = listOf("tps", "ram")
)

@Serializable
data class StatGetPacket(
    val type: String,
    val stats: List<String> = listOf("tps", "ram")
)

@Serializable
data class ConsoleCmdPacket(
    val type: String,
    val command: String
)