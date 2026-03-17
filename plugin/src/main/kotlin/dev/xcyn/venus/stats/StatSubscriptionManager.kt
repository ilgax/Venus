package dev.xcyn.venus.stats

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object StatSubscriptionManager {

    private val tasks = ConcurrentHashMap<UUID, BukkitTask>()

    fun subscribe(
        uuid: UUID,
        stats: List<String>,
        intervalSeconds: Int,
        plugin: Plugin,
        sender: (String) -> Unit
    ) {
        cancel(uuid)
        val ticks = (intervalSeconds * 20L).coerceAtLeast(40L) // minimum 2 seconds
        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val json = StatsCollector.buildStatsJson(plugin.server, stats)
            sender(json)
        }, ticks, ticks)
        tasks[uuid] = task
    }

    fun cancel(uuid: UUID) {
        tasks.remove(uuid)?.cancel()
    }

    fun cancelAll() {
        tasks.values.forEach { it.cancel() }
        tasks.clear()
    }
}