package dev.xcyn.venus

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.messaging.PluginMessageListener

class VenusPlugin : JavaPlugin(), PluginMessageListener {

    override fun onEnable() {
        logger.info("Venus enabled")
        server.messenger.registerIncomingPluginChannel(this, "venus:hello", this)
    }

    override fun onDisable() {
        logger.info("Venus disabled")
        server.messenger.unregisterIncomingPluginChannel(this, "venus:hello")
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel == "venus:hello") {
            logger.info("Venus mod detected on client: ${player.name}")
        }
    }
}