package dev.ilgax.venus.backend

import dev.ilgax.venus.protocol.CmdResponsePacket
import dev.ilgax.venus.protocol.ConsoleCmdPacket
import dev.ilgax.venus.protocol.LogSanitizer
import dev.ilgax.venus.protocol.MAX_LINES_PER_PACKET
import kotlinx.serialization.json.Json

class BackendConsoleHandler(
    private val platform: BackendPlatform,
    private val json: Json,
    private val suppressOwnExecutionLog: (BackendPlayer, String) -> Unit = { _, _ -> },
) {
    fun handle(
        player: BackendPlayer,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<ConsoleCmdPacket>(data)
            } catch (e: Exception) {
                platform.logger.warning("${player.name} sent malformed console_cmd packet: ${e.message}")
                return
            }
        if (packet.command.isBlank()) {
            platform.logger.warning("${player.name} sent blank console command - ignoring")
            return
        }
        val executionLog = "${player.name} executed console command: ${LogSanitizer.redactCommand(packet.command)}"
        suppressOwnExecutionLog(player, executionLog)
        platform.logger.info(executionLog)
        val lines = mutableListOf<String>()
        val dispatched = platform.executeCommand(player, packet.command) { lines.add(it) }
        if (!dispatched && lines.isEmpty()) {
            lines.add("Unknown command.")
        }
        val response =
            json.encodeToString(
                CmdResponsePacket.serializer(),
                CmdResponsePacket(type = "cmd_response", command = packet.command, lines = lines.takeLast(MAX_LINES_PER_PACKET)),
            )
        platform.sendData(player, response)
    }
}
