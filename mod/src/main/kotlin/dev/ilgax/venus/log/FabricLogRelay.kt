package dev.ilgax.venus.log

import dev.ilgax.venus.backend.BackendLogHandler
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender

class FabricLogRelay(
    private val delegate: BackendLogHandler,
) {
    private var appender: VenusLogAppender? = null

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
    }

    fun stop() {
        val currentAppender = appender ?: return
        val context = LogManager.getContext(false) as LoggerContext
        context.configuration.rootLogger.removeAppender(currentAppender.name)
        context.updateLoggers()
        currentAppender.stop()
        appender = null
    }

    fun flush() {
        delegate.flush()
    }

    private class VenusLogAppender(
        name: String,
        private val onLine: (String, String) -> Unit,
    ) : AbstractAppender(name, null, null, true) {
        override fun append(event: LogEvent) {
            onLine(event.loggerName, event.message.formattedMessage)
        }
    }

    companion object {
        const val APPENDER_NAME = "VenusFabricLogAppender"
    }
}
