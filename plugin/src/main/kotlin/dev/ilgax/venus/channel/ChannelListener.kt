package dev.ilgax.venus.channel

import dev.ilgax.venus.handlers.AuthHandler
import dev.ilgax.venus.protocol.VenusChannels
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener

internal enum class IncomingChannel(
    val channel: String,
) {
    HELLO(VenusChannels.HELLO),
    KEY(VenusChannels.KEY),
    AUTH(VenusChannels.AUTH),
    ERROR(VenusChannels.ERROR),
    CMD(VenusChannels.CMD),
    ;

    companion object {
        fun fromChannel(channel: String): IncomingChannel? = entries.firstOrNull { it.channel == channel }
    }
}

class ChannelListener(
    private val authHandler: AuthHandler,
    private val packetRouter: PacketRouter,
) : PluginMessageListener {
    override fun onPluginMessageReceived(
        channel: String,
        player: Player,
        message: ByteArray,
    ) {
        val data = message.toString(Charsets.UTF_8)
        when (IncomingChannel.fromChannel(channel)) {
            IncomingChannel.HELLO -> authHandler.handleHello(player)
            IncomingChannel.KEY -> authHandler.handleClientKey(player, data)
            IncomingChannel.AUTH -> authHandler.handleAuthResponse(player, data)
            IncomingChannel.ERROR -> authHandler.handleClientError(player, data)
            IncomingChannel.CMD -> packetRouter.handleCommand(player, data)
            null -> Unit
        }
    }
}
