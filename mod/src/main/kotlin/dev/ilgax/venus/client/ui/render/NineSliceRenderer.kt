package dev.ilgax.venus.client.ui.render

import dev.ilgax.venus.client.ui.core.Bounds
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.Identifier

/**
 * Nine-slice panel renderer. Splits a texture into 9 regions (corners, edges,
 * center) and stretches the edges/center to fill [bounds]. This is the
 * preferred panel primitive: reliable, no shader code, scales cleanly.
 *
 * The texture is expected to be `(3*slice) x (3*slice)` px with the center
 * slice at `[slice, 2*slice)`.
 *
 * First-pass implementation draws a flat filled panel with borders via
 * [VenusDraw]; nine-slice blits are wired but only used once a panel texture
 * asset exists. This keeps the code path ready for an asset swap without a
 * refactor.
 */
object NineSliceRenderer {
    fun draw(
        g: GuiGraphics,
        texture: Identifier,
        bounds: Bounds,
        slice: Int,
        sheetSize: Int,
    ) {
        val (x, y, w, h) = bounds
        val s = slice
        val innerW = w - s * 2
        val innerH = h - s * 2

        // Corners
        blit(g, texture, x, y, 0, 0, s, s, s, s, sheetSize, sheetSize)
        blit(g, texture, x + w - s, y, s, 0, s, s, s, s, sheetSize, sheetSize)
        blit(g, texture, x, y + h - s, 0, s, s, s, s, s, sheetSize, sheetSize)
        blit(g, texture, x + w - s, y + h - s, s, s, s, s, s, s, sheetSize, sheetSize)

        // Edges
        if (innerW > 0) {
            g.blit(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                texture,
                x + s,
                y,
                s.toFloat(),
                0f,
                innerW,
                s,
                sheetSize,
                sheetSize,
            )
            g.blit(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                texture,
                x + s,
                y + h - s,
                s.toFloat(),
                s.toFloat(),
                innerW,
                s,
                sheetSize,
                sheetSize,
            )
        }
        if (innerH > 0) {
            g.blit(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y + s,
                0f,
                s.toFloat(),
                s,
                innerH,
                sheetSize,
                sheetSize,
            )
            g.blit(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                texture,
                x + w - s,
                y + s,
                s.toFloat(),
                s.toFloat(),
                s,
                innerH,
                sheetSize,
                sheetSize,
            )
        }

        // Center
        if (innerW > 0 && innerH > 0) {
            g.blit(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                texture,
                x + s,
                y + s,
                s.toFloat(),
                s.toFloat(),
                innerW,
                innerH,
                sheetSize,
                sheetSize,
            )
        }
    }

    private fun blit(
        g: GuiGraphics,
        texture: Identifier,
        x: Int,
        y: Int,
        u: Int,
        v: Int,
        regionW: Int,
        regionH: Int,
        w: Int,
        h: Int,
        sheetW: Int,
        sheetH: Int,
    ) {
        g.blit(
            net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            texture,
            x,
            y,
            u.toFloat(),
            v.toFloat(),
            regionW,
            regionH,
            w,
            h,
            sheetW,
            sheetH,
        )
    }
}
