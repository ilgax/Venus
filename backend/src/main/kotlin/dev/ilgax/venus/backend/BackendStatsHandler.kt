package dev.ilgax.venus.backend

import dev.ilgax.venus.protocol.StatGetPacket
import dev.ilgax.venus.protocol.StatSubscribePacket
import kotlinx.serialization.json.Json

class BackendStatsHandler(
    private val platform: BackendPlatform,
    private val json: Json,
    private val subscriptions: BackendStatSubscriptionManager,
) {
    fun handleSubscribe(
        player: BackendPlayer,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<StatSubscribePacket>(data)
            } catch (e: Exception) {
                platform.logger.warning("${player.name} sent malformed stat_subscribe packet: ${e.message}")
                return
            }
        subscriptions.subscribe(player.uuid, packet.stats, packet.intervalSeconds)
    }

    fun handleGet(
        player: BackendPlayer,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<StatGetPacket>(data)
            } catch (e: Exception) {
                platform.logger.warning("${player.name} sent malformed stat_get packet: ${e.message}")
                return
            }
        platform.sendData(player, platform.buildStatsJson(packet.stats))
    }
}
