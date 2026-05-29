package dev.ilgax.venus.channel

import dev.ilgax.venus.protocol.CmdResponsePacket
import dev.ilgax.venus.protocol.ConsoleLogPacket
import dev.ilgax.venus.protocol.ReadyPacket
import dev.ilgax.venus.protocol.StatSubscribePacket
import dev.ilgax.venus.protocol.StatsPacket
import dev.ilgax.venus.state.SessionState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PacketHandler(
    private val json: Json,
    private val sendCommand: (String) -> Unit,
    private val log: (String) -> Unit = ::println,
) {
    fun handleReady(data: String) {
        val packet =
            try {
                json.decodeFromString(ReadyPacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid ready packet - ${e.message}")
                return
            }
        if (packet.type != "ready") {
            log("Venus: unexpected ready packet type: ${packet.type}")
            return
        }

        SessionState.activate()
        log("Venus: session ready")
        val subscription =
            json.encodeToString(
                StatSubscribePacket.serializer(),
                StatSubscribePacket(
                    type = "stat_subscribe",
                    intervalSeconds = 1,
                    stats = listOf("tps", "ram", "mspt", "uptime", "players", "server", "cpu"),
                ),
            )
        sendCommand(subscription)
    }

    fun handleData(data: String) {
        val type =
            try {
                json
                    .parseToJsonElement(data)
                    .jsonObject["type"]
                    ?.jsonPrimitive
                    ?.content
            } catch (e: Exception) {
                log("Venus: invalid data packet - ${e.message}")
                return
            }
        when (type) {
            "stats" -> handleStats(data)
            "console_log" -> handleConsoleLog(data)
            "cmd_response" -> handleCommandResponse(data)
            else -> log("Venus: unexpected data packet type: $type")
        }
    }

    private fun handleStats(data: String) {
        val packet =
            try {
                json.decodeFromString(StatsPacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid stats packet - ${e.message}")
                return
            }
        SessionState.updateStats(packet)
    }

    private fun handleCommandResponse(data: String) {
        val packet =
            try {
                json.decodeFromString(CmdResponsePacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid cmd_response packet - ${e.message}")
                return
            }
        SessionState.addCommandResponse(packet)
    }

    private fun handleConsoleLog(data: String) {
        val packet =
            try {
                json.decodeFromString(ConsoleLogPacket.serializer(), data)
            } catch (e: Exception) {
                log("Venus: invalid console_log packet - ${e.message}")
                return
            }
        SessionState.addConsoleLines(packet.lines)
    }
}
