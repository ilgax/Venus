package dev.ilgax.venus.client.ui.layout

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.profile.FactoryUiProfile
import dev.ilgax.venus.client.ui.profile.NavigationPlacement
import dev.ilgax.venus.client.ui.profile.UiLayoutMode
import dev.ilgax.venus.client.ui.profile.UiShell
import dev.ilgax.venus.client.ui.profile.UiTheme
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiLayoutEngineTest {
    @Test
    fun `shell reserves navigation on every supported side`() {
        NavigationPlacement.entries.forEach { placement ->
            val geometry = UiLayoutEngine.shell(1000, 700, UiShell(navigationPlacement = placement), UiTheme(), false)

            assertTrue(geometry.window.contains(geometry.content.x, geometry.content.y))
            assertTrue(geometry.navigation.width > 0)
            assertTrue(geometry.navigation.height > 0)
        }
    }

    @Test
    fun `normal factory dashboard maps all visible modules to grid bounds`() {
        val content = Bounds(100, 50, 840, 600)
        val profile = FactoryUiProfile.profile

        val geometry =
            UiLayoutEngine.modules(
                content,
                profile.normalLayout,
                "dashboard",
                UiLayoutMode.NORMAL,
                gap = 8,
                rowHeight = 22,
            )

        assertEquals(9, geometry.modules.size)
        assertTrue(geometry.modules.values.all { it.x >= content.x && it.right <= content.right })
    }
}
