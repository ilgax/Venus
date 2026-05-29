package dev.ilgax.venus.gui

import dev.ilgax.venus.gui.tabs.ConsoleTab
import dev.ilgax.venus.gui.tabs.StatsTab
import dev.ilgax.venus.keybind.PanelKeybind
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class PanelScreen(
    private val sendConsoleCommand: (String) -> Unit,
    private val subscribeLogs: () -> Unit,
) : Screen(Component.translatable("screen.venus.panel")) {
    private var activeTab = PanelTab.OVERVIEW
    private var logsSubscribed = false
    private var consoleScrollOffset = 0
    private var selectedConsoleStart: Int? = null
    private var selectedConsoleEnd: Int? = null
    private var commandInput: EditBox? = null
    private val commandHistory = mutableListOf<String>()
    private var historyIndex: Int? = null
    private var lastMouseX = 0
    private var lastMouseY = 0
    private var draggingScrollbar = false

    override fun init() {
        commandInput =
            EditBox(font, 0, 0, 100, 18, Component.literal("Command")).also { input ->
                input.setMaxLength(256)
                input.setBordered(false)
                input.setSuggestion("")
                input.setTextColor(COLOR_TEXT)
                input.setTextColorUneditable(COLOR_MUTED)
                input.setVisible(false)
                addRenderableWidget(input)
            }
    }

    override fun render(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        lastMouseX = mouseX
        lastMouseY = mouseY

        guiGraphics.fill(0, 0, width, height, COLOR_BACKGROUND)

        val padding = panelPadding()
        val panelX = padding
        val panelY = padding
        val panelWidth = width - padding * 2
        val panelHeight = height - padding * 2

        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_PANEL)
        guiGraphics.renderOutline(panelX, panelY, panelWidth, panelHeight, COLOR_BORDER)

        guiGraphics.drawString(font, TITLE, panelX + 18, panelY + 12, COLOR_TEXT, false)
        guiGraphics.drawString(font, sessionLabel(), panelX + 18, panelY + 26, COLOR_MUTED, false)

        val navX = panelX + 12
        val navY = panelY + headerHeight()
        val navWidth = navWidth()
        val contentX = navX + navWidth + contentGap()
        val contentY = navY
        val contentWidth = panelX + panelWidth - contentX - 12
        val contentHeight = panelY + panelHeight - contentY - 12

        guiGraphics.fill(navX, navY, navX + navWidth, panelY + panelHeight - 12, COLOR_SIDEBAR)
        renderTab(guiGraphics, tabBounds(PanelTab.OVERVIEW), "Overview", PanelTab.OVERVIEW)
        renderTab(guiGraphics, tabBounds(PanelTab.CONSOLE), "Console", PanelTab.CONSOLE)

        guiGraphics.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, COLOR_CONTENT)
        guiGraphics.renderOutline(contentX, contentY, contentWidth, contentHeight, COLOR_BORDER)
        renderActiveTab(guiGraphics, contentX, contentY, contentWidth, contentHeight)

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen(): Boolean = false

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (PanelKeybind.matches(keyEvent, commandInput?.isFocused == true)) {
            onClose()
            return true
        }

        if (activeTab == PanelTab.CONSOLE && isCopy(keyEvent) && copySelectedConsoleLines()) {
            return true
        }

        if (activeTab == PanelTab.CONSOLE && commandInput?.isFocused == true && isScrollKey(keyEvent)) {
            navigateCommandHistory(keyEvent.key())
            return true
        }

        if (activeTab == PanelTab.CONSOLE && isScrollKey(keyEvent)) {
            scrollConsole(if (keyEvent.key() == GLFW.GLFW_KEY_UP) SCROLL_LINES else -SCROLL_LINES)
            return true
        }

        if (activeTab == PanelTab.CONSOLE && isEnter(keyEvent)) {
            submitCommand()
            return true
        }

        return super.keyPressed(keyEvent)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        val bounds = consoleBounds()
        if (
            activeTab == PanelTab.CONSOLE &&
            inside(mouseX.toInt(), mouseY.toInt(), bounds.x, bounds.y, bounds.width, bounds.height)
        ) {
            val direction = if (verticalAmount > 0) 1 else -1
            consoleScrollOffset =
                (consoleScrollOffset + direction * SCROLL_LINES)
                    .coerceIn(0, maxConsoleScroll(bounds.height))
            return true
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(
        mouseButtonEvent: MouseButtonEvent,
        doubleClick: Boolean,
    ): Boolean {
        val mouseX = mouseButtonEvent.x().toInt()
        val mouseY = mouseButtonEvent.y().toInt()
        val overviewTab = tabBounds(PanelTab.OVERVIEW)
        val consoleTab = tabBounds(PanelTab.CONSOLE)
        if (inside(mouseX, mouseY, overviewTab.x, overviewTab.y, overviewTab.width, overviewTab.height)) {
            activeTab = PanelTab.OVERVIEW
            updateInputVisibility()
            return true
        }
        if (inside(mouseX, mouseY, consoleTab.x, consoleTab.y, consoleTab.width, consoleTab.height)) {
            activeTab = PanelTab.CONSOLE
            ensureLogSubscription()
            updateInputVisibility()
            commandInput?.setFocused(true)
            setFocused(commandInput)
            return true
        }

        if (activeTab == PanelTab.CONSOLE && insideClearButton(mouseX, mouseY)) {
            SessionState.clearConsole()
            consoleScrollOffset = 0
            selectedConsoleStart = null
            selectedConsoleEnd = null
            return true
        }

        val console = consoleBounds()
        if (activeTab == PanelTab.CONSOLE && insideScrollbar(mouseX, mouseY, console)) {
            draggingScrollbar = true
            updateScrollFromMouse(mouseY, console)
            commandInput?.setFocused(false)
            setFocused(null)
            return true
        }

        if (activeTab == PanelTab.CONSOLE && inside(mouseX, mouseY, console.x, console.y, console.width, console.height)) {
            val lineIndex = consoleLineIndexAt(mouseY, console)
            selectedConsoleStart = lineIndex
            selectedConsoleEnd = lineIndex
            commandInput?.setFocused(false)
            setFocused(null)
            return true
        }

        return super.mouseClicked(mouseButtonEvent, doubleClick)
    }

    override fun mouseDragged(
        mouseButtonEvent: MouseButtonEvent,
        dragX: Double,
        dragY: Double,
    ): Boolean {
        val console = consoleBounds()
        if (activeTab == PanelTab.CONSOLE && draggingScrollbar) {
            updateScrollFromMouse(mouseButtonEvent.y().toInt(), console)
            return true
        }

        if (activeTab == PanelTab.CONSOLE && selectedConsoleStart != null) {
            selectedConsoleEnd = consoleLineIndexAt(mouseButtonEvent.y().toInt(), console)
            return true
        }

        return super.mouseDragged(mouseButtonEvent, dragX, dragY)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (draggingScrollbar) {
            draggingScrollbar = false
            return true
        }

        return super.mouseReleased(mouseButtonEvent)
    }

    private fun renderActiveTab(
        guiGraphics: GuiGraphics,
        contentX: Int,
        contentY: Int,
        contentWidth: Int,
        contentHeight: Int,
    ) {
        when (activeTab) {
            PanelTab.OVERVIEW -> {
                updateInputVisibility()
                StatsTab.render(guiGraphics, font, contentX + 16, contentY + 16, contentWidth - 32, contentHeight - 32)
            }

            PanelTab.CONSOLE -> {
                ensureLogSubscription()
                val input = commandInput
                if (input != null) {
                    input.setX(contentX + 30)
                    input.setY(contentY + contentHeight - 28)
                    input.setWidth(contentWidth - 46)
                    input.setHeight(18)
                }
                updateInputVisibility()
                guiGraphics.drawString(font, "Console", contentX + 16, contentY + 16, COLOR_TEXT, false)
                guiGraphics.drawString(font, consoleHint(), contentX + 16, contentY + 32, COLOR_MUTED, false)
                renderClearButton(guiGraphics)
                consoleScrollOffset = consoleScrollOffset.coerceIn(0, maxConsoleScroll(contentHeight - 90))
                ConsoleTab.render(
                    guiGraphics,
                    font,
                    contentX + 16,
                    contentY + 54,
                    contentWidth - 32,
                    contentHeight - 90,
                    consoleScrollOffset,
                    selectedConsoleStart,
                    selectedConsoleEnd,
                )
                renderCommandPrompt(guiGraphics, contentX + 20, contentY + contentHeight - 27)
            }
        }
    }

    private fun renderTab(
        guiGraphics: GuiGraphics,
        bounds: Bounds,
        label: String,
        tab: PanelTab,
    ) {
        if (activeTab == tab) {
            guiGraphics.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, COLOR_ACTIVE_TAB)
        }
        guiGraphics.drawString(font, label, bounds.x + 8, bounds.y + 6, if (activeTab == tab) COLOR_TEXT else COLOR_MUTED, false)
    }

    private fun sessionLabel(): String =
        if (SessionState.sessionActive) {
            "Connected"
        } else {
            "Offline or not authenticated"
        }

    private fun consoleHint(): String =
        if (SessionState.sessionActive) {
            "Live server console"
        } else {
            "Connect and authenticate to send commands"
        }

    private fun submitCommand() {
        val input = commandInput ?: return
        val command = input.value.trim()
        if (command.isEmpty() || !SessionState.sessionActive) return

        sendConsoleCommand(command)
        rememberCommand(command)
        input.setValue("")
        historyIndex = null
        consoleScrollOffset = 0
    }

    private fun rememberCommand(command: String) {
        if (commandHistory.lastOrNull() != command) {
            commandHistory.add(command)
            while (commandHistory.size > MAX_COMMAND_HISTORY) {
                commandHistory.removeAt(0)
            }
        }
    }

    private fun navigateCommandHistory(key: Int) {
        val input = commandInput ?: return
        if (commandHistory.isEmpty()) return

        historyIndex =
            when (key) {
                GLFW.GLFW_KEY_UP -> (historyIndex ?: commandHistory.size).let { (it - 1).coerceAtLeast(0) }
                GLFW.GLFW_KEY_DOWN -> {
                    val current = historyIndex ?: return
                    (current + 1).takeIf { it < commandHistory.size }
                }
                else -> historyIndex
            }

        input.setValue(historyIndex?.let(commandHistory::get) ?: "")
        input.moveCursorToEnd(false)
    }

    private fun ensureLogSubscription() {
        if (!logsSubscribed && SessionState.sessionActive) {
            subscribeLogs()
            logsSubscribed = true
        }
    }

    private fun updateInputVisibility() {
        commandInput?.setVisible(activeTab == PanelTab.CONSOLE)
    }

    private fun isEnter(keyEvent: KeyEvent): Boolean = keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER

    private fun isCopy(keyEvent: KeyEvent): Boolean =
        keyEvent.key() == GLFW.GLFW_KEY_C && keyEvent.modifiers() and GLFW.GLFW_MOD_CONTROL != 0

    private fun isScrollKey(keyEvent: KeyEvent): Boolean = keyEvent.key() == GLFW.GLFW_KEY_UP || keyEvent.key() == GLFW.GLFW_KEY_DOWN

    private fun scrollConsole(lines: Int) {
        consoleScrollOffset =
            (consoleScrollOffset + lines)
                .coerceIn(0, maxConsoleScroll(consoleBounds().height))
    }

    private fun insideScrollbar(
        mouseX: Int,
        mouseY: Int,
        bounds: Bounds,
    ): Boolean {
        val metrics = scrollbarMetrics(bounds) ?: return false
        return inside(mouseX, mouseY, metrics.x - 4, metrics.trackY, SCROLLBAR_HIT_WIDTH, metrics.trackHeight)
    }

    private fun updateScrollFromMouse(
        mouseY: Int,
        bounds: Bounds,
    ) {
        val metrics = scrollbarMetrics(bounds) ?: return
        val maxScroll = maxConsoleScroll(bounds.height)
        val centeredThumbY = (mouseY - metrics.trackY - metrics.thumbHeight / 2).coerceIn(0, metrics.maxThumbTravel)
        consoleScrollOffset = maxScroll - (centeredThumbY * maxScroll / metrics.maxThumbTravel.coerceAtLeast(1))
    }

    private fun renderClearButton(guiGraphics: GuiGraphics) {
        val button = clearButtonBounds()
        val x = button.x
        val y = button.y
        val hovered = inside(lastMouseX, lastMouseY, button.x, button.y, button.width, button.height)
        guiGraphics.fill(x, y, x + CLEAR_BUTTON_SIZE, y + CLEAR_BUTTON_SIZE, if (hovered) COLOR_BUTTON_HOVER else COLOR_BUTTON)
        guiGraphics.renderOutline(x, y, CLEAR_BUTTON_SIZE, CLEAR_BUTTON_SIZE, COLOR_BORDER)
        guiGraphics.fill(x + 5, y + 6, x + 13, y + 14, COLOR_TEXT)
        guiGraphics.fill(x + 4, y + 5, x + 14, y + 6, COLOR_TEXT)
        guiGraphics.fill(x + 7, y + 3, x + 11, y + 5, COLOR_TEXT)
        guiGraphics.fill(x + 7, y + 8, x + 8, y + 12, COLOR_BUTTON)
        guiGraphics.fill(x + 10, y + 8, x + 11, y + 12, COLOR_BUTTON)
    }

    private fun renderCommandPrompt(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
    ) {
        val promptY = y + (COMMAND_INPUT_HEIGHT - font.lineHeight) / 2
        guiGraphics.drawString(font, ">", x, promptY, COLOR_MUTED, false)
    }

    private fun insideClearButton(
        mouseX: Int,
        mouseY: Int,
    ): Boolean {
        val button = clearButtonBounds()
        return inside(mouseX, mouseY, button.x, button.y, button.width, button.height)
    }

    private fun clearButtonBounds(): Bounds {
        val panelPadding = panelPadding()
        val panelX = panelPadding
        val panelY = panelPadding
        val panelWidth = width - panelPadding * 2
        val panelHeight = height - panelPadding * 2
        val navX = panelX + 12
        val navY = panelY + headerHeight()
        val navWidth = navWidth()
        val contentX = navX + navWidth + contentGap()
        val contentY = navY
        val contentWidth = panelX + panelWidth - contentX - 12
        return Bounds(contentX + contentWidth - 34, contentY + 12, CLEAR_BUTTON_SIZE, CLEAR_BUTTON_SIZE)
    }

    private fun copySelectedConsoleLines(): Boolean {
        val start = selectedConsoleStart ?: return false
        val end = selectedConsoleEnd ?: start
        val lines = SessionState.consoleLines
        val from = minOf(start, end).coerceIn(0, lines.lastIndex)
        val to = maxOf(start, end).coerceIn(0, lines.lastIndex)
        if (lines.isEmpty()) return false

        minecraft.keyboardHandler.setClipboard(lines.subList(from, to + 1).joinToString(System.lineSeparator()))
        return true
    }

    private fun navBounds(): Bounds {
        val panelX = panelPadding()
        val panelY = panelPadding()
        val navX = panelX + 12
        val navY = panelY + headerHeight()
        return Bounds(navX, navY, navWidth())
    }

    private fun tabBounds(tab: PanelTab): Bounds {
        val nav = navBounds()
        val yOffset =
            when (tab) {
                PanelTab.OVERVIEW -> 8
                PanelTab.CONSOLE -> 34
            }
        return Bounds(nav.x + 8, nav.y + yOffset, nav.width - 16, TAB_HEIGHT)
    }

    private fun consoleBounds(): Bounds {
        val panelPadding = panelPadding()
        val panelX = panelPadding
        val panelY = panelPadding
        val panelWidth = width - panelPadding * 2
        val panelHeight = height - panelPadding * 2
        val navX = panelX + 12
        val navY = panelY + headerHeight()
        val contentX = navX + navWidth() + contentGap()
        val contentY = navY
        val contentWidth = panelX + panelWidth - contentX - 12
        val contentHeight = panelY + panelHeight - contentY - 12
        return Bounds(contentX + 16, contentY + 54, contentWidth - 32, contentHeight - 90)
    }

    private fun panelPadding(): Int = if (width < COMPACT_SCREEN_WIDTH || height < COMPACT_SCREEN_HEIGHT) 12 else 24

    private fun headerHeight(): Int = if (width < COMPACT_SCREEN_WIDTH || height < COMPACT_SCREEN_HEIGHT) 44 else 58

    private fun navWidth(): Int = if (width < COMPACT_SCREEN_WIDTH) 96 else 120

    private fun contentGap(): Int = if (width < COMPACT_SCREEN_WIDTH) 10 else 16

    private fun consoleLineIndexAt(
        mouseY: Int,
        bounds: Bounds,
    ): Int? {
        val lines = SessionState.consoleLines
        if (lines.isEmpty()) return null

        val visibleLines = maxOf(1, (bounds.height - 20) / (font.lineHeight + 2))
        val offset = consoleScrollOffset.coerceIn(0, maxOf(0, lines.size - visibleLines))
        val visibleEnd = lines.size - offset
        val visibleStart = maxOf(0, visibleEnd - visibleLines)
        val row = (mouseY - bounds.y - CONSOLE_PADDING) / (font.lineHeight + 2)
        if (row < 0) return null

        return (visibleStart + row).takeIf { it in visibleStart until visibleEnd }
    }

    private fun maxConsoleScroll(consoleHeight: Int): Int {
        val visibleLines = maxOf(1, (consoleHeight - 20) / (font.lineHeight + 2))
        return maxOf(0, SessionState.consoleLines.size - visibleLines)
    }

    private fun scrollbarMetrics(bounds: Bounds): ScrollbarMetrics? {
        val totalLines = SessionState.consoleLines.size
        val visibleLines = maxOf(1, (bounds.height - 20) / (font.lineHeight + 2))
        if (totalLines <= visibleLines) return null

        val trackHeight = bounds.height - 16
        val thumbHeight = maxOf(MIN_SCROLLBAR_THUMB_HEIGHT, trackHeight * visibleLines / totalLines)
        return ScrollbarMetrics(
            x = bounds.x + bounds.width - 8,
            trackY = bounds.y + 8,
            trackHeight = trackHeight,
            thumbHeight = thumbHeight,
            maxThumbTravel = trackHeight - thumbHeight,
        )
    }

    private fun inside(
        mouseX: Int,
        mouseY: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Boolean = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    private enum class PanelTab {
        OVERVIEW,
        CONSOLE,
    }

    private data class Bounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int = 0,
    )

    private data class ScrollbarMetrics(
        val x: Int,
        val trackY: Int,
        val trackHeight: Int,
        val thumbHeight: Int,
        val maxThumbTravel: Int,
    )

    private companion object {
        const val TAB_HEIGHT = 20
        const val SCROLL_LINES = 3
        const val MAX_COMMAND_HISTORY = 50
        const val CLEAR_BUTTON_SIZE = 18
        const val COMMAND_INPUT_HEIGHT = 18
        const val CONSOLE_PADDING = 10
        const val MIN_SCROLLBAR_THUMB_HEIGHT = 12
        const val SCROLLBAR_HIT_WIDTH = 10
        const val COMPACT_SCREEN_WIDTH = 900
        const val COMPACT_SCREEN_HEIGHT = 520
        const val COLOR_BACKGROUND = 0xFF101418.toInt()
        const val COLOR_PANEL = 0xFF151A20.toInt()
        const val COLOR_SIDEBAR = 0xFF0D1117.toInt()
        const val COLOR_CONTENT = 0xFF1B2028.toInt()
        const val COLOR_ACTIVE_TAB = 0xFF2F6F85.toInt()
        const val COLOR_BUTTON = 0xFF222B35.toInt()
        const val COLOR_BUTTON_HOVER = 0xFF334150.toInt()
        const val COLOR_BORDER = 0xFF5F6C7A.toInt()
        const val COLOR_TEXT = 0xFFF4F7FA.toInt()
        const val COLOR_MUTED = 0xFF9AA7B5.toInt()
        const val TITLE = "Venus"
    }
}
