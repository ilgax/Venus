package dev.ilgax.venus.handlers

import dev.ilgax.venus.protocol.StatGetPacket
import dev.ilgax.venus.protocol.StatSubscribePacket
import dev.ilgax.venus.stats.StatSubscriptionManager
import dev.ilgax.venus.stats.StatsCollector
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class StatsHandler(
    private val plugin: JavaPlugin,
    private val json: Json,
    private val sendData: (Player, String) -> Unit,
) {
    fun handleSubscribe(
        player: Player,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<StatSubscribePacket>(data)
            } catch (e: SerializationException) {
                plugin.logger.warning("${player.name} sent malformed stat_subscribe packet: ${e.message}")
                return
            }
        plugin.logger.info("${player.name} subscribed to stats: ${packet.stats} every ${packet.intervalSeconds}s")
        StatSubscriptionManager.subscribe(player.uniqueId, packet.stats, packet.intervalSeconds, plugin) { statsJson ->
            sendData(player, statsJson)
        }
    }

    fun handleGet(
        player: Player,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<StatGetPacket>(data)
            } catch (e: SerializationException) {
                plugin.logger.warning("${player.name} sent malformed stat_get packet: ${e.message}")
                return
            }
        val statsJson = StatsCollector.buildStatsJson(plugin.server, packet.stats)
        sendData(player, statsJson)
        plugin.logger.info("${player.name} requested one-time stats: ${packet.stats}")
    }
}
