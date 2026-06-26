package dev.ilgax.venus.handlers

import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.backend.BackendLogHandler
import dev.ilgax.venus.backend.BackendPlatform
import dev.ilgax.venus.platform.PaperBackendPlatform
import dev.ilgax.venus.platform.toBackendPlayer
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class LogHandler {
    private val plugin: JavaPlugin
    internal val delegate: BackendLogHandler
    private var appender: VenusLogAppender? = null
    private var flushTask: BukkitTask? = null

    constructor(
        plugin: JavaPlugin,
        json: Json,
        sendData: (Player, String) -> Unit,
        sessionManager: SessionManager,
    ) {
        this.plugin = plugin
        delegate = BackendLogHandler(PaperBackendPlatform(plugin, sendDataPacket = sendData), json, sessionManager)
    }

    internal constructor(
        plugin: JavaPlugin,
        platform: BackendPlatform,
        json: Json,
        sessionManager: SessionManager,
    ) {
        this.plugin = plugin
        delegate = BackendLogHandler(platform, json, sessionManager)
    }

    internal constructor(
        plugin: JavaPlugin,
        delegate: BackendLogHandler,
    ) {
        this.plugin = plugin
        this.delegate = delegate
    }

    fun start() {
        val context = LogManager.getContext(false) as LoggerContext
        val rootLogger = context.configuration.rootLogger
        val newAppender =
            VenusLogAppender(APPENDER_NAME) { loggerName, message ->
                delegate.queueFormattedLine(delegate.formatLine(loggerName, message))
            }
        newAppender.start()
        rootLogger.addAppender(newAppender, Level.INFO, null)
        context.updateLoggers()
        appender = newAppender
        flushTask =
            plugin.server.scheduler.runTaskTimer(
                plugin,
                Runnable { delegate.flush() },
                BackendLogHandler.FLUSH_INTERVAL_TICKS,
                BackendLogHandler.FLUSH_INTERVAL_TICKS,
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
    }

    fun handleSubscribe(
        player: Player,
        data: String,
    ) = delegate.handleSubscribe(player.toBackendPlayer(), data)

    fun unsubscribe(uuid: UUID) = delegate.unsubscribe(uuid)

    fun suppressNextFor(
        uuid: UUID,
        marker: String,
    ) = delegate.suppressNextFor(uuid, marker)

    private class VenusLogAppender(
        name: String,
        private val onLine: (String, String) -> Unit,
    ) : AbstractAppender(name, null, null, true) {
        override fun append(event: LogEvent) {
            onLine(event.loggerName, event.message.formattedMessage)
        }
    }

    companion object {
        const val APPENDER_NAME = "VenusConsoleLogAppender"
    }
}
