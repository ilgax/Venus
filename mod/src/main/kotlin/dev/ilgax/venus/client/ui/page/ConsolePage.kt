package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.client.ui.component.ConsoleLineParser
import dev.ilgax.venus.client.ui.component.VenusConsoleLine
import dev.ilgax.venus.client.ui.component.VenusEmptyState
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusSpacing
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.ScissorStack
import dev.ilgax.venus.client.ui.render.VenusDraw
import dev.ilgax.venus.client.ui.widget.VenusTextField
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import org.lwjgl.glfw.GLFW

/**
 * Console page. Scrollable console output with timestamp, log level, colored
 * semantic categories. Command input uses native [VenusTextField] (EditBox
 * wrapper) preserving clipboard/selection/Unicode. Command history, auto-scroll
 * toggle, clear, pause.
 *
 * Efficient history handling: no permanent widget per line. Lines are rendered
 * in a bounded loop from [SessionState.consoleLines] (already capped at 500).
 * The [historyLimit] setting can further reduce the visible window.
 */
class ConsolePage(
    private val sendConsoleCommand: (String) -> Unit,
    private val subscribeLogs: () -> Unit,
    private val historyLimit: () -> Int = { 500 },
) : VenusPageContract {
    private var contentBounds: Bounds = Bounds(0, 0, 0, 0)
    private var font: Font? = null
    private var inputField: VenusTextField? = null
    private var logsSubscribed = false
    private var scrollOffset = 0
    private var autoScroll = true
    private var paused = false
    private var selectedStart: Int? = null
    private var selectedEnd: Int? = null
    private var draggingScrollbar = false
    private val commandHistory = mutableListOf<String>()
    private var historyIndex: Int? = null
    private var clearBtnBounds: Bounds = Bounds(0, 0, 0, 0)
    private var pauseBtnBounds: Bounds = Bounds(0, 0, 0, 0)
    private var autoScrollBtnBounds: Bounds = Bounds(0, 0, 0, 0)
    private var sourceBtnBounds: Bounds = Bounds(0, 0, 0, 0)
    private var simpleLogger = true

    override fun layout(contentBounds: Bounds) {
        this.contentBounds = contentBounds
        val pad = VenusDimensions.CONTENT_PADDING
        val inner = contentBounds.inset(pad)
        val inputH = VenusDimensions.INPUT_HEIGHT
        val inputY = inner.bottom - inputH
        inputField?.layout(Bounds(inner.x + 16, inputY, inner.width - 16, inputH))

        val f =
            font ?: net.minecraft.client.Minecraft
                .getInstance()
                .font
        val btnH = VenusDimensions.BUTTON_HEIGHT
        val btnY = inner.y
        var bx = inner.right
        val clearW = f.width("Clear") + 16
        bx -= clearW
        clearBtnBounds = Bounds(bx, btnY, clearW, btnH)
        bx -= VenusSpacing.SM
        val pauseLabel = if (paused) "Resume" else "Pause"
        val pauseW = f.width(pauseLabel) + 16
        bx -= pauseW
        pauseBtnBounds = Bounds(bx, btnY, pauseW, btnH)
        bx -= VenusSpacing.SM
        val autoLabel = if (autoScroll) "Auto" else "Manual"
        val autoW = f.width(autoLabel) + 16
        bx -= autoW
        autoScrollBtnBounds = Bounds(bx, btnY, autoW, btnH)
        bx -= VenusSpacing.SM
        val sourceLabel = if (simpleLogger) "Simple" else "Full"
        val sourceW = f.width(sourceLabel) + 16
        bx -= sourceW
        sourceBtnBounds = Bounds(bx, btnY, sourceW, btnH)
    }

    fun inputField(): VenusTextField? {
        if (inputField == null) {
            inputField =
                VenusTextField(
                    font ?: net.minecraft.client.Minecraft
                        .getInstance()
                        .font,
                    0,
                    0,
                    100,
                    placeholder = "Enter command...",
                )
        }
        return inputField
    }

    fun detachInput() {
        inputField?.setVisible(false)
    }

    override fun onEnter() {
        inputField?.setVisible(true)
        inputField?.setFocused(true)
        if (!logsSubscribed && SessionState.sessionActive) {
            subscribeLogs()
            logsSubscribed = true
        }
    }

    override fun onLeave() {
        inputField?.setVisible(false)
        inputField?.setFocused(false)
    }

    override fun render(
        g: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        this.font = font
        val pad = VenusDimensions.CONTENT_PADDING
        val inner = contentBounds.inset(pad)

        renderHeader(g, font, inner)
        renderLabelButton(g, font, clearBtnBounds, "Clear", mouseX, mouseY)
        renderLabelButton(g, font, pauseBtnBounds, if (paused) "Resume" else "Pause", mouseX, mouseY)
        renderLabelButton(g, font, autoScrollBtnBounds, if (autoScroll) "Auto" else "Manual", mouseX, mouseY)
        renderLabelButton(g, font, sourceBtnBounds, if (simpleLogger) "Simple" else "Full", mouseX, mouseY)

        val inputH = VenusDimensions.INPUT_HEIGHT
        val consoleBounds = Bounds(inner.x, inner.y + 24, inner.width, inner.height - 24 - inputH - VenusSpacing.SM)
        renderConsole(g, font, consoleBounds, mouseX, mouseY)

        inputField?.renderBackground(g, mouseX, mouseY)
        val promptY = inner.bottom - inputH + (inputH - font.lineHeight) / 2
        VenusDraw.text(g, font, ">", inner.x + 4, promptY, VenusTheme.TEXT_MUTED, false)
    }

    private fun renderHeader(
        g: GuiGraphics,
        font: Font,
        inner: Bounds,
    ) {
        VenusDraw.text(g, font, "Console", inner.x, inner.y + 4, VenusTheme.TEXT, false)
        val hint = if (SessionState.sessionActive) "Live server console" else "Authenticate to send commands"
        VenusDraw.text(g, font, hint, inner.x + font.width("Console") + VenusSpacing.LG, inner.y + 4, VenusTheme.TEXT_MUTED, false)
    }

    private fun renderLabelButton(
        g: GuiGraphics,
        font: Font,
        bounds: Bounds,
        label: String,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (bounds.width == 0) return
        val hovered = bounds.contains(mouseX, mouseY)
        VenusDraw.rect(g, bounds, if (hovered) VenusTheme.HOVER else VenusTheme.RAISED)
        VenusDraw.border(g, bounds, VenusTheme.BORDER_BRIGHT)
        VenusDraw.textCentered(g, font, label, bounds, VenusTheme.TEXT, false)
    }

    private fun renderConsole(
        g: GuiGraphics,
        font: Font,
        bounds: Bounds,
        mouseX: Int,
        mouseY: Int,
    ) {
        VenusDraw.rect(g, bounds, VenusTheme.SURFACE)
        VenusDraw.border(g, bounds, VenusTheme.BORDER)

        val lines = visibleLines()
        val lineH = font.lineHeight + 2
        val visibleCount = (bounds.height / lineH).coerceAtLeast(1)
        val maxScroll = (lines.size - visibleCount).coerceAtLeast(0)

        if (autoScroll && !paused && !draggingScrollbar) {
            scrollOffset = maxScroll
        }
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        if (lines.isEmpty()) {
            VenusEmptyState(bounds).run {
                message = if (SessionState.sessionActive) "Waiting for logs..." else "Authenticate to view console"
                render(g, font)
            }
            return
        }

        val first = scrollOffset
        val last = minOf(lines.size, scrollOffset + visibleCount + 1)

        ScissorStack.with(g, bounds) {
            for (i in first until last) {
                val y = bounds.y + (i - scrollOffset) * lineH
                if (y + lineH < bounds.y || y > bounds.bottom) continue
                val parsed = ConsoleLineParser.parse(lines[i])
                val lineBounds = Bounds(bounds.x + 2, y, bounds.width - 4, lineH)
                val selected = selectedStart != null && i in selectionRange()
                VenusConsoleLine(
                    lineBounds,
                    parsed.timestamp,
                    parsed.level,
                    parsed.text,
                    parsed.logger,
                    parsed.message,
                    simpleLogger,
                ).render(g, font, selected)
            }
        }

        renderScrollbar(g, bounds, lines.size, visibleCount, maxScroll, mouseY)
    }

    private fun renderScrollbar(
        g: GuiGraphics,
        bounds: Bounds,
        total: Int,
        visible: Int,
        maxScroll: Int,
        mouseY: Int,
    ) {
        if (total <= visible) return
        val trackH = bounds.height - 8
        val thumbH = (trackH * visible / total).coerceAtLeast(VenusDimensions.SCROLLBAR_THUMB_MIN)
        val thumbY = bounds.y + 4 + (if (maxScroll == 0) 0 else (trackH - thumbH) * scrollOffset / maxScroll)
        val sbX = bounds.right - VenusDimensions.SCROLLBAR_WIDTH - 2
        VenusDraw.rect(g, sbX, bounds.y + 4, VenusDimensions.SCROLLBAR_WIDTH, trackH, VenusTheme.RAISED)
        VenusDraw.rect(
            g,
            sbX,
            thumbY,
            VenusDimensions.SCROLLBAR_WIDTH,
            thumbH,
            if (draggingScrollbar) VenusTheme.ACCENT else VenusTheme.TEXT_MUTED,
        )
    }

    private fun visibleLines(): List<String> {
        val all = SessionState.consoleLines
        val limit = historyLimit()
        return if (all.size > limit) {
            all.takeLast(limit)
        } else {
            all
        }
    }

    private fun selectionRange(): IntRange {
        val s = selectedStart ?: return -1..-1
        val e = selectedEnd ?: s
        return minOf(s, e)..maxOf(s, e)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean {
        val pad = VenusDimensions.CONTENT_PADDING
        val inner = contentBounds.inset(pad)
        val inputH = VenusDimensions.INPUT_HEIGHT
        val bounds = Bounds(inner.x, inner.y + 24, inner.width, inner.height - 24 - inputH - VenusSpacing.SM)
        if (!bounds.contains(mouseX.toInt(), mouseY.toInt())) return false
        val lines = visibleLines()
        val lineH = (
            net.minecraft.client.Minecraft
                .getInstance()
                .font.lineHeight + 2
        )
        val visibleCount = (bounds.height / lineH).coerceAtLeast(1)
        val maxScroll = (lines.size - visibleCount).coerceAtLeast(0)
        val dir = if (scrollY > 0) -1 else 1
        scrollOffset = (scrollOffset + dir * VenusDimensions.SCROLL_LINES).coerceIn(0, maxScroll)
        autoScroll = false
        return true
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        if (button != 0) return false
        val pad = VenusDimensions.CONTENT_PADDING
        val inner = contentBounds.inset(pad)

        if (clearBtnBounds.contains(mouseX, mouseY)) {
            SessionState.clearConsole()
            scrollOffset = 0
            selectedStart = null
            selectedEnd = null
            return true
        }
        if (pauseBtnBounds.contains(mouseX, mouseY)) {
            paused = !paused
            return true
        }
        if (autoScrollBtnBounds.contains(mouseX, mouseY)) {
            autoScroll = !autoScroll
            return true
        }
        if (sourceBtnBounds.contains(mouseX, mouseY)) {
            simpleLogger = !simpleLogger
            return true
        }

        val inputH = VenusDimensions.INPUT_HEIGHT
        val bounds = Bounds(inner.x, inner.y + 24, inner.width, inner.height - 24 - inputH - VenusSpacing.SM)
        if (bounds.contains(mouseX, mouseY)) {
            val lines = visibleLines()
            val lineH = (
                net.minecraft.client.Minecraft
                    .getInstance()
                    .font.lineHeight + 2
            )
            val idx = scrollOffset + (mouseY.toInt() - bounds.y) / lineH
            if (idx in lines.indices) {
                selectedStart = idx
                selectedEnd = idx
            }
            inputField?.setFocused(false)
            return true
        }

        if (inputField?.bounds?.contains(mouseX, mouseY) == true) {
            inputField?.setFocused(true)
            return true
        }
        return false
    }

    fun keyPressed(
        keyCode: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean {
        val input = inputField ?: return false
        if (input.isFocused) {
            when (keyCode) {
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    submitCommand()
                    return true
                }
                GLFW.GLFW_KEY_UP -> {
                    navigateHistory(-1)
                    return true
                }
                GLFW.GLFW_KEY_DOWN -> {
                    navigateHistory(1)
                    return true
                }
            }
        }
        if (keyCode == GLFW.GLFW_KEY_C && (modifiers and GLFW.GLFW_MOD_CONTROL) != 0) {
            copySelection()
            return true
        }
        return false
    }

    private fun submitCommand() {
        val input = inputField ?: return
        val cmd = input.value.trim()
        if (cmd.isEmpty() || !SessionState.sessionActive) return
        sendConsoleCommand(cmd)
        if (commandHistory.lastOrNull() != cmd) {
            commandHistory.add(cmd)
            while (commandHistory.size > VenusDimensions.MAX_COMMAND_HISTORY) commandHistory.removeAt(0)
        }
        input.clear()
        historyIndex = null
        scrollOffset = 0
    }

    private fun navigateHistory(direction: Int) {
        if (commandHistory.isEmpty()) return
        val input = inputField ?: return
        historyIndex =
            when {
                historyIndex == null && direction < 0 -> commandHistory.lastIndex
                historyIndex == null -> 0
                else -> (historyIndex!! + direction).coerceIn(0, commandHistory.lastIndex)
            }
        input.setValue(commandHistory[historyIndex!!])
        input.moveCursorToEnd()
    }

    private fun copySelection() {
        val range = selectionRange()
        if (range.first < 0) return
        val lines = visibleLines()
        if (lines.isEmpty()) return
        val from = range.first.coerceIn(0, lines.lastIndex)
        val to = range.last.coerceIn(0, lines.lastIndex)
        net.minecraft.client.Minecraft
            .getInstance()
            .keyboardHandler
            .setClipboard(lines.subList(from, to + 1).joinToString("\n"))
    }
}
