package dev.ilgax.venus.backend

import dev.ilgax.venus.protocol.VenusChannels

enum class BackendIncomingChannel(
    val channel: String,
) {
    HELLO(VenusChannels.HELLO),
    KEY(VenusChannels.KEY),
    AUTH(VenusChannels.AUTH),
    ERROR(VenusChannels.ERROR),
    CMD(VenusChannels.CMD),
    ;

    companion object {
        fun fromChannel(channel: String): BackendIncomingChannel? = entries.firstOrNull { it.channel == channel }
    }
}

class BackendChannelHandler(
    private val authHandler: BackendAuthHandler,
    private val packetRouter: BackendPacketRouter,
) {
    fun handle(
        channel: String,
        player: BackendPlayer,
        data: String,
    ) {
        when (BackendIncomingChannel.fromChannel(channel)) {
            BackendIncomingChannel.HELLO -> authHandler.handleHello(player)
            BackendIncomingChannel.KEY -> authHandler.handleClientKey(player, data)
            BackendIncomingChannel.AUTH -> authHandler.handleAuthResponse(player, data)
            BackendIncomingChannel.ERROR -> authHandler.handleClientError(player, data)
            BackendIncomingChannel.CMD -> packetRouter.handleCommand(player, data)
            null -> Unit
        }
    }
}
