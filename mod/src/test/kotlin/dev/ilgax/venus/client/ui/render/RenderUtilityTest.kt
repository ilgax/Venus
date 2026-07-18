package dev.ilgax.venus.client.ui.render

import dev.ilgax.venus.client.ui.UiTestFixture
import dev.ilgax.venus.client.ui.core.Bounds
import io.mockk.verify
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RenderUtilityTest : UiTestFixture() {
    private val bounds = Bounds(10, 20, 100, 40)

    @Test
    fun `drawing primitives delegate deterministic geometry`() {
        VenusDraw.rect(graphics, bounds, 1)
        VenusDraw.rect(graphics, 1, 2, 3, 4, 5)
        VenusDraw.border(graphics, bounds, 6)
        VenusDraw.border(graphics, 1, 2, 3, 4, 7)
        VenusDraw.panel(graphics, bounds, 8, 9)
        VenusDraw.hSeparator(graphics, 1, 2, 3, 10)
        VenusDraw.vSeparator(graphics, 1, 2, 3, 11)
        VenusDraw.vGradient(graphics, bounds, 12, 13)
        VenusDraw.statusDot(graphics, 1, 2, 14, 5)
        VenusDraw.progressBar(graphics, bounds, -1f)
        VenusDraw.progressBar(graphics, bounds, 0.5f)
        VenusDraw.progressBar(graphics, bounds, 2f)
        VenusDraw.pingBars(graphics, 1, 2, 9)
        VenusDraw.focusOutline(graphics, bounds)
        VenusDraw.hoverOverlay(graphics, bounds, 0f)
        VenusDraw.hoverOverlay(graphics, bounds, 0.5f)

        verify { graphics.fill(10, 20, 110, 60, 1) }
        verify { graphics.renderOutline(10, 20, 100, 40, 6) }
        verify { graphics.fillGradient(10, 20, 110, 60, 12, 13) }
    }

    @Test
    fun `text helpers handle full truncated centered and tooltip text`() {
        VenusDraw.text(graphics, font, "text", 1, 2)
        VenusDraw.text(graphics, font, Component.literal("component"), 1, 2)
        VenusDraw.textCentered(graphics, font, "center", bounds)
        VenusDraw.textCenteredX(graphics, font, "center", 50, 10)
        VenusDraw.textRight(graphics, font, "right", 100, 10)

        assertEquals("short", VenusDraw.textTruncated(graphics, font, "short", 0, 0, 100))
        assertEquals("...", VenusDraw.textTruncated(graphics, font, "long", 0, 0, 5))
        assertEquals("lo...", VenusDraw.textTruncated(graphics, font, "long text", 0, 0, 30))

        VenusDraw.tooltip(graphics, font, "tip", -2, 2)
        VenusDraw.tooltip(graphics, font, emptyList(), 0, 0)
        VenusDraw.tooltip(graphics, font, listOf("one", "longer"), 10, 10)
    }

    @Test
    fun `color blend clamps endpoints and interpolates channels`() {
        assertEquals(0xFF000000.toInt(), VenusDraw.blendColor(0x00000000, 0xFF000000.toInt(), -1f))
        assertEquals(0xFFFFFFFF.toInt(), VenusDraw.blendColor(0x00000000, 0xFFFFFFFF.toInt(), 1f))
        assertEquals(0xFF808080.toInt(), VenusDraw.blendColor(0x00000000, 0xFFFFFFFF.toInt(), 0.5f))
    }

    @Test
    fun `text layout truncates splits and centers deterministically`() {
        assertEquals(18, TextRenderUtil.width(font, "abc"))
        assertEquals("abc", TextRenderUtil.truncate(font, "abc", 20))
        assertEquals("...", TextRenderUtil.truncate(font, "abcdef", 10))
        assertEquals("a...", TextRenderUtil.truncate(font, "abcdef", 24))
        assertEquals(35, TextRenderUtil.verticalCenter(bounds, font))
        assertEquals(51, TextRenderUtil.horizontalCenter(bounds, font, "abc"))
        assertEquals(listOf(""), TextRenderUtil.split(font, "", 20))
        assertEquals(listOf("one", "two", "three"), TextRenderUtil.split(font, "one two three", 30))
    }

    @Test
    fun `scissor scope always balances on success and failure`() {
        ScissorStack.with(graphics, bounds) {}
        assertFailsWith<IllegalStateException> {
            ScissorStack.with(graphics, 1, 2, 3, 4) { error("stop") }
        }

        verify(exactly = 2) { graphics.disableScissor() }
    }

    @Test
    fun `texture helpers cover full region bounds and nine slice geometry`() {
        val texture = Identifier.fromNamespaceAndPath("venus", "textures/gui/test.png")
        TextureRenderer.blit(graphics, texture, 1, 2, 3, 4)
        TextureRenderer.blitRegion(graphics, texture, 1, 2, 3, 4, 5, 6, 16, 16, 10, 12)
        TextureRenderer.blitBounds(graphics, texture, bounds)
        NineSliceRenderer.draw(graphics, texture, Bounds(0, 0, 30, 20), 4, 12)
        NineSliceRenderer.draw(graphics, texture, Bounds(0, 0, 8, 8), 4, 12)

        assertEquals("venus:textures/gui/icons.png", TextureRenderer.iconsSheet.toString())
        assertEquals("venus:textures/gui/logo.png", TextureRenderer.logo.toString())
    }
}
