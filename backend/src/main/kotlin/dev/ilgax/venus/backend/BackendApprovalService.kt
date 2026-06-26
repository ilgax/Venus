package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.Handshake
import dev.ilgax.venus.auth.SessionManager

data class BackendApprovalResult(
    val success: Boolean,
    val message: String,
)

class BackendApprovalService(
    private val platform: BackendPlatform,
    private val authHandler: BackendAuthHandler,
    private val sessionManager: SessionManager,
) {
    fun allowNextPending(): BackendApprovalResult {
        while (true) {
            val entry =
                sessionManager.getNextPendingApproval()
                    ?: return BackendApprovalResult(success = false, message = "No pending Venus requests.")

            val (uuid, approval) = entry
            val player = platform.player(uuid)
            if (player == null) {
                sessionManager.removePendingApproval(uuid)
                authHandler.cancelPendingApproval(uuid)
                continue
            }

            val fingerprint = Handshake.fingerprint(approval.clientPublicKey)
            AuthorizedKeys.authorize(approval.clientPublicKeyBase64, player.name)
            sessionManager.removePendingApproval(uuid)
            authHandler.cancelPendingApproval(uuid)
            authHandler.startApprovedChallenge(player, approval.clientPublicKey)
            platform.logger.info("${player.name} (key $fingerprint) authorized via console.")
            return BackendApprovalResult(success = true, message = "${player.name} (key $fingerprint) authorized.")
        }
    }

    fun denyNextPending(): BackendApprovalResult {
        while (true) {
            val entry =
                sessionManager.getNextPendingApproval()
                    ?: return BackendApprovalResult(success = false, message = "No pending Venus requests.")

            val (uuid, approval) = entry
            val player = platform.player(uuid)
            sessionManager.removePendingApproval(uuid)
            authHandler.cancelPendingApproval(uuid)
            if (player == null) {
                continue
            }
            authHandler.notifyDenied(player)
            val fingerprint = Handshake.fingerprint(approval.clientPublicKey)
            return BackendApprovalResult(success = true, message = "${player.name} (key $fingerprint) denied.")
        }
    }
}
