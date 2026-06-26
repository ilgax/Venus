package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import dev.ilgax.venus.client.ui.widget.VenusSlider
import dev.ilgax.venus.client.ui.widget.VenusToggle
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Settings page. Uses the Venus UI kit exclusively (no unrelated config
 * library). Only settings Venus currently supports or this UI needs:
 * compact mode, animations, background opacity, show player heads,
 * confirm dangerous actions, console history limit.
 *
 * Persistence is via the injected [onSave] callback which writes to the
 * project's existing config system — no second config store.
 */
class SettingsPage(
    private val onSave: (Settings) -> Unit,
) : VenusPageContract {
    private var contentBounds: Bounds = Bounds(0, 0, 0, 0)

    data class Settings(
        val compactMode: Boolean,
        val animationsEnabled: Boolean,
        val backgroundOpacity: Float,
        val showPlayerHeads: Boolean,
        val confirmDangerousActions: Boolean,
        val consoleHistoryLimit: Int,
    )

    private var compactMode = false
    private var animationsEnabled = true
    var backgroundOpacity = 0.75f
        private set
    var showPlayerHeads = true
        private set
    private var confirmDangerousActions = true
    var consoleHistoryLimit = 500
        private set

    private var compactToggle: VenusToggle? = null
    private var animationsToggle: VenusToggle? = null
    private var headsToggle: VenusToggle? = null
    private var confirmToggle: VenusToggle? = null
    private var historySlider: VenusSlider? = null
    private var opacitySlider: VenusSlider? = null

    override fun layout(contentBounds: Bounds) {
        this.contentBounds = contentBounds
        val pad = VenusDimensions.CONTENT_PADDING
        val inner = contentBounds.inset(pad)
        val labelW = 160
        val controlX = inner.x + labelW
        val controlW = inner.width - labelW
        var y = inner.y + 24

        compactToggle?.layout(controlX, y, VenusDimensions.TOGGLE_WIDTH, VenusDimensions.TOGGLE_HEIGHT)
        y += ROW_H
        animationsToggle?.layout(controlX, y, VenusDimensions.TOGGLE_WIDTH, VenusDimensions.TOGGLE_HEIGHT)
        y += ROW_H
        headsToggle?.layout(controlX, y, VenusDimensions.TOGGLE_WIDTH, VenusDimensions.TOGGLE_HEIGHT)
        y += ROW_H
        confirmToggle?.layout(controlX, y, VenusDimensions.TOGGLE_WIDTH, VenusDimensions.TOGGLE_HEIGHT)
        y += ROW_H
        historySlider?.layout(Bounds(controlX, y, controlW, VenusDimensions.SLIDER_HEIGHT))
        y += ROW_H
        opacitySlider?.layout(Bounds(controlX, y, controlW, VenusDimensions.SLIDER_HEIGHT))
    }

    fun widgets(): List<dev.ilgax.venus.client.ui.widget.VenusWidget> {
        buildControls()
        return listOfNotNull(compactToggle, animationsToggle, headsToggle, confirmToggle, historySlider, opacitySlider)
    }

    private fun buildControls() {
        if (compactToggle != null) return
        compactToggle =
            VenusToggle(initial = compactMode) {
                compactMode = it
                save()
            }
        animationsToggle =
            VenusToggle(initial = animationsEnabled) {
                animationsEnabled = it
                save()
            }
        headsToggle =
            VenusToggle(initial = showPlayerHeads) {
                showPlayerHeads = it
                save()
            }
        confirmToggle =
            VenusToggle(initial = confirmDangerousActions) {
                confirmDangerousActions = it
                save()
            }
        historySlider =
            VenusSlider(
                width = 120,
                min = 100.0,
                max = 2000.0,
                step = 100.0,
                initial = consoleHistoryLimit.toDouble(),
                valueFormatter = { "${it.toInt()} lines" },
            ) {
                consoleHistoryLimit = it.toInt()
                save()
            }
        opacitySlider =
            VenusSlider(
                width = 120,
                min = 0.0,
                max = 1.0,
                step = 0.05,
                initial = backgroundOpacity.toDouble(),
                valueFormatter = { String.format("%.0f%%", it * 100) },
            ) {
                backgroundOpacity = it.toFloat()
                save()
            }
    }

    fun applySettings(s: Settings) {
        compactMode = s.compactMode
        animationsEnabled = s.animationsEnabled
        backgroundOpacity = s.backgroundOpacity
        showPlayerHeads = s.showPlayerHeads
        confirmDangerousActions = s.confirmDangerousActions
        consoleHistoryLimit = s.consoleHistoryLimit
    }

    fun currentSettings(): Settings =
        Settings(compactMode, animationsEnabled, backgroundOpacity, showPlayerHeads, confirmDangerousActions, consoleHistoryLimit)

    override fun onEnter() {
        compactToggle?.visible = true
        animationsToggle?.visible = true
        headsToggle?.visible = true
        confirmToggle?.visible = true
        historySlider?.visible = true
        opacitySlider?.visible = true
    }

    override fun onLeave() {
        compactToggle?.visible = false
        animationsToggle?.visible = false
        headsToggle?.visible = false
        confirmToggle?.visible = false
        historySlider?.visible = false
        opacitySlider?.visible = false
    }

    override fun render(
        g: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val pad = VenusDimensions.CONTENT_PADDING
        val inner = contentBounds.inset(pad)

        VenusDraw.text(g, font, "Settings", inner.x, inner.y, VenusTheme.TEXT, false)
        VenusDraw.hSeparator(g, inner.x, inner.y + font.lineHeight + 2, inner.width, VenusTheme.BORDER)

        val labelX = inner.x
        val controlX = inner.x + 160
        var y = inner.y + 24

        renderRow(g, font, "Compact mode", labelX, y)
        y += ROW_H
        renderRow(g, font, "Animations", labelX, y)
        y += ROW_H
        renderRow(g, font, "Show player heads", labelX, y)
        y += ROW_H
        renderRow(g, font, "Confirm dangerous actions", labelX, y)
        y += ROW_H
        renderRow(g, font, "Console history limit", labelX, y)
        y += ROW_H
        renderRow(g, font, "Background opacity", labelX, y)
    }

    private fun renderRow(
        g: GuiGraphics,
        font: Font,
        label: String,
        x: Int,
        y: Int,
    ) {
        VenusDraw.text(g, font, label, x, y + (VenusDimensions.TOGGLE_HEIGHT - font.lineHeight) / 2, VenusTheme.TEXT_MUTED, false)
    }

    private fun save() {
        onSave(currentSettings())
    }

    private companion object {
        const val ROW_H = 28
    }
}
