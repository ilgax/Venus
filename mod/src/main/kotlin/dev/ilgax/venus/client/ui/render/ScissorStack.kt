package dev.ilgax.venus.client.ui.render

import net.minecraft.client.gui.GuiGraphics

/**
 * Scissor-clipping helper. Minecraft's [GuiGraphics] scissor is global and must
 * be balanced: every [push] must be matched by a [pop]. This is a thin wrapper
 * that makes pairing obvious at call sites and avoids leaking scissor state.
 *
 * Scissor coordinates are in screen space (origin bottom-left in OpenGL, but
 * Minecraft's [GuiGraphics.enableScissor] handles the flip internally).
 */
object ScissorStack {
    fun push(
        g: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        g.enableScissor(x, y, x + width, y + height)
    }

    fun push(
        g: GuiGraphics,
        bounds: dev.ilgax.venus.client.ui.core.Bounds,
    ) = push(g, bounds.x, bounds.y, bounds.width, bounds.height)

    fun pop(g: GuiGraphics) {
        g.disableScissor()
    }

    /**
     * Runs [block] with scissor active, then always pops — even if [block]
     * throws. Use for scoped clipping of list/table content.
     */
    inline fun with(
        g: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        block: () -> Unit,
    ) {
        push(g, x, y, width, height)
        try {
            block()
        } finally {
            pop(g)
        }
    }

    inline fun with(
        g: GuiGraphics,
        bounds: dev.ilgax.venus.client.ui.core.Bounds,
        block: () -> Unit,
    ) = with(g, bounds.x, bounds.y, bounds.width, bounds.height, block)
}
