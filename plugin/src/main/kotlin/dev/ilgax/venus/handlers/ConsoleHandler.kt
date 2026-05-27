package dev.ilgax.venus.handlers

import dev.ilgax.venus.protocol.CmdResponsePacket
import dev.ilgax.venus.protocol.ConsoleCmdPacket
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class ConsoleHandler(
    private val plugin: JavaPlugin,
    private val json: Json,
    private val sendData: (Player, String) -> Unit,
) {
    fun handle(
        player: Player,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<ConsoleCmdPacket>(data)
            } catch (e: SerializationException) {
                plugin.logger.warning("${player.name} sent malformed console_cmd packet: ${e.message}")
                return
            }
        plugin.logger.info("${player.name} executed console command: ${packet.command}")
        val lines = mutableListOf<String>()
        val sender =
            plugin.server.createCommandSender { component ->
                lines.add(PlainTextComponentSerializer.plainText().serialize(component))
            }
        plugin.server.dispatchCommand(sender, packet.command)
        if (lines.isNotEmpty()) {
            val response =
                json.encodeToString(
                    CmdResponsePacket.serializer(),
                    CmdResponsePacket(type = "cmd_response", command = packet.command, lines = lines),
                )
            sendData(player, response)
        }
    }
}
