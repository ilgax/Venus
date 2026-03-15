package dev.xcyn.venus

import dev.xcyn.venus.auth.Handshake
import dev.xcyn.venus.auth.KeyManager
import dev.xcyn.venus.auth.PendingSession
import dev.xcyn.venus.auth.SessionManager
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.DiscardedPayload
import net.minecraft.resources.Identifier
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.Base64

class VenusPlugin : JavaPlugin(), PluginMessageListener {

    lateinit var keyManager: KeyManager

    override fun onEnable() {
        logger.info("Venus enabled")
        keyManager = KeyManager(dataFolder)
        keyManager.loadOrGenerate()
        logger.info("Server keypair loaded")

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
            logger.info("Empty client key from ${player.name}")
            return
        }
        val clientPublicKey = try {
            Handshake.decodePublicKey(clientPublicKeyBase64)
        } catch (e: Exception) {
            logger.info("Invalid client key from ${player.name}: ${e.message}")
            return
        }

        val challenge = Handshake.generateChallenge()
        val serverSig = Handshake.sign(challenge, keyManager.privateKey)

        SessionManager.addPending(
            player.uniqueId,
            PendingSession(clientPublicKey, challenge)
        )

        sendAuthChallenge(player, challenge, serverSig)
        logger.info("Sent auth challenge to ${player.name}")
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
        logger.info("${player.name} authenticated successfully!")
        sendReady(player)

        // TODO: send venus:ready, start session
    }

    private fun sendReady(player: Player) {
        val id = Identifier.fromNamespaceAndPath("venus", "ready")
        val packet = ClientboundCustomPayloadPacket(DiscardedPayload(id, ByteArray(0)))
        (player as CraftPlayer).handle.connection.send(packet)
        logger.info("Sent venus:ready to ${player.name}")
    }
}