package dev.ilgax.venus.handlers

import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.backend.BackendAuthHandler
import dev.ilgax.venus.backend.BackendPlatform
import dev.ilgax.venus.backend.BackendStatSubscriptionManager
import dev.ilgax.venus.platform.PaperBackendPlatform
import dev.ilgax.venus.platform.toBackendPlayer
import kotlinx.serialization.json.Json
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.security.PublicKey

class AuthHandler {
    internal val delegate: BackendAuthHandler

    constructor(
        plugin: JavaPlugin,
        json: Json,
        keyManager: KeyManager,
        sendKey: (Player, String) -> Unit,
        sendAuth: (Player, String) -> Unit,
        sendReady: (Player, String) -> Unit,
        sendError: (Player, String) -> Unit,
    ) {
        val platform =
            PaperBackendPlatform(
                plugin,
                sendKeyPacket = sendKey,
                sendAuthPacket = sendAuth,
                sendReadyPacket = sendReady,
                sendErrorPacket = sendError,
            )
        delegate = BackendAuthHandler(platform, json, keyManager, BackendStatSubscriptionManager(platform))
    }

    internal constructor(
        platform: BackendPlatform,
        json: Json,
        keyManager: KeyManager,
        subscriptions: BackendStatSubscriptionManager,
    ) {
        delegate = BackendAuthHandler(platform, json, keyManager, subscriptions)
    }

    fun handleHello(player: Player) = delegate.handleHello(player.toBackendPlayer())

    fun handleClientKey(
        player: Player,
        data: String,
    ) = delegate.handleClientKey(player.toBackendPlayer(), data)

    fun handleAuthResponse(
        player: Player,
        data: String,
    ) = delegate.handleAuthResponse(player.toBackendPlayer(), data)

    fun handleClientError(
        player: Player,
        data: String,
    ) = delegate.handleClientError(player.toBackendPlayer(), data)

    fun startApprovedChallenge(
        player: Player,
        clientPublicKey: PublicKey,
    ) = delegate.startApprovedChallenge(player.toBackendPlayer(), clientPublicKey)

    fun notifyDenied(player: Player) = delegate.notifyDenied(player.toBackendPlayer())

    fun onPlayerQuit(player: Player) = delegate.onPlayerQuit(player.toBackendPlayer())

    fun cancelAllTimeouts() = delegate.cancelAllTimeouts()
}
