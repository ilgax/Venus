package dev.ilgax.venus.handlers

import dev.ilgax.venus.backend.BackendConsoleHandler
import dev.ilgax.venus.backend.BackendPlatform
import dev.ilgax.venus.platform.PaperBackendPlatform
import dev.ilgax.venus.platform.toBackendPlayer
import kotlinx.serialization.json.Json
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class ConsoleHandler {
    internal val delegate: BackendConsoleHandler

    constructor(
        plugin: JavaPlugin,
        json: Json,
        sendData: (Player, String) -> Unit,
        suppressOwnExecutionLog: (UUID, String) -> Unit = { _, _ -> },
    ) {
        val platform = PaperBackendPlatform(plugin, sendDataPacket = sendData)
        delegate =
            BackendConsoleHandler(platform, json) { player, marker ->
                suppressOwnExecutionLog(player.uuid, marker)
            }
    }

    internal constructor(
        platform: BackendPlatform,
        json: Json,
        suppressOwnExecutionLog: (UUID, String) -> Unit = { _, _ -> },
    ) {
        delegate =
            BackendConsoleHandler(platform, json) { player, marker ->
                suppressOwnExecutionLog(player.uuid, marker)
            }
    }

    fun handle(
        player: Player,
        data: String,
    ) = delegate.handle(player.toBackendPlayer(), data)
}
