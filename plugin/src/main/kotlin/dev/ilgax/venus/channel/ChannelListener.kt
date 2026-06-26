package dev.ilgax.venus.channel

import dev.ilgax.venus.backend.BackendChannelHandler
import dev.ilgax.venus.platform.toBackendPlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener

class ChannelListener(
    private val delegate: BackendChannelHandler,
) : PluginMessageListener {
    override fun onPluginMessageReceived(
        channel: String,
        player: Player,
        message: ByteArray,
    ) {
        delegate.handle(channel, player.toBackendPlayer(), message)
    }
}
