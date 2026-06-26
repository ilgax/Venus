package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.client.ui.core.Bounds
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * A Venus page. Pages render into [contentBounds] (the area below the top bar
 * and right of the sidebar). Pages receive injected callbacks via their
 * constructors — they never touch networking directly.
 *
 * Layout is recomputed in [layout] (called from Screen.init and on resize).
 * Render is called every frame. Input is routed by the screen.
 *
 * Pages must not allocate collections, strings, or textures every frame.
 * Reusable state (scroll offsets, selections) lives on the page instance.
 */
interface VenusPageContract {
    fun layout(contentBounds: Bounds)

    fun render(
        g: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    )

    /**
     * Called when this page becomes active (navigated to). Use to trigger
     * one-time data requests via the injected callbacks.
     */
    fun onEnter() {}

    /**
     * Called when this page is navigated away from. Use to hide inputs.
     */
    fun onLeave() {}

    fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean = false

    fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean = false
}
