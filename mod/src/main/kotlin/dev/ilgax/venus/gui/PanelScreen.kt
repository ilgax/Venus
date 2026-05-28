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
        guiGraphics.fill(0, 0, width, height, COLOR_BACKGROUND)

        val panelX = 24
        val panelY = 24
        val panelWidth = width - 48
        val panelHeight = height - 48

        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_PANEL)
        guiGraphics.renderOutline(panelX, panelY, panelWidth, panelHeight, COLOR_BORDER)

        guiGraphics.drawString(font, TITLE, panelX + 18, panelY + 16, COLOR_TEXT, false)
        guiGraphics.drawString(font, sessionLabel(), panelX + 18, panelY + 32, COLOR_MUTED, false)

        val navX = panelX + 12
        val navY = panelY + 58
        val navWidth = 120
        val contentX = navX + navWidth + 16
        val contentY = navY
        val contentWidth = panelX + panelWidth - contentX - 12
        val contentHeight = panelY + panelHeight - contentY - 12

        guiGraphics.fill(navX, navY, navX + navWidth, panelY + panelHeight - 12, COLOR_SIDEBAR)
        renderTab(guiGraphics, navX + 8, navY + 8, navWidth - 16, "Overview", PanelTab.OVERVIEW)
        renderTab(guiGraphics, navX + 8, navY + 34, navWidth - 16, "Console", PanelTab.CONSOLE)

        guiGraphics.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, COLOR_CONTENT)
        guiGraphics.renderOutline(contentX, contentY, contentWidth, contentHeight, COLOR_BORDER)
        renderActiveTab(guiGraphics, contentX, contentY, contentWidth, contentHeight)

        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderCommandPlaceholder(guiGraphics)
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
        val nav = navBounds()
        val mouseX = mouseButtonEvent.x().toInt()
        val mouseY = mouseButtonEvent.y().toInt()
        if (inside(mouseX, mouseY, nav.x + 8, nav.y + 8, nav.width - 16, TAB_HEIGHT)) {
            activeTab = PanelTab.OVERVIEW
            updateInputVisibility()
            return true
        }
        if (inside(mouseX, mouseY, nav.x + 8, nav.y + 34, nav.width - 16, TAB_HEIGHT)) {
            activeTab = PanelTab.CONSOLE
            ensureLogSubscription()
            updateInputVisibility()
            commandInput?.setFocused(true)
            setFocused(commandInput)
            return true
        }

        val console = consoleBounds()
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
        if (activeTab == PanelTab.CONSOLE && selectedConsoleStart != null) {
            selectedConsoleEnd = consoleLineIndexAt(mouseButtonEvent.y().toInt(), console)
            return true
        }

        return super.mouseDragged(mouseButtonEvent, dragX, dragY)
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
                guiGraphics.drawString(font, "Overview", contentX + 16, contentY + 16, COLOR_TEXT, false)
                guiGraphics.drawString(font, overviewHint(), contentX + 16, contentY + 32, COLOR_MUTED, false)
                StatsTab.render(guiGraphics, font, contentX + 16, contentY + 54, contentWidth - 32)
            }

            PanelTab.CONSOLE -> {
                ensureLogSubscription()
                val input = commandInput
                if (input != null) {
                    input.setX(contentX + 16)
                    input.setY(contentY + contentHeight - 28)
                    input.setWidth(contentWidth - 32)
                    input.setHeight(18)
                }
                updateInputVisibility()
                guiGraphics.drawString(font, "Console", contentX + 16, contentY + 16, COLOR_TEXT, false)
                guiGraphics.drawString(font, consoleHint(), contentX + 16, contentY + 32, COLOR_MUTED, false)
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
            }
        }
    }

    private fun renderTab(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        label: String,
        tab: PanelTab,
    ) {
        if (activeTab == tab) {
            guiGraphics.fill(x, y, x + width, y + TAB_HEIGHT, COLOR_ACTIVE_TAB)
        }
        guiGraphics.drawString(font, label, x + 8, y + 6, if (activeTab == tab) COLOR_TEXT else COLOR_MUTED, false)
    }

    private fun sessionLabel(): String =
        if (SessionState.sessionActive) {
            "Connected"
        } else {
            "Offline or not authenticated"
        }

    private fun overviewHint(): String =
        if (SessionState.latestStats == null) {
            "Waiting for stats..."
        } else {
            "Live server stats"
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
        input.setValue("")
        consoleScrollOffset = 0
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

    private fun renderCommandPlaceholder(guiGraphics: GuiGraphics) {
        val input = commandInput ?: return
        if (activeTab == PanelTab.CONSOLE && input.visible && input.value.isEmpty()) {
            guiGraphics.drawString(font, "Enter a command...", input.x + 4, input.y + 5, COLOR_MUTED, false)
        }
    }

    private fun isCopy(keyEvent: KeyEvent): Boolean =
        keyEvent.key() == GLFW.GLFW_KEY_C && keyEvent.modifiers() and GLFW.GLFW_MOD_CONTROL != 0

    private fun isScrollKey(keyEvent: KeyEvent): Boolean = keyEvent.key() == GLFW.GLFW_KEY_UP || keyEvent.key() == GLFW.GLFW_KEY_DOWN

    private fun scrollConsole(lines: Int) {
        consoleScrollOffset =
            (consoleScrollOffset + lines)
                .coerceIn(0, maxConsoleScroll(consoleBounds().height))
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
        val panelX = 24
        val panelY = 24
        val navX = panelX + 12
        val navY = panelY + 58
        return Bounds(navX, navY, 120)
    }

    private fun consoleBounds(): Bounds {
        val panelX = 24
        val panelY = 24
        val panelWidth = width - 48
        val panelHeight = height - 48
        val navX = panelX + 12
        val navY = panelY + 58
        val navWidth = 120
        val contentX = navX + navWidth + 16
        val contentY = navY
        val contentWidth = panelX + panelWidth - contentX - 12
        val contentHeight = panelY + panelHeight - contentY - 12
        return Bounds(contentX + 16, contentY + 54, contentWidth - 32, contentHeight - 90)
    }

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

    private companion object {
        const val TAB_HEIGHT = 20
        const val SCROLL_LINES = 3
        const val CONSOLE_PADDING = 10
        const val COLOR_BACKGROUND = 0xFF101418.toInt()
        const val COLOR_PANEL = 0xFF151A20.toInt()
        const val COLOR_SIDEBAR = 0xFF0D1117.toInt()
        const val COLOR_CONTENT = 0xFF1B2028.toInt()
        const val COLOR_ACTIVE_TAB = 0xFF2F6F85.toInt()
        const val COLOR_BORDER = 0xFF5F6C7A.toInt()
        const val COLOR_TEXT = 0xFFF4F7FA.toInt()
        const val COLOR_MUTED = 0xFF9AA7B5.toInt()
        const val TITLE = "Venus"
    }
}
