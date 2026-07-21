package dev.ilgax.venus.client.ui.core

import dev.ilgax.venus.client.ui.profile.UiTheme
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiThemeRuntimeTest {
    @After
    fun resetTheme() {
        UiThemeRuntime.reset()
    }

    @Test
    fun `theme activation updates shared Venus tokens`() {
        val theme = UiTheme(accent = 0xFF112233.toInt(), spacing = 12, rowHeight = 30)

        UiThemeRuntime.activate(theme)

        assertEquals(theme.accent, VenusTheme.ACCENT)
        assertEquals(12, VenusSpacing.MD)
        assertEquals(30, VenusDimensions.ROW_HEIGHT)
    }

    @Test
    fun `low contrast themes warn without rejection`() {
        val theme = UiTheme(text = 0xFF111111.toInt(), window = 0xFF111111.toInt())

        UiProfileValidatorBridge.validate(theme)

        assertTrue(UiThemeRuntime.contrastWarnings(theme).isNotEmpty())
    }
}

private object UiProfileValidatorBridge {
    fun validate(theme: UiTheme) {
        UiThemeRuntime.activate(theme)
    }
}
