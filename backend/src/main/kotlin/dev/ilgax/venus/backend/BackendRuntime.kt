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
) {
    fun onPlayerQuit(player: BackendPlayer) {
        authHandler.onPlayerQuit(player)
        logHandler.unsubscribe(player.uuid)
    }

    fun shutdown() {
        authHandler.cancelAllTimeouts()
        statSubscriptions.cancelAll()
        SessionManager.clearAll()
    }

    companion object {
        fun create(
            platform: BackendPlatform,
            json: Json,
            keyManager: KeyManager,
        ): BackendRuntime {
            val statSubscriptions = BackendStatSubscriptionManager(platform)
            val logHandler = BackendLogHandler(platform, json)
            val authHandler = BackendAuthHandler(platform, json, keyManager, statSubscriptions)
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
                )
            return BackendRuntime(
                authHandler = authHandler,
                channelHandler = BackendChannelHandler(authHandler, packetRouter),
                logHandler = logHandler,
                statSubscriptions = statSubscriptions,
                approvals = BackendApprovalService(platform, authHandler),
            )
        }
    }
}
