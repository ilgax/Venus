package dev.ilgax.venus.handlers

import dev.ilgax.venus.backend.BackendPlatform
import dev.ilgax.venus.backend.BackendStatSubscriptionManager
import dev.ilgax.venus.backend.BackendStatsHandler
import dev.ilgax.venus.platform.PaperBackendPlatform
import dev.ilgax.venus.platform.toBackendPlayer
import kotlinx.serialization.json.Json
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class StatsHandler {
    internal val delegate: BackendStatsHandler

    constructor(
        plugin: JavaPlugin,
        json: Json,
        sendData: (Player, String) -> Unit,
    ) {
        val platform = PaperBackendPlatform(plugin, sendDataPacket = sendData)
        delegate = BackendStatsHandler(platform, json, BackendStatSubscriptionManager(platform))
    }

    internal constructor(
        platform: BackendPlatform,
        json: Json,
        subscriptions: BackendStatSubscriptionManager,
    ) {
        delegate = BackendStatsHandler(platform, json, subscriptions)
    }

    fun handleSubscribe(
        player: Player,
        data: String,
    ) = delegate.handleSubscribe(player.toBackendPlayer(), data)

    fun handleGet(
        player: Player,
        data: String,
    ) = delegate.handleGet(player.toBackendPlayer(), data)
}
