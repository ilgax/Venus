package dev.ilgax.venus.client.ui

import dev.ilgax.venus.client.ui.core.UiRuntime
import dev.ilgax.venus.state.SessionState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.server.Bootstrap
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

object UiTestSupport {
    init {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    fun font(
        lineHeight: Int = 9,
        characterWidth: Int = 6,
    ): Font {
        val font = spyk(Font(mockk<Font.Provider>(relaxed = true)))
        Font::class.java.getDeclaredField("lineHeight").apply {
            isAccessible = true
            setInt(font, lineHeight)
        }
        every { font.width(any<String>()) } answers { firstArg<String>().length * characterWidth }
        return font
    }

    fun graphics(): GuiGraphics = mockk(relaxed = true)

    fun minecraft(font: Font): Minecraft {
        val minecraft = mockk<Minecraft>(relaxed = true)
        Minecraft::class.java.getDeclaredField("font").apply {
            isAccessible = true
            set(minecraft, font)
        }
        return minecraft
    }

    fun mouse(
        x: Double,
        y: Double,
        button: Int,
    ): MouseButtonEvent =
        mockk {
            every { this@mockk.x() } returns x
            every { this@mockk.y() } returns y
            every { this@mockk.button() } returns button
        }

    fun key(
        key: Int,
        modifiers: Int = 0,
    ): KeyEvent =
        mockk(relaxed = true) {
            every { this@mockk.key() } returns key
            every { this@mockk.modifiers() } returns modifiers
        }
}

abstract class UiTestFixture {
    protected lateinit var font: Font
    protected lateinit var graphics: GuiGraphics
    protected lateinit var minecraft: Minecraft
    protected val copiedText = mutableListOf<String>()

    @BeforeTest
    fun setUpUiRuntime() {
        font = UiTestSupport.font()
        graphics = UiTestSupport.graphics()
        minecraft = UiTestSupport.minecraft(font)
        mockkStatic(Minecraft::class)
        every { Minecraft.getInstance() } returns minecraft
        copiedText.clear()
        SessionState.reset()
        UiRuntime.fontProvider = { font }
        UiRuntime.clipboardWriter = copiedText::add
        UiRuntime.gameDirectoryProvider = { Path.of(".").toAbsolutePath().normalize() }
    }

    @AfterTest
    fun tearDownUiRuntime() {
        SessionState.reset()
        UiRuntime.reset()
        unmockkStatic(Minecraft::class)
    }
}
