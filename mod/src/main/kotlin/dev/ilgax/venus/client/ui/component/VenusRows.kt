package dev.ilgax.venus.client.ui.component

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusSpacing
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Player row — head, name, online status dot, op indicator. Rendered in a list
 * loop, no per-row widget. [render] draws into [bounds]; the caller passes
 * [hovered] and [selected].
 */
class VenusPlayerRow(
    val bounds: Bounds,
    val name: String,
    val uuid: String,
    val online: Boolean,
    val operator: Boolean,
) {
    fun render(
        g: GuiGraphics,
        font: Font,
        hovered: Boolean,
        selected: Boolean,
        showHeads: Boolean = true,
    ) {
        val bg =
            when {
                selected -> VenusTheme.ACTIVE
                hovered -> VenusTheme.HOVER
                else -> VenusTheme.SURFACE
            }
        VenusDraw.rect(g, bounds, bg)

        val head = VenusPlayerHead(bounds.x + 4, bounds.y + (bounds.height - VenusDimensions.PLAYER_HEAD_SIZE) / 2, uuid)
        head.render(g, showHeads)

        val nameX = bounds.x + VenusDimensions.PLAYER_HEAD_SIZE + 10
        val nameColor = if (online) VenusTheme.TEXT else VenusTheme.TEXT_MUTED
        VenusDraw.textTruncated(
            g,
            font,
            name,
            nameX,
            bounds.y + (bounds.height - font.lineHeight) / 2,
            bounds.width - (nameX - bounds.x) - 16,
            nameColor,
            false,
        )

        if (operator) {
            val opX = nameX + font.width(name) + 6
            VenusDraw.rect(g, opX, bounds.y + 4, 8, 8, VenusTheme.WARNING)
        }

        if (online) {
            val dotX = bounds.right - 10
            val dotY = bounds.y + (bounds.height - 6) / 2
            VenusDraw.statusDot(g, dotX, dotY, VenusTheme.SUCCESS)
        }
    }
}

/**
 * Event row for the dashboard's recent-events list: timestamp + message.
 */
class VenusEventRow(
    val bounds: Bounds,
    val timestamp: String,
    val message: String,
    val color: Int = VenusTheme.TEXT_MUTED,
) {
    fun render(
        g: GuiGraphics,
        font: Font,
    ) {
        val tsW = font.width(timestamp) + VenusSpacing.SM
        VenusDraw.text(g, font, timestamp, bounds.x, bounds.y + (bounds.height - font.lineHeight) / 2, VenusTheme.CONSOLE_TIMESTAMP, false)
        VenusDraw.textTruncated(
            g,
            font,
            message,
            bounds.x + tsW,
            bounds.y + (bounds.height - font.lineHeight) / 2,
            bounds.width - tsW,
            color,
            false,
        )
    }
}

/**
 * Single console line with semantic coloring by log level. Rendered in a
 * bounded loop — no permanent widget per line.
 */
class VenusConsoleLine(
    val bounds: Bounds,
    val timestamp: String,
    val level: String,
    val text: String,
    val logger: String = "",
    val message: String = "",
    val simpleLogger: Boolean = false,
) {
    fun render(
        g: GuiGraphics,
        font: Font,
        selected: Boolean = false,
    ) {
        if (selected) {
            VenusDraw.rect(g, bounds, VenusTheme.ACTIVE)
        }
        val textColor = if (level.isNotEmpty()) levelColor(level) else VenusTheme.CONSOLE_DEFAULT

        var x = bounds.x
        if (timestamp.isNotEmpty()) {
            VenusDraw.text(g, font, "[$timestamp]", x, bounds.y, VenusTheme.CONSOLE_TIMESTAMP, false)
            x += font.width("[$timestamp]") + VenusSpacing.SM
        }
        if (simpleLogger && logger.isNotEmpty()) {
            val simplified = ConsoleLineParser.simplifyLoggerName(logger)
            val sourceLabel =
                if (level.isNotEmpty()) {
                    "[$simplified/$level]"
                } else {
                    "[$simplified]"
                }
            VenusDraw.text(g, font, sourceLabel, x, bounds.y, VenusTheme.TEXT_MUTED, false)
            x += font.width(sourceLabel) + VenusSpacing.SM
            VenusDraw.textTruncated(g, font, message, x, bounds.y, bounds.right - x, textColor, false)
        } else {
            VenusDraw.textTruncated(g, font, text, x, bounds.y, bounds.right - x, textColor, false)
        }
    }

    private fun levelColor(level: String): Int =
        when (level.uppercase()) {
            "INFO" -> VenusTheme.CONSOLE_INFO
            "WARN", "WARNING" -> VenusTheme.CONSOLE_WARN
            "ERROR", "SEVERE" -> VenusTheme.CONSOLE_ERROR
            "DEBUG", "TRACE" -> VenusTheme.CONSOLE_DEBUG
            else -> VenusTheme.CONSOLE_DEFAULT
        }
}

/**
 * Console line parsing — pure, unit-testable. Extracts timestamp, logger name,
 * level, and message from a raw log line. Handles the bracketed formats Venus
 * already streams via Log4j appender batches.
 */
object ConsoleLineParser {
    data class Parsed(
        val timestamp: String,
        val level: String,
        val text: String,
        val logger: String = "",
        val message: String = "",
    )

    private val LEVELS = setOf("INFO", "WARN", "WARNING", "ERROR", "SEVERE", "DEBUG", "TRACE")
    private val TS_REGEX = Regex("""^\[(\d{2}:\d{2}:\d{2})\]\s+(.*)$""")
    private val LEVEL_ONLY_REGEX = Regex("""^\[(INFO|WARN|WARNING|ERROR|SEVERE|DEBUG|TRACE)\]\s*(.*)$""")
    private val BRACKET_REGEX = Regex("""^\[([^\]]+)\]\s*(.*)$""")
    private val LOGGER_LEVEL_REGEX = Regex("""^([^/]+)/((?:INFO|WARN|WARNING|ERROR|SEVERE|DEBUG|TRACE))$""")

    fun parse(raw: String): Parsed {
        val tsMatch = TS_REGEX.matchEntire(raw)
        if (tsMatch != null) {
            val (ts, rest) = tsMatch.destructured
            val bracketMatch = BRACKET_REGEX.matchEntire(rest)
            if (bracketMatch != null) {
                val (bracketContent, afterBracket) = bracketMatch.destructured
                val levelOnly = LEVEL_ONLY_REGEX.matchEntire("[$bracketContent] $afterBracket")
                if (levelOnly != null && bracketContent.uppercase() in LEVELS) {
                    return Parsed(ts, bracketContent.uppercase(), rest, "", afterBracket.trim())
                }
                val loggerLevel = LOGGER_LEVEL_REGEX.matchEntire(bracketContent)
                if (loggerLevel != null) {
                    val (loggerName, lvl) = loggerLevel.destructured
                    return Parsed(ts, lvl.uppercase(), rest, loggerName.trim(), afterBracket.trim())
                }
                val level = extractLevel(afterBracket)
                return Parsed(ts, level, rest, bracketContent.trim(), afterBracket.trim())
            }
            val level = extractLevel(rest)
            return Parsed(ts, level, rest, "", rest.trim())
        }
        val levelMatch = LEVEL_ONLY_REGEX.matchEntire(raw)
        if (levelMatch != null) {
            val (lvl, msg) = levelMatch.destructured
            return Parsed("", lvl.uppercase(), msg.trim(), "", msg.trim())
        }
        return Parsed("", "", raw, "", raw)
    }

    fun simplifyLoggerName(name: String): String {
        if (name.isEmpty() || '.' !in name) return name
        return name.substringAfterLast('.')
    }

    private fun extractLevel(rest: String): String {
        val upper = rest.uppercase()
        for (level in LEVELS) {
            if (upper.contains("[$level]") or upper.contains("/$level]") or upper.contains(" $level ") or upper.contains(" $level:")) {
                return level
            }
        }
        return ""
    }
}
