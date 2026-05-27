package dev.ilgax.venus.handlers

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.Handshake
import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.PendingApproval
import dev.ilgax.venus.auth.PendingSession
import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.config.VenusConfig
import dev.ilgax.venus.protocol.AuthChallengePacket
import dev.ilgax.venus.protocol.AuthResponsePacket
import dev.ilgax.venus.protocol.ClientKeyPacket
import dev.ilgax.venus.protocol.ErrorPacket
import dev.ilgax.venus.protocol.ReadyPacket
import dev.ilgax.venus.protocol.ServerKeyPacket
import dev.ilgax.venus.stats.StatSubscriptionManager
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.security.PublicKey
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AuthHandler(
    private val plugin: JavaPlugin,
    private val json: Json,
    private val keyManager: KeyManager,
    private val sendKey: (Player, String) -> Unit,
    private val sendAuth: (Player, String) -> Unit,
    private val sendReady: (Player, String) -> Unit,
) {
    private val sessionTimeoutTasks = ConcurrentHashMap<UUID, BukkitTask>()

    fun handleHello(player: Player) {
        sessionTimeoutTasks.remove(player.uniqueId)?.cancel()
        plugin.logger.info("Venus mod detected: ${player.name}")
        val data =
            json.encodeToString(
                ServerKeyPacket.serializer(),
                ServerKeyPacket(type = "server_key", publicKey = keyManager.publicKeyBase64),
            )
        sendKey(player, data)
        plugin.logger.info("Sent server public key to ${player.name}")
    }

    fun handleClientKey(
        player: Player,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<ClientKeyPacket>(data)
            } catch (e: SerializationException) {
                plugin.logger.warning("Malformed client key packet from ${player.name}: ${e.message}")
                return
            }
        if (packet.type != "client_key") {
            plugin.logger.warning("Invalid client key packet type from ${player.name}: ${packet.type}")
            return
        }
        val clientPublicKeyBase64 = packet.publicKey
        if (clientPublicKeyBase64.isBlank()) {
            plugin.logger.warning("Empty client key from ${player.name}")
            return
        }

        val clientPublicKey =
            try {
                Handshake.decodePublicKey(clientPublicKeyBase64)
            } catch (e: Exception) {
                plugin.logger.warning("Invalid client key from ${player.name}: ${e.message}")
                return
            }

        if (plugin.server.onlineMode && VenusConfig.cacheVerifiedUuid) {
            val cachedKey = SessionManager.getCachedKey(player.uniqueId)
            if (cachedKey != null) {
                if (cachedKey != clientPublicKeyBase64) {
                    plugin.logger.warning("UUID cache mismatch for ${player.name} - key changed, falling through to full auth")
                    SessionManager.clearUUIDCache(player.uniqueId)
                } else {
                    plugin.logger.info("UUID cache hit for ${player.name} - skipping authorized_keys check")
                    startChallenge(player, clientPublicKey, expireChallenge = true)
                    return
                }
            }
        }

        if (AuthorizedKeys.isAuthorized(clientPublicKeyBase64)) {
            startChallenge(player, clientPublicKey, expireChallenge = true)
            plugin.logger.info("Authorized key recognized for ${player.name} - sending challenge")
        } else {
            if (AuthorizedKeys.count() >= VenusConfig.maxUsers) {
                plugin.logger.warning("${player.name} tried to connect to Venus but max_users (${VenusConfig.maxUsers}) reached - rejecting.")
                return
            }

            SessionManager.addPendingApproval(
                player.uniqueId,
                PendingApproval(clientPublicKey, clientPublicKeyBase64),
            )
            plugin.logger.info("${player.name} wants to connect to Venus. Type 'venus allow' or 'venus deny'")

            plugin.server.scheduler.runTaskLater(
                plugin,
                Runnable {
                    if (SessionManager.getPendingApproval(player.uniqueId) != null) {
                        SessionManager.removePendingApproval(player.uniqueId)
                        plugin.logger.info("Venus request from ${player.name} timed out.")
                    }
                },
                (VenusConfig.sessionTimeoutSeconds * 20L),
            )
        }
    }

    fun handleAuthResponse(
        player: Player,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<AuthResponsePacket>(data)
            } catch (e: SerializationException) {
                plugin.logger.warning("Malformed auth response packet from ${player.name}: ${e.message}")
                return
            }
        if (packet.type != "auth_response") {
            plugin.logger.warning("Invalid auth response packet type from ${player.name}: ${packet.type}")
            return
        }
        val pending = SessionManager.getPending(player.uniqueId)
        if (pending == null) {
            plugin.logger.warning("No pending session for ${player.name}")
            return
        }

        val challenge =
            try {
                Base64.getDecoder().decode(packet.challenge)
            } catch (_: IllegalArgumentException) {
                plugin.logger.warning("Invalid Base64 in auth challenge from ${player.name}")
                SessionManager.removePending(player.uniqueId)
                return
            }
        val clientSig =
            try {
                Base64.getDecoder().decode(packet.clientSignature)
            } catch (_: IllegalArgumentException) {
                plugin.logger.warning("Invalid Base64 in auth signature from ${player.name}")
                SessionManager.removePending(player.uniqueId)
                return
            }

        if (!challenge.contentEquals(pending.challenge)) {
            plugin.logger.warning("Challenge mismatch from ${player.name}")
            SessionManager.removePending(player.uniqueId)
            return
        }

        if (!Handshake.verify(challenge, clientSig, pending.clientPublicKey)) {
            plugin.logger.warning("Invalid signature from ${player.name} - rejecting")
            SessionManager.removePending(player.uniqueId)
            return
        }

        SessionManager.removePending(player.uniqueId)
        sessionTimeoutTasks.remove(player.uniqueId)?.cancel()
        SessionManager.activate(player.uniqueId, pending.clientPublicKey)

        if (plugin.server.onlineMode && VenusConfig.cacheVerifiedUuid) {
            SessionManager.cacheUUID(
                player.uniqueId,
                Base64.getEncoder().encodeToString(pending.clientPublicKey.encoded),
            )
        }

        plugin.logger.info("${player.name} authenticated successfully!")
        val data =
            json.encodeToString(
                ReadyPacket.serializer(),
                ReadyPacket(type = "ready"),
            )
        sendReady(player, data)
        plugin.logger.info("Sent venus:ready to ${player.name}")
    }

    fun handleClientError(
        player: Player,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<ErrorPacket>(data)
            } catch (e: SerializationException) {
                plugin.logger.warning("${player.name} sent malformed error packet: ${e.message}")
                return
            }
        if (packet.type != "error") {
            plugin.logger.warning("${player.name} sent invalid error packet type: ${packet.type}")
            return
        }
        when (val reason = packet.reason) {
            "mitm_key_mismatch" -> {
                plugin.logger.warning(
                    "${player.name} rejected connection - server key mismatch on client side (possible MITM)",
                )
            }

            "mitm_sig_fail" -> {
                plugin.logger.warning(
                    "${player.name} rejected connection - server signature verification failed on client side (possible MITM)",
                )
            }

            else -> {
                plugin.logger.warning("${player.name} sent error: $reason")
            }
        }
    }

    fun startApprovedChallenge(
        player: Player,
        clientPublicKey: PublicKey,
    ) {
        startChallenge(player, clientPublicKey, expireChallenge = false)
    }

    fun onPlayerQuit(player: Player) {
        val uuid = player.uniqueId
        if (!SessionManager.isActive(uuid)) return

        plugin.logger.info("${player.name} disconnected - starting ${VenusConfig.sessionTimeoutSeconds}s Venus session timeout")

        val task =
            plugin.server.scheduler.runTaskLater(
                plugin,
                Runnable {
                    if (!SessionManager.isActive(uuid)) return@Runnable
                    SessionManager.deactivate(uuid)
                    StatSubscriptionManager.cancel(uuid)
                    sessionTimeoutTasks.remove(uuid)
                    plugin.logger.info("Venus session expired for ${player.name}")
                },
                (VenusConfig.sessionTimeoutSeconds * 20L),
            )
        sessionTimeoutTasks[uuid] = task
    }

    fun cancelAllTimeouts() {
        sessionTimeoutTasks.values.forEach { it.cancel() }
        sessionTimeoutTasks.clear()
    }

    private fun startChallenge(
        player: Player,
        clientPublicKey: PublicKey,
        expireChallenge: Boolean,
    ) {
        val challenge = Handshake.generateChallenge()
        val serverSig = Handshake.sign(challenge, keyManager.privateKey)
        SessionManager.addPending(player.uniqueId, PendingSession(clientPublicKey, challenge))
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
        sendAuth(player, data)
    }

    private fun scheduleAuthChallengeTimeout(player: Player) {
        val uuid = player.uniqueId
        sessionTimeoutTasks[uuid]?.cancel()
        sessionTimeoutTasks[uuid] =
            plugin.server.scheduler.runTaskLater(
                plugin,
                Runnable {
                    if (SessionManager.getPending(uuid) != null) {
                        SessionManager.removePending(uuid)
                        plugin.logger.info("Auth challenge expired for ${player.name}")
                    }
                    sessionTimeoutTasks.remove(uuid)
                },
                (VenusConfig.sessionTimeoutSeconds * 20L),
            )
    }
}
