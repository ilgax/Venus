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
        val lines = ArrayDeque<String>()
        val dispatched = platform.executeCommand(player, packet.command) { appendBoundedLine(packet.command, lines, it) }
        if (!dispatched && lines.isEmpty()) {
            appendBoundedLine(packet.command, lines, "Unknown command.")
        }
        val response =
            json.encodeToString(
                CmdResponsePacket.serializer(),
                CmdResponsePacket(type = "cmd_response", command = packet.command, lines = lines.toList()),
            )
        platform.sendData(player, response)
    }

    private fun appendBoundedLine(
        command: String,
        lines: ArrayDeque<String>,
        line: String,
    ) {
        lines.addLast(line)
        while (lines.size > MAX_LINES_PER_PACKET) lines.removeFirst()
        while (!fitsPacket(command, lines) && lines.size > 1) lines.removeFirst()
        if (!fitsPacket(command, lines)) {
            lines[lines.lastIndex] = fitSingleLine(command, lines.last())
        }
    }

    private fun fitsPacket(
        command: String,
        lines: Collection<String>,
    ): Boolean = runCatching { CmdResponsePacket("cmd_response", command, lines.toList()) }.isSuccess

    private fun fitSingleLine(
        command: String,
        line: String,
    ): String {
        var low = 0
        var high = line.length
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (fitsPacket(command, listOf(line.take(middle)))) {
                low = middle
            } else {
                high = middle - 1
            }
        }
        return line.take(low)
    }
}
