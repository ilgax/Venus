package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.client.ui.UiTestFixture
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.state.SessionState
import org.lwjgl.glfw.GLFW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConsolePageBehaviorTest : UiTestFixture() {
    @Test
    fun `subscription command submission and bounded deduplicated history follow session state`() {
        val commands = mutableListOf<String>()
        var subscriptions = 0
        val page = ConsolePage(commands::add, { subscriptions++ })
        val input = page.inputField()!!
        page.layout(Bounds(0, 0, 500, 300))

        page.onEnter()
        assertEquals(0, subscriptions)
        assertTrue(input.isFocused)
        input.setValue("ignored")
        assertTrue(page.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0))
        assertTrue(commands.isEmpty())

        SessionState.markActive()
        page.onEnter()
        page.onEnter()
        assertEquals(1, subscriptions)
        assertTrue(page.currentUiState().logsSubscribed)

        submit(page, input, "say alpha")
        submit(page, input, "say alpha")
        submit(page, input, "say beta")
        assertEquals(listOf("say alpha", "say alpha", "say beta"), commands)
        assertEquals(listOf("say alpha", "say beta"), page.currentUiState().commandHistory)

        assertTrue(page.keyPressed(GLFW.GLFW_KEY_UP, 0, 0))
        assertEquals("say beta", input.value)
        assertTrue(page.keyPressed(GLFW.GLFW_KEY_UP, 0, 0))
        assertEquals("say alpha", input.value)
        assertTrue(page.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0))
        assertEquals("say beta", input.value)

        page.onLeave()
        assertFalse(input.isFocused)
        assertFalse(input.editBox.visible)
    }

    @Test
    fun `console selection copy scrolling and header controls are focus sensitive`() {
        val page = ConsolePage({}, {}, historyLimit = { 3 })
        page.inputField()
        page.layout(Bounds(0, 0, 500, 300))
        SessionState.markActive()
        SessionState.addConsoleLines(listOf("old", "one", "two", "three"))
        page.render(graphics, font, 0, 0, 0f)

        assertFalse(page.mouseClicked(20.0, 40.0, 1))
        assertTrue(page.mouseClicked(20.0, 40.0, 0))
        assertEquals(0..0, page.currentUiState().selection)
        assertTrue(page.keyPressed(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL))
        assertEquals(listOf("one"), copiedText)

        assertFalse(page.mouseScrolled(0.0, 0.0, 0.0, -1.0))
        assertTrue(page.mouseScrolled(20.0, 60.0, 0.0, -1.0))
        assertFalse(page.currentUiState().autoScroll)

        assertFalse(page.mouseClicked(300.0, 15.0, 0))
        assertTrue(page.mouseClicked(400.0, 15.0, 0))
        assertTrue(page.currentUiState().paused)
        assertTrue(page.mouseClicked(450.0, 15.0, 0))
        assertTrue(SessionState.consoleLines.isEmpty())
        assertNull(page.currentUiState().selection)
    }

    private fun submit(
        page: ConsolePage,
        input: dev.ilgax.venus.client.ui.widget.VenusTextField,
        command: String,
    ) {
        input.setValue(command)
        assertTrue(page.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0))
        assertEquals("", input.value)
    }
}
