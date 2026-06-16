package dev.ilgax.venus.handlers

import dev.ilgax.venus.backend.BackendPlatform
import dev.ilgax.venus.backend.BackendPlayersHandler
import dev.ilgax.venus.platform.PaperBackendPlatform
import dev.ilgax.venus.platform.toBackendPlayer
import kotlinx.serialization.json.Json
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class PlayersHandler {
    internal val delegate: BackendPlayersHandler

    constructor(
        plugin: JavaPlugin,
        json: Json,
        sendData: (Player, String) -> Unit,
    ) {
        val platform = PaperBackendPlatform(plugin, sendDataPacket = sendData)
        delegate = BackendPlayersHandler(platform, json)
    }

    internal constructor(
        platform: BackendPlatform,
        json: Json,
    ) {
        delegate = BackendPlayersHandler(platform, json)
    }

    fun handleListGet(
        player: Player,
        data: String,
    ) = delegate.handleListGet(player.toBackendPlayer(), data)

    fun handleDetailGet(
        player: Player,
        data: String,
    ) = delegate.handleDetailGet(player.toBackendPlayer(), data)

    fun handleAction(
        player: Player,
        data: String,
    ) = delegate.handleAction(player.toBackendPlayer(), data)
}
