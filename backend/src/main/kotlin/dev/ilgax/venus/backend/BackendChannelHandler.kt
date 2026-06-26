package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.protocol.MAX_PACKET_SIZE
import dev.ilgax.venus.protocol.PRE_AUTH_RATE_LIMIT
import dev.ilgax.venus.protocol.PRE_AUTH_RATE_WINDOW_MS
import dev.ilgax.venus.protocol.VenusChannels
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
    private val sessionManager: SessionManager,
    private val logger: BackendLogger,
) {
    private val preAuthMessageTimes = ConcurrentHashMap<UUID, MutableList<Long>>()

    fun handle(
        channel: String,
        player: BackendPlayer,
        data: String,
    ) {
        try {
            if (data.length > MAX_PACKET_SIZE) {
                logger.warning("Oversized packet (${data.length} bytes) from ${player.name} on $channel - ignoring")
                return
            }

            val incomingChannel = BackendIncomingChannel.fromChannel(channel)
            if (incomingChannel == null) {
                logger.warning("Received packet on unknown channel: $channel from ${player.name}")
                return
            }

            val isPreAuth =
                incomingChannel == BackendIncomingChannel.HELLO ||
                    incomingChannel == BackendIncomingChannel.KEY ||
                    incomingChannel == BackendIncomingChannel.AUTH
            if (isPreAuth && !sessionManager.isActive(player.uuid) && isRateLimited(player.uuid)) {
                logger.warning("Rate-limited pre-auth packets from ${player.name} on $channel - ignoring")
                return
            }

            when (incomingChannel) {
                BackendIncomingChannel.HELLO -> authHandler.handleHello(player)
                BackendIncomingChannel.KEY -> authHandler.handleClientKey(player, data)
                BackendIncomingChannel.AUTH -> authHandler.handleAuthResponse(player, data)
                BackendIncomingChannel.ERROR -> authHandler.handleClientError(player, data)
                BackendIncomingChannel.CMD -> packetRouter.handleCommand(player, data)
            }
        } catch (e: Throwable) {
            logger.warning("Unexpected error handling packet on $channel from ${player.name}: ${e.message}")
        }
    }

    fun cleanupPlayer(uuid: UUID) {
        preAuthMessageTimes.remove(uuid)
    }

    private fun isRateLimited(uuid: UUID): Boolean {
        val now = System.currentTimeMillis()
        val times = preAuthMessageTimes.computeIfAbsent(uuid) { CopyOnWriteArrayList() }
        times.add(now)
        times.removeAll { it < now - PRE_AUTH_RATE_WINDOW_MS }
        return times.size > PRE_AUTH_RATE_LIMIT
    }
}
