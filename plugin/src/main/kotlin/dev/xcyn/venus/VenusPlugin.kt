package dev.xcyn.venus

import dev.xcyn.venus.auth.AuthorizedKeys
import dev.xcyn.venus.auth.Handshake
import dev.xcyn.venus.auth.KeyManager
import dev.xcyn.venus.auth.PendingApproval
import dev.xcyn.venus.auth.PendingSession
import dev.xcyn.venus.auth.SessionManager
import dev.xcyn.venus.commands.VenusCommand
import dev.xcyn.venus.config.VenusConfig
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.DiscardedPayload
import net.minecraft.resources.Identifier
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.Base64

class VenusPlugin : JavaPlugin(), PluginMessageListener, Listener {

    lateinit var keyManager: KeyManager

    override fun onEnable() {
        VenusConfig.load(this)
        logger.info("Venus enabled")
        keyManager = KeyManager(dataFolder)
        keyManager.loadOrGenerate()
        logger.info("Server keypair loaded")
        AuthorizedKeys.init(dataFolder)

        registerCommand("venus", VenusCommand(this))
        server.pluginManager.registerEvents(this, this)

        server.messenger.registerIncomingPluginChannel(this, "venus:hello", this)
        server.messenger.registerIncomingPluginChannel(this, "venus:key", this)
        server.messenger.registerIncomingPluginChannel(this, "venus:auth", this)
    }

    override fun onDisable() {
        logger.info("Venus disabled")
        server.messenger.unregisterIncomingPluginChannel(this, "venus:hello")
        server.messenger.unregisterIncomingPluginChannel(this, "venus:key")
        server.messenger.unregisterIncomingPluginChannel(this, "venus:auth")
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        when (channel) {
            "venus:hello" -> {
                logger.info("Venus mod detected: ${player.name}")
                sendServerPublicKey(player)
            }
            "venus:key" -> {
                val clientPublicKeyBase64 = message.toString(Charsets.UTF_8)
                handleClientKey(player, clientPublicKeyBase64)
            }
            "venus:auth" -> {
                val response = message.toString(Charsets.UTF_8)
                handleAuthResponse(player, response)
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        SessionManager.deactivate(uuid)
        logger.info("Venus session deactivated for ${event.player.name}")
    }

    private fun sendServerPublicKey(player: Player) {
        val keyBytes = keyManager.publicKeyBase64.toByteArray(Charsets.UTF_8)
        val id = Identifier.fromNamespaceAndPath("venus", "key")
        val payload = DiscardedPayload(id, keyBytes)
        val packet = ClientboundCustomPayloadPacket(payload)
        (player as CraftPlayer).handle.connection.send(packet)
        logger.info("Sent server public key to ${player.name}")
    }

    private fun handleClientKey(player: Player, clientPublicKeyBase64: String) {
        if (clientPublicKeyBase64.isBlank()) {
            logger.warning("Empty client key from ${player.name}")
            return
        }

        val clientPublicKey = try {
            Handshake.decodePublicKey(clientPublicKeyBase64)
        } catch (e: Exception) {
            logger.warning("Invalid client key from ${player.name}: ${e.message}")
            return
        }

        if (server.onlineMode && VenusConfig.cacheVerifiedUuid) {
            val cachedKey = SessionManager.getCachedKey(player.uniqueId)
            if (cachedKey != null) {
                if (cachedKey != clientPublicKeyBase64) {
                    logger.warning("UUID cache mismatch for ${player.name} — key changed, falling through to full auth")
                    SessionManager.clearUUIDCache(player.uniqueId)
                } else {
                    logger.info("UUID cache hit for ${player.name} — skipping authorized_keys check")
                    val challenge = Handshake.generateChallenge()
                    val serverSig = Handshake.sign(challenge, keyManager.privateKey)
                    SessionManager.addPending(player.uniqueId, PendingSession(clientPublicKey, challenge))
                    sendAuthChallenge(player, challenge, serverSig)
                    return
                }
            }
        }

        if (AuthorizedKeys.isAuthorized(clientPublicKeyBase64)) {
            val challenge = Handshake.generateChallenge()
            val serverSig = Handshake.sign(challenge, keyManager.privateKey)
            SessionManager.addPending(player.uniqueId, PendingSession(clientPublicKey, challenge))
            sendAuthChallenge(player, challenge, serverSig)
            logger.info("Authorized key recognized for ${player.name} — sending challenge")
        } else {
            if (AuthorizedKeys.count() >= VenusConfig.maxUsers) {
                logger.warning("${player.name} tried to connect to Venus but max_users (${VenusConfig.maxUsers}) reached — rejecting.")
                return
            }

            SessionManager.addPendingApproval(
                player.uniqueId,
                PendingApproval(clientPublicKey, clientPublicKeyBase64)
            )
            logger.info("${player.name} wants to connect to Venus. Type 'venus allow' or 'venus deny'")

            server.scheduler.runTaskLater(this, Runnable {
                if (SessionManager.getPendingApproval(player.uniqueId) != null) {
                    SessionManager.removePendingApproval(player.uniqueId)
                    logger.info("Venus request from ${player.name} timed out.")
                }
            }, (VenusConfig.sessionTimeoutSeconds * 20L))
        }
    }

    private fun sendAuthChallenge(player: Player, challenge: ByteArray, serverSig: ByteArray) {
        val challengeB64 = Base64.getEncoder().encodeToString(challenge)
        val sigB64 = Base64.getEncoder().encodeToString(serverSig)
        val payload = "$challengeB64.$sigB64".toByteArray(Charsets.UTF_8)
        val id = Identifier.fromNamespaceAndPath("venus", "auth")
        val packet = ClientboundCustomPayloadPacket(DiscardedPayload(id, payload))
        (player as CraftPlayer).handle.connection.send(packet)
    }

    private fun handleAuthResponse(player: Player, response: String) {
        val parts = response.split(".")
        if (parts.size != 2) {
            logger.warning("Invalid auth response format from ${player.name}")
            return
        }

        val challengeB64 = parts[0]
        val clientSigB64 = parts[1]

        val pending = SessionManager.getPending(player.uniqueId)
        if (pending == null) {
            logger.warning("No pending session for ${player.name}")
            return
        }

        val challenge = Base64.getDecoder().decode(challengeB64)
        val clientSig = Base64.getDecoder().decode(clientSigB64)

        if (!challenge.contentEquals(pending.challenge)) {
            logger.warning("Challenge mismatch from ${player.name}")
            SessionManager.removePending(player.uniqueId)
            return
        }

        if (!Handshake.verify(challenge, clientSig, pending.clientPublicKey)) {
            logger.warning("Invalid signature from ${player.name} — rejecting")
            SessionManager.removePending(player.uniqueId)
            return
        }

        SessionManager.removePending(player.uniqueId)
        SessionManager.activate(player.uniqueId, pending.clientPublicKey)

        if (server.onlineMode && VenusConfig.cacheVerifiedUuid) {
            SessionManager.cacheUUID(player.uniqueId,
                Base64.getEncoder().encodeToString(pending.clientPublicKey.encoded))
        }

        logger.info("${player.name} authenticated successfully!")
        sendReady(player)
    }

    fun sendReady(player: Player) {
        val id = Identifier.fromNamespaceAndPath("venus", "ready")
        val packet = ClientboundCustomPayloadPacket(DiscardedPayload(id, ByteArray(0)))
        (player as CraftPlayer).handle.connection.send(packet)
        logger.info("Sent venus:ready to ${player.name}")
    }

    fun sendAuthChallengeTo(player: Player, challenge: ByteArray, serverSig: ByteArray) {
        sendAuthChallenge(player, challenge, serverSig)
    }
}