package dev.ilgax.venus.handlers

import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.protocol.ConsoleLogPacket
import dev.ilgax.venus.protocol.ConsoleLogSubscribePacket
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class LogHandler(
    private val plugin: JavaPlugin,
    private val json: Json,
    private val sendData: (Player, String) -> Unit,
) {
    private data class LogLine(
        val text: String,
        val hiddenFrom: UUID? = null,
    )

    private val queue = ConcurrentLinkedQueue<LogLine>()
    private val subscribers = ConcurrentHashMap.newKeySet<UUID>()
    private val suppressedMarkers = ConcurrentHashMap<UUID, String>()
    private var appender: VenusLogAppender? = null
    private var flushTask: BukkitTask? = null

    fun start() {
        val context = LogManager.getContext(false) as LoggerContext
        val rootLogger = context.configuration.rootLogger
        val newAppender = VenusLogAppender(APPENDER_NAME, ::queueLine)
        newAppender.start()
        rootLogger.addAppender(newAppender, Level.INFO, null)
        context.updateLoggers()
        appender = newAppender

        flushTask =
            plugin.server.scheduler.runTaskTimer(
                plugin,
                Runnable { flush() },
                FLUSH_INTERVAL_TICKS,
                FLUSH_INTERVAL_TICKS,
            )
    }

    fun stop() {
        flushTask?.cancel()
        flushTask = null

        val currentAppender = appender ?: return
        val context = LogManager.getContext(false) as LoggerContext
        context.configuration.rootLogger.removeAppender(currentAppender.name)
        context.updateLoggers()
        currentAppender.stop()
        appender = null
        subscribers.clear()
        suppressedMarkers.clear()
        queue.clear()
    }

    fun handleSubscribe(
        player: Player,
        data: String,
    ) {
        val packet =
            try {
                json.decodeFromString<ConsoleLogSubscribePacket>(data)
            } catch (e: SerializationException) {
                plugin.logger.warning("${player.name} sent malformed log_subscribe packet: ${e.message}")
                return
            }
        if (packet.type != "log_subscribe") {
            plugin.logger.warning("${player.name} sent invalid log_subscribe packet type: ${packet.type}")
            return
        }

        subscribers.add(player.uniqueId)
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

    private fun queueLine(line: String) {
        if (subscribers.isEmpty()) return
        queue.add(LogLine(line, hiddenFrom = suppressedPlayer(line)))
        while (queue.size > MAX_QUEUE_LINES) {
            queue.poll()
        }
    }

    private fun suppressedPlayer(line: String): UUID? {
        val suppressed =
            suppressedMarkers.entries.firstOrNull { (_, marker) ->
                line.contains(marker)
            } ?: return null
        suppressedMarkers.remove(suppressed.key)
        return suppressed.key
    }

    private fun flush() {
        if (subscribers.isEmpty()) {
            queue.clear()
            return
        }

        val lines = mutableListOf<LogLine>()
        while (lines.size < MAX_LINES_PER_PACKET) {
            lines.add(queue.poll() ?: break)
        }
        if (lines.isEmpty()) return

        subscribers.removeIf { uuid ->
            val player = plugin.server.getPlayer(uuid)
            if (player == null || !SessionManager.isActive(uuid)) {
                true
            } else {
                val visibleLines =
                    lines
                        .filter { it.hiddenFrom != uuid }
                        .map { it.text }
                if (visibleLines.isNotEmpty()) {
                    sendData(player, encode(visibleLines))
                }
                false
            }
        }
    }

    private fun encode(lines: List<String>): String =
        json.encodeToString(
            ConsoleLogPacket.serializer(),
            ConsoleLogPacket(type = "console_log", lines = lines),
        )

    private class VenusLogAppender(
        name: String,
        private val onLine: (String) -> Unit,
    ) : AbstractAppender(name, null, null, true) {
        override fun append(event: LogEvent) {
            val line =
                "[${TIME_FORMAT.format(Instant.ofEpochMilli(event.timeMillis))} ${event.level.name()}]: " +
                    event.message.formattedMessage
            line.lineSequence().forEach { onLine(it) }
        }
    }

    private companion object {
        const val APPENDER_NAME = "VenusConsoleLogAppender"
        const val FLUSH_INTERVAL_TICKS = 20L
        const val MAX_LINES_PER_PACKET = 100
        const val MAX_QUEUE_LINES = 1_000

        val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter
                .ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault())
    }
}
