package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.SessionManager

data class BackendApprovalResult(
    val success: Boolean,
    val message: String,
)

class BackendApprovalService(
    private val platform: BackendPlatform,
    private val authHandler: BackendAuthHandler,
) {
    fun allowNextPending(): BackendApprovalResult {
        while (true) {
            val entry =
                SessionManager.getNextPendingApproval()
                    ?: return BackendApprovalResult(success = false, message = "No pending Venus requests.")

            val (uuid, approval) = entry
            val player = platform.player(uuid)
            if (player == null) {
                SessionManager.removePendingApproval(uuid)
                authHandler.cancelPendingApproval(uuid)
                continue
            }

            AuthorizedKeys.authorize(approval.clientPublicKeyBase64, player.name)
            SessionManager.removePendingApproval(uuid)
            authHandler.cancelPendingApproval(uuid)
            authHandler.startApprovedChallenge(player, approval.clientPublicKey)
            platform.logger.info("${player.name} authorized via console.")
            return BackendApprovalResult(success = true, message = "${player.name} authorized.")
        }
    }

    fun denyNextPending(): BackendApprovalResult {
        while (true) {
            val entry =
                SessionManager.getNextPendingApproval()
                    ?: return BackendApprovalResult(success = false, message = "No pending Venus requests.")

            val (uuid, _) = entry
            val player = platform.player(uuid)
            SessionManager.removePendingApproval(uuid)
            authHandler.cancelPendingApproval(uuid)
            if (player == null) {
                continue
            }
            authHandler.notifyDenied(player)
            return BackendApprovalResult(success = true, message = "${player.name} denied.")
        }
    }
}
