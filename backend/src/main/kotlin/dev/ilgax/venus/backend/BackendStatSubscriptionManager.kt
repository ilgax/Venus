package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.SessionManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BackendStatSubscriptionManager(
    private val platform: BackendPlatform,
    private val sessionManager: SessionManager,
) {
    private val tasks = ConcurrentHashMap<UUID, BackendTask>()

    fun subscribe(
        uuid: UUID,
        stats: List<String>,
        intervalSeconds: Int,
    ) {
        cancel(uuid)
        val intervalTicks = intervalSeconds.coerceIn(2, 300) * 20L
        tasks[uuid] =
            platform.scheduler.runRepeating(intervalTicks, intervalTicks) {
                if (!sessionManager.isActive(uuid)) {
                    cancel(uuid)
                    return@runRepeating
                }
                val player = platform.player(uuid) ?: return@runRepeating
                val statsJson = platform.buildStatsJson(stats)
                platform.sendData(player, statsJson)
            }
    }

    fun cancel(uuid: UUID) {
        tasks.remove(uuid)?.cancel()
    }

    fun cancelAll() {
        tasks.values.forEach { it.cancel() }
        tasks.clear()
    }
}
