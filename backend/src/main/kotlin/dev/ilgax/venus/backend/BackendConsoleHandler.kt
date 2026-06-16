package dev.ilgax.venus.backend

import dev.ilgax.venus.protocol.CmdResponsePacket
import dev.ilgax.venus.protocol.ConsoleCmdPacket
import kotlinx.serialization.SerializationException
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
            } catch (e: SerializationException) {
                platform.logger.warning("${player.name} sent malformed console_cmd packet: ${e.message}")
                return
            }
        val executionLog = "${player.name} executed console command: ${packet.command}"
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
                CmdResponsePacket(type = "cmd_response", command = packet.command, lines = lines),
            )
        platform.sendData(player, response)
    }
}
