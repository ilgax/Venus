package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.Handshake
import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.PendingApproval
import dev.ilgax.venus.auth.PendingSession
import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.protocol.AuthChallengePacket
import dev.ilgax.venus.protocol.AuthResponsePacket
import dev.ilgax.venus.protocol.ClientKeyPacket
import dev.ilgax.venus.protocol.ErrorPacket
import dev.ilgax.venus.protocol.ReadyPacket
import dev.ilgax.venus.protocol.ServerKeyPacket
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.security.PublicKey
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BackendAuthHandler(
    private val platform: BackendPlatform,
    private val json: Json,
    private val keyManager: KeyManager,
    private val subscriptions: BackendStatSubscriptionManager,
) {
    private val sessionTimeoutTasks = ConcurrentHashMap<UUID, BackendTask>()

    fun handleHello(player: BackendPlayer) {
        sessionTimeoutTasks.remove(player.uuid)?.cancel()
        val data =
            json.encodeToString(
                ServerKeyPacket.serializer(),
                ServerKeyPacket(type = "server_key", publicKey = keyManager.publicKeyBase64),
            )
        platform.sendKey(player, data)
    }

    fun handleClientKey(
        player: BackendPlayer,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<ClientKeyPacket>(data)
            } catch (e: SerializationException) {
                platform.logger.warning("Malformed client key packet from ${player.name}: ${e.message}")
                return
            }
        if (packet.type != "client_key") {
            platform.logger.warning("Invalid client key packet type from ${player.name}: ${packet.type}")
            return
        }
        val clientPublicKeyBase64 = packet.publicKey
        if (clientPublicKeyBase64.isBlank()) {
            platform.logger.warning("Empty client key from ${player.name}")
            return
        }

        val clientPublicKey =
            try {
                Handshake.decodePublicKey(clientPublicKeyBase64)
            } catch (e: Exception) {
                platform.logger.warning("Invalid client key from ${player.name}: ${e.message}")
                return
            }

        if (AuthorizedKeys.isAuthorized(clientPublicKeyBase64)) {
            startChallenge(player, clientPublicKey, expireChallenge = true)
        } else {
            if (AuthorizedKeys.count() >= platform.config.maxUsers) {
                platform.logger.warning(
                    "${player.name} tried to connect to Venus but max_users (${platform.config.maxUsers}) reached - rejecting.",
                )
                sendAuthError(player, "auth_max_users")
                return
            }

            SessionManager.addPendingApproval(
                player.uuid,
                PendingApproval(clientPublicKey, clientPublicKeyBase64),
            )
            platform.logger.info("${player.name} wants to connect to Venus. Type 'venus allow' or 'venus deny'")

            platform.scheduler.runLater(platform.config.authTimeoutSeconds * 20L) {
                if (SessionManager.getPendingApproval(player.uuid) != null) {
                    platform.player(player.uuid)?.let { sendAuthError(it, "auth_timeout") }
                    SessionManager.removePendingApproval(player.uuid)
                    platform.logger.info("Venus request from ${player.name} timed out.")
                }
            }
        }
    }

    fun handleAuthResponse(
        player: BackendPlayer,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<AuthResponsePacket>(data)
            } catch (e: SerializationException) {
                platform.logger.warning("Malformed auth response packet from ${player.name}: ${e.message}")
                sendAuthError(player, "auth_invalid_response")
                return
            }
        if (packet.type != "auth_response") {
            platform.logger.warning("Invalid auth response packet type from ${player.name}: ${packet.type}")
            sendAuthError(player, "auth_invalid_response")
            return
        }
        val pending = SessionManager.getPending(player.uuid)
        if (pending == null) {
            platform.logger.warning("No pending session for ${player.name}")
            return
        }

        val challenge =
            try {
                Base64.getDecoder().decode(packet.challenge)
            } catch (_: IllegalArgumentException) {
                platform.logger.warning("Invalid Base64 in auth challenge from ${player.name}")
                sendAuthError(player, "auth_invalid_response")
                SessionManager.removePending(player.uuid)
                return
            }
        val clientSig =
            try {
                Base64.getDecoder().decode(packet.clientSignature)
            } catch (_: IllegalArgumentException) {
                platform.logger.warning("Invalid Base64 in auth signature from ${player.name}")
                sendAuthError(player, "auth_invalid_response")
                SessionManager.removePending(player.uuid)
                return
            }

        if (!challenge.contentEquals(pending.challenge)) {
            platform.logger.warning("Challenge mismatch from ${player.name}")
            sendAuthError(player, "auth_invalid_response")
            SessionManager.removePending(player.uuid)
            return
        }

        if (!Handshake.verify(challenge, clientSig, pending.clientPublicKey)) {
            platform.logger.warning("Invalid signature from ${player.name} - rejecting")
            sendAuthError(player, "auth_invalid_response")
            SessionManager.removePending(player.uuid)
            return
        }

        SessionManager.removePending(player.uuid)
        sessionTimeoutTasks.remove(player.uuid)?.cancel()
        SessionManager.activate(player.uuid, pending.clientPublicKey)
        platform.logger.info("Venus session active for ${player.name}")

        val ready =
            json.encodeToString(
                ReadyPacket.serializer(),
                ReadyPacket(type = "ready"),
            )
        platform.sendReady(player, ready)
    }

    fun handleClientError(
        player: BackendPlayer,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<ErrorPacket>(data)
            } catch (e: SerializationException) {
                platform.logger.warning("${player.name} sent malformed error packet: ${e.message}")
                return
            }
        if (packet.type != "error") {
            platform.logger.warning("${player.name} sent invalid error packet type: ${packet.type}")
            return
        }
        when (val reason = packet.reason) {
            "mitm_key_mismatch" ->
                platform.logger.warning(
                    "${player.name} rejected connection - server key mismatch on client side (possible MITM)",
                )

            "mitm_sig_fail" ->
                platform.logger.warning(
                    "${player.name} rejected connection - server signature verification failed on client side (possible MITM)",
                )

            else -> platform.logger.warning("${player.name} sent error: $reason")
        }
    }

    fun startApprovedChallenge(
        player: BackendPlayer,
        clientPublicKey: PublicKey,
    ) {
        startChallenge(player, clientPublicKey, expireChallenge = false)
    }

    fun notifyDenied(player: BackendPlayer) {
        sendAuthError(player, "auth_denied")
    }

    fun onPlayerQuit(player: BackendPlayer) {
        if (!SessionManager.isActive(player.uuid)) return

        sessionTimeoutTasks.remove(player.uuid)?.cancel()
        SessionManager.deactivate(player.uuid)
        subscriptions.cancel(player.uuid)
    }

    fun cancelAllTimeouts() {
        sessionTimeoutTasks.values.forEach { it.cancel() }
        sessionTimeoutTasks.clear()
    }

    private fun startChallenge(
        player: BackendPlayer,
        clientPublicKey: PublicKey,
        expireChallenge: Boolean,
    ) {
        val challenge = Handshake.generateChallenge()
        val serverSig = Handshake.sign(challenge, keyManager.privateKey)
        SessionManager.addPending(player.uuid, PendingSession(clientPublicKey, challenge))
        if (expireChallenge) {
            scheduleAuthChallengeTimeout(player)
        }
        val challengeB64 = Base64.getEncoder().encodeToString(challenge)
        val sigB64 = Base64.getEncoder().encodeToString(serverSig)
        val data =
            json.encodeToString(
                AuthChallengePacket.serializer(),
                AuthChallengePacket(type = "auth_challenge", challenge = challengeB64, serverSignature = sigB64),
            )
        platform.sendAuth(player, data)
    }

    private fun scheduleAuthChallengeTimeout(player: BackendPlayer) {
        val uuid = player.uuid
        sessionTimeoutTasks[uuid]?.cancel()
        sessionTimeoutTasks[uuid] =
            platform.scheduler.runLater(platform.config.authTimeoutSeconds * 20L) {
                if (SessionManager.getPending(uuid) != null) {
                    SessionManager.removePending(uuid)
                    platform.logger.info("Auth challenge expired for ${player.name}")
                }
                sessionTimeoutTasks.remove(uuid)
            }
    }

    private fun sendAuthError(
        player: BackendPlayer,
        reason: String,
    ) {
        val data =
            json.encodeToString(
                ErrorPacket.serializer(),
                ErrorPacket(type = "error", reason = reason),
            )
        platform.sendError(player, data)
    }
}
