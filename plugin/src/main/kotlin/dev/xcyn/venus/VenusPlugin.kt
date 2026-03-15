package dev.xcyn.venus

import dev.xcyn.venus.auth.KeyManager
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.DiscardedPayload
import net.minecraft.resources.Identifier
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.messaging.PluginMessageListener

class VenusPlugin : JavaPlugin(), PluginMessageListener {

    lateinit var keyManager: KeyManager

    override fun onEnable() {
        logger.info("Venus enabled")
        keyManager = KeyManager(dataFolder)
        keyManager.loadOrGenerate()
        logger.info("Server keypair loaded")

        server.messenger.registerIncomingPluginChannel(this, "venus:hello", this)
        server.messenger.registerIncomingPluginChannel(this, "venus:key", this)
    }

    override fun onDisable() {
        logger.info("Venus disabled")
        server.messenger.unregisterIncomingPluginChannel(this, "venus:hello")
        server.messenger.unregisterIncomingPluginChannel(this, "venus:key")
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
        logger.info("Client key received from ${player.name}: $clientPublicKeyBase64")
    }
}