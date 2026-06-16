package dev.ilgax.venus.channel

import dev.ilgax.venus.backend.BackendChannelHandler
import dev.ilgax.venus.backend.BackendIncomingChannel
import dev.ilgax.venus.handlers.AuthHandler
import dev.ilgax.venus.platform.toBackendPlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener

internal enum class IncomingChannel(
    val channel: String,
) {
    HELLO(BackendIncomingChannel.HELLO.channel),
    KEY(BackendIncomingChannel.KEY.channel),
    AUTH(BackendIncomingChannel.AUTH.channel),
    ERROR(BackendIncomingChannel.ERROR.channel),
    CMD(BackendIncomingChannel.CMD.channel),
    ;

    companion object {
        fun fromChannel(channel: String): IncomingChannel? =
            BackendIncomingChannel.fromChannel(channel)?.let { backend ->
                entries.firstOrNull { it.channel == backend.channel }
            }
    }
}

class ChannelListener(
    authHandler: AuthHandler,
    packetRouter: PacketRouter,
) : PluginMessageListener {
    private val delegate = BackendChannelHandler(authHandler.delegate, packetRouter.delegate)

    override fun onPluginMessageReceived(
        channel: String,
        player: Player,
        message: ByteArray,
    ) {
        delegate.handle(channel, player.toBackendPlayer(), message.toString(Charsets.UTF_8))
    }
}
