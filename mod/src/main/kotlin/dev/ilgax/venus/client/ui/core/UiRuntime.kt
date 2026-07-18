package dev.ilgax.venus.client.ui.core

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import java.nio.file.Path

/**
 * Narrow runtime boundary for UI services that are otherwise only reachable
 * through the live Minecraft singleton. Defaults preserve normal client
 * behavior; JVM tests can replace and restore individual providers.
 */
internal object UiRuntime {
    private val defaultFontProvider: () -> Font = { Minecraft.getInstance().font }
    private val defaultClipboardWriter: (String) -> Unit = { Minecraft.getInstance().keyboardHandler.setClipboard(it) }
    private val defaultGameDirectoryProvider: () -> Path = { Minecraft.getInstance().gameDirectory.toPath() }

    var fontProvider: () -> Font = defaultFontProvider
    var clipboardWriter: (String) -> Unit = defaultClipboardWriter
    var gameDirectoryProvider: () -> Path = defaultGameDirectoryProvider

    fun font(): Font = fontProvider()

    fun copyToClipboard(text: String) = clipboardWriter(text)

    fun gameDirectory(): Path = gameDirectoryProvider()

    fun reset() {
        fontProvider = defaultFontProvider
        clipboardWriter = defaultClipboardWriter
        gameDirectoryProvider = defaultGameDirectoryProvider
    }
}
