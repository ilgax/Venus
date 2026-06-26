package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.core.Bounds
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

/**
 * Base class for Venus interactive widgets. Extends Minecraft's
 * [AbstractWidget] so the screen's focus, narration, and renderable bookkeeping
 * work natively. Passive visuals stay as render helpers in `render/`, not here.
 *
 * Subclasses override [renderVenus] for drawing and handle input via the
 * mouse/key/scroll methods. [bounds] is the canonical layout rect; the
 * underlying [AbstractWidget] x/y/width/height are kept in sync on layout.
 */
abstract class VenusWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
) : AbstractWidget(x, y, width, height, message) {
    var bounds: Bounds = Bounds(x, y, width, height)
        private set

    /** Optional tooltip shown when hovered and [tooltipText] is non-null. */
    open var tooltipText: String? = null

    /**
     * Called by the screen when layout is recomputed (init/resize). Subclasses
     * should update any internal layout-derived state here, not in render().
     */
    open fun layout(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        bounds = Bounds(x, y, width, height)
        this.x = x
        this.y = y
        this.width = width
        this.height = height
    }

    fun layout(b: Bounds) = layout(b.x, b.y, b.width, b.height)

    /**
     * Public render entry point. Delegates to [renderVenus] then draws tooltip.
     * Call this from pages/components instead of the protected renderWidget.
     */
    fun renderVenus(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        renderWidget(g, mouseX, mouseY, partialTick)
    }

    override fun renderWidget(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        drawVenus(g, mouseX, mouseY, partialTick)
        val tip = tooltipText
        if (tip != null) {
            setTooltip(
                net.minecraft.client.gui.components.Tooltip
                    .create(
                        net.minecraft.network.chat.Component
                            .literal(tip),
                        null,
                    ),
            )
        }
    }

    protected abstract fun drawVenus(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    )

    override fun updateWidgetNarration(narration: NarrationElementOutput) {
        defaultButtonNarrationText(narration)
    }

    override fun isActive(): Boolean = active && visible
}
