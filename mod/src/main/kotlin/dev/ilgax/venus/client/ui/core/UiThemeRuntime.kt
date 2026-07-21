package dev.ilgax.venus.client.ui.core

import dev.ilgax.venus.client.ui.profile.UiTheme
import kotlin.math.roundToInt

object UiThemeRuntime {
    var current: UiTheme = UiTheme()
        private set

    fun activate(theme: UiTheme) {
        current = theme
        VenusTheme.apply(theme)
        VenusSpacing.apply(theme)
        VenusDimensions.apply(theme)
    }

    fun reset() {
        activate(UiTheme())
    }

    fun contrastWarnings(theme: UiTheme): List<String> =
        buildList {
            if (contrastRatio(theme.text, theme.window) < MIN_TEXT_CONTRAST) add("Text and window contrast is low")
            if (contrastRatio(theme.textMuted, theme.surface) < MIN_MUTED_CONTRAST) add("Muted text and surface contrast is low")
            if (contrastRatio(theme.accent, theme.surface) < MIN_MUTED_CONTRAST) add("Accent and surface contrast is low")
        }

    internal fun contrastRatio(
        foreground: Int,
        background: Int,
    ): Double {
        val lighter = maxOf(luminance(foreground), luminance(background))
        val darker = minOf(luminance(foreground), luminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color ushr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return channel(16) * 0.2126 + channel(8) * 0.7152 + channel(0) * 0.0722
    }

    internal fun scaled(
        base: Int,
        theme: UiTheme,
    ): Int = (base * (theme.spacing / 8f)).roundToInt().coerceAtLeast(1)

    private const val MIN_TEXT_CONTRAST = 4.5
    private const val MIN_MUTED_CONTRAST = 3.0
}
