package dev.ilgax.venus.client.ui

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.ModalKind
import dev.ilgax.venus.client.ui.core.ToastKind
import dev.ilgax.venus.client.ui.core.VenusPage
import dev.ilgax.venus.client.ui.page.SettingsPage
import io.mockk.verify
import org.lwjgl.glfw.GLFW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VenusScreenBehaviorTest : UiTestFixture() {
    @Test
    fun `screen computes compact layout and routes keyboard and primary sidebar navigation`() {
        val screen = screen()
        screen.initializeForJvmTest(800, 480)

        assertTrue(screen.currentUiState().compactMode)
        assertEquals(Bounds(112, 38, 680, 434), screen.currentUiState().contentBounds)
        assertEquals(VenusPage.DASHBOARD, screen.currentUiState().activePage)

        assertTrue(screen.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_TAB)))
        assertEquals(VenusPage.PLAYERS, screen.currentUiState().activePage)
        assertEquals(VenusPage.DASHBOARD, screen.currentUiState().previousPage)

        assertTrue(screen.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_TAB)))
        assertTrue(screen.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_TAB)))
        assertEquals(VenusPage.CONSOLE, screen.currentUiState().activePage)
        assertTrue(screen.currentUiState().textInputFocused)

        assertTrue(screen.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT)))
        assertEquals(VenusPage.FILES, screen.currentUiState().activePage)
        assertFalse(screen.currentUiState().textInputFocused)

        assertFalse(screen.mouseClicked(UiTestSupport.mouse(20.0, 80.0, GLFW.GLFW_MOUSE_BUTTON_RIGHT), false))
        assertEquals(VenusPage.FILES, screen.currentUiState().activePage)
        assertTrue(screen.mouseClicked(UiTestSupport.mouse(20.0, 80.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false))
        assertEquals(VenusPage.PLAYERS, screen.currentUiState().activePage)
    }

    @Test
    fun `modal captures input and toast lifecycle remains bounded`() {
        val screen = screen()
        screen.initializeForJvmTest(1_000, 600)
        var confirms = 0

        screen.showConfirm("Delete", "Confirm", { confirms++ }, ModalKind.DANGER)
        assertEquals(1, screen.currentUiState().modalCount)
        assertTrue(screen.mouseClicked(UiTestSupport.mouse(510.0, 330.0, GLFW.GLFW_MOUSE_BUTTON_RIGHT), false))
        assertEquals(0, confirms)
        assertEquals(1, screen.currentUiState().modalCount)
        assertTrue(screen.mouseClicked(UiTestSupport.mouse(510.0, 330.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false))
        assertEquals(1, confirms)
        assertEquals(0, screen.currentUiState().modalCount)

        screen.showConfirm("Cancel", "Escape", {}, ModalKind.WARN)
        assertTrue(screen.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_ESCAPE)))
        assertEquals(0, screen.currentUiState().modalCount)

        repeat(5) { screen.showToast(ToastKind.INFO, "Toast $it", "Message") }
        assertEquals(4, screen.currentUiState().toastCount)
        screen.showToast(ToastKind.DANGER, "Expired", "Gone", durationMs = -1)
        screen.render(graphics, 0, 0, 0f)
        assertEquals(3, screen.currentUiState().toastCount)
    }

    @Test
    fun `close key respects focused text input and ordinary shortcuts`() {
        val screen = screen()
        screen.initializeForJvmTest(1_000, 600)

        repeat(3) { assertTrue(screen.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_TAB))) }
        assertEquals(VenusPage.CONSOLE, screen.currentUiState().activePage)
        assertTrue(screen.currentUiState().textInputFocused)

        screen.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL))
        verify(exactly = 0) { minecraft.setScreen(null) }

        assertTrue(screen.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_F6)))
        verify(exactly = 1) { minecraft.setScreen(null) }
    }

    private fun screen(): VenusScreen =
        VenusScreen(
            sendConsoleCommand = {},
            subscribeLogs = {},
            requestPlayerList = {},
            requestPlayerDetail = {},
            sendPlayerAction = { _, _, _ -> "player-action" },
            subscribeStats = {},
            requestFileRoots = { "roots" },
            requestFileList = { _, _, _ -> "list" },
            sendFileAction = { _, _, _, _, _ -> "file-action" },
            uploadFile = { _, _, _, _ -> "upload" },
            downloadFile = { _, _, _, _ -> "download" },
            openFileEditor = { _, _ -> "open" },
            saveEditedFile = { _, _, _, _, _ -> "save" },
            cancelFileTransfer = {},
            onSaveSettings = {},
            initialSettings =
                SettingsPage.Settings(
                    compactMode = false,
                    animationsEnabled = true,
                    backgroundOpacity = 0.75f,
                    showPlayerHeads = true,
                    confirmDangerousActions = true,
                    consoleHistoryLimit = 500,
                ),
        )
}
