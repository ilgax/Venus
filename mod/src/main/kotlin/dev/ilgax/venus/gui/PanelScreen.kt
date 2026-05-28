package dev.ilgax.venus.gui

import dev.ilgax.venus.keybind.PanelKeybind
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component

class PanelScreen : Screen(Component.translatable("screen.venus.panel")) {
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
        guiGraphics.fill(navX + 8, navY + 8, navX + navWidth - 8, navY + 28, COLOR_ACTIVE_TAB)
        guiGraphics.drawString(font, "Overview", navX + 16, navY + 14, COLOR_TEXT, false)
        guiGraphics.drawString(font, "Console", navX + 16, navY + 40, COLOR_MUTED, false)

        guiGraphics.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, COLOR_CONTENT)
        guiGraphics.renderOutline(contentX, contentY, contentWidth, contentHeight, COLOR_BORDER)
        guiGraphics.drawString(font, "Venus panel shell", contentX + 16, contentY + 16, COLOR_TEXT, false)
        guiGraphics.drawString(font, "Stats and console come next.", contentX + 16, contentY + 32, COLOR_MUTED, false)

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen(): Boolean = false

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (PanelKeybind.matches(keyEvent)) {
            onClose()
            return true
        }

        return super.keyPressed(keyEvent)
    }

    private fun sessionLabel(): String =
        if (SessionState.sessionActive) {
            "Connected"
        } else {
            "Offline or not authenticated"
        }

    private companion object {
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
