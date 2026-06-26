package dev.ilgax.venus.client.ui.render

import dev.ilgax.venus.client.ui.core.Bounds
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

/**
 * Native Minecraft texture blit helpers. All Venus UI textures live under the
 * `venus` namespace and are small monochrome PNGs intended to be tinted via
 * [GuiGraphics.blit] color modulation where the pipeline supports it.
 *
 * Identifiers are cached as `private val` on this object to avoid per-frame
 * allocation. Callers must NOT construct `Identifier` instances in render loops.
 */
object TextureRenderer {
    private val ICONS = Identifier.fromNamespaceAndPath("venus", "textures/gui/icons.png")
    private val LOGO = Identifier.fromNamespaceAndPath("venus", "textures/gui/logo.png")

    val iconsSheet: Identifier get() = ICONS
    val logo: Identifier get() = LOGO

    /**
     * Blit a full texture at [x], [y] sized [w]x[h]. For sub-regions of a sprite
     * sheet use [blitRegion].
     */
    fun blit(
        g: GuiGraphics,
        texture: Identifier,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        g.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x,
            y,
            0f,
            0f,
            w,
            h,
            w,
            h,
            w,
            h,
        )
    }

    /**
     * Blit a sub-region [u], [v] of size [regionW]x[regionH] from a texture
     * [sheetW]x[sheetH], drawn at [w]x[h]. Used for icon sprite sheets.
     */
    fun blitRegion(
        g: GuiGraphics,
        texture: Identifier,
        x: Int,
        y: Int,
        u: Int,
        v: Int,
        regionW: Int,
        regionH: Int,
        sheetW: Int,
        sheetH: Int,
        w: Int,
        h: Int,
    ) {
        g.blit(
            RenderPipelines.GUI_TEXTURED,
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

    fun blitBounds(
        g: GuiGraphics,
        texture: Identifier,
        bounds: Bounds,
    ) = blit(g, texture, bounds.x, bounds.y, bounds.width, bounds.height)
}
