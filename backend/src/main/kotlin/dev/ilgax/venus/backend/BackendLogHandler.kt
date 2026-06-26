package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.protocol.ConsoleLogPacket
import dev.ilgax.venus.protocol.ConsoleLogSubscribePacket
import dev.ilgax.venus.protocol.LogSanitizer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class BackendLogHandler(
    private val platform: BackendPlatform,
    private val json: Json,
    private val sessionManager: SessionManager,
) {
    private val queue = ConcurrentLinkedQueue<LogLine>()
    private val queueSize = AtomicInteger(0)
    private val subscribers = ConcurrentHashMap<UUID, Boolean>()
    private val suppressedMarkers = ConcurrentHashMap<UUID, String>()

    fun handleSubscribe(
        player: BackendPlayer,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<ConsoleLogSubscribePacket>(data)
            } catch (e: Exception) {
                platform.logger.warning("${player.name} sent malformed log_subscribe packet: ${e.message}")
                return
            }
        if (packet.type != "log_subscribe") {
            platform.logger.warning("${player.name} sent invalid log_subscribe packet type: ${packet.type}")
            return
        }
        subscribers[player.uuid] = true
    }

    fun unsubscribe(uuid: UUID) {
        subscribers.remove(uuid)
        suppressedMarkers.remove(uuid)
    }

    fun suppressNextFor(
        uuid: UUID,
        marker: String,
    ) {
        suppressedMarkers[uuid] = marker
    }

    fun queueFormattedLine(line: String) {
        val suppressed =
            synchronized(suppressedMarkers) {
                val entry = suppressedMarkers.entries.firstOrNull { (_, marker) -> line.contains(marker) }
                if (entry != null) {
                    suppressedMarkers.remove(entry.key)
                    entry.key
                } else {
                    null
                }
            }
        queue.add(LogLine(line, suppressed))
        queueSize.incrementAndGet()
        while (queueSize.get() > MAX_QUEUE_LINES) {
            if (queue.poll() != null) queueSize.decrementAndGet() else break
        }
    }

    fun formatLine(
        loggerName: String,
        message: String,
        instant: Instant = Instant.now(),
    ): String = "[${TIME_FORMAT.format(instant)}] [$loggerName] ${LogSanitizer.sanitize(message)}"

    fun flush() {
        if (subscribers.isEmpty() || queue.isEmpty()) return
        val lines = mutableListOf<LogLine>()
        var drained = 0
        while (drained < MAX_LINES_PER_PACKET) {
            val line = queue.poll() ?: break
            queueSize.decrementAndGet()
            lines.add(line)
            drained++
        }
        if (lines.isEmpty()) return
        subscribers.keys.forEach { uuid ->
            val player = platform.player(uuid)
            if (player == null || !sessionManager.isActive(uuid)) {
                unsubscribe(uuid)
                return@forEach
            }
            val visibleLines = lines.filter { it.hiddenFrom != uuid }.map { it.text }
            if (visibleLines.isNotEmpty()) {
                platform.sendData(player, encode(visibleLines))
            }
        }
    }

    private fun encode(lines: List<String>): String =
        json.encodeToString(
            ConsoleLogPacket.serializer(),
            ConsoleLogPacket(type = "console_log", lines = lines),
        )

    private data class LogLine(
        val text: String,
        val hiddenFrom: UUID?,
    )

    companion object {
        const val FLUSH_INTERVAL_TICKS = 20L
        private const val MAX_LINES_PER_PACKET = 50
        private const val MAX_QUEUE_LINES = 1000
        private val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter
                .ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault())
    }
}
