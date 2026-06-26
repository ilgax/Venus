package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.SessionManager
import kotlinx.serialization.json.Json

class BackendRuntime private constructor(
    val authHandler: BackendAuthHandler,
    val channelHandler: BackendChannelHandler,
    val logHandler: BackendLogHandler,
    val statSubscriptions: BackendStatSubscriptionManager,
    val approvals: BackendApprovalService,
    private val sessionManager: SessionManager,
) {
    fun onPlayerQuit(player: BackendPlayer) {
        authHandler.onPlayerQuit(player)
        logHandler.unsubscribe(player.uuid)
        channelHandler.cleanupPlayer(player.uuid)
    }

    fun shutdown() {
        authHandler.cancelAllTimeouts()
        statSubscriptions.cancelAll()
        sessionManager.clearAll()
    }

    companion object {
        fun create(
            platform: BackendPlatform,
            json: Json,
            keyManager: KeyManager,
        ): BackendRuntime {
            val sessionManager = SessionManager()
            val statSubscriptions = BackendStatSubscriptionManager(platform, sessionManager)
            val logHandler = BackendLogHandler(platform, json, sessionManager)
            val authHandler =
                BackendAuthHandler(
                    platform,
                    json,
                    keyManager,
                    statSubscriptions,
                    sessionManager,
                )
            val packetRouter =
                BackendPacketRouter(
                    platform,
                    json,
                    BackendConsoleHandler(platform, json) { player, marker ->
                        logHandler.suppressNextFor(player.uuid, marker)
                    },
                    BackendStatsHandler(platform, json, statSubscriptions),
                    logHandler,
                    BackendPlayersHandler(platform, json),
                    sessionManager,
                )
            return BackendRuntime(
                authHandler = authHandler,
                channelHandler = BackendChannelHandler(authHandler, packetRouter, sessionManager, platform.logger),
                logHandler = logHandler,
                statSubscriptions = statSubscriptions,
                approvals =
                    BackendApprovalService(platform, authHandler, sessionManager) { uuid ->
                        statSubscriptions.cancel(uuid)
                        logHandler.unsubscribe(uuid)
                    },
                sessionManager = sessionManager,
            )
        }
    }
}
