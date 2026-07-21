package dev.ilgax.venus.client.ui

import dev.ilgax.venus.client.ui.module.UiScreenServices
import dev.ilgax.venus.client.ui.profile.FactoryUiProfile
import dev.ilgax.venus.client.ui.profile.UiProfileController
import dev.ilgax.venus.client.ui.profile.UiProfileStore
import io.mockk.verify
import org.junit.Test
import org.lwjgl.glfw.GLFW
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModularVenusScreenTest : UiTestFixture() {
    @Test
    fun `factory profile opens responsive non-pausing modular screen`() {
        val screen = ModularVenusScreen(services(), FactoryUiProfile.profile)

        screen.initializeForJvmTest(800, 480)

        assertTrue(screen.isCompactForTest())
        assertEquals("dashboard", screen.activePageForTest())
        assertFalse(screen.isPauseScreen)

        assertFalse(screen.mouseClicked(UiTestSupport.mouse(20.0, 86.0, GLFW.GLFW_MOUSE_BUTTON_RIGHT), false))
        assertEquals("dashboard", screen.activePageForTest())
        assertTrue(screen.mouseClicked(UiTestSupport.mouse(20.0, 86.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false))
        assertEquals("players", screen.activePageForTest())

        assertTrue(screen.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_F6)))
        verify(exactly = 1) { minecraft.setScreen(null) }
    }

    private fun services(): UiScreenServices =
        UiScreenServices(
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
            profiles = UiProfileController(UiProfileStore(createTempDirectory("venus-ui-screen").toFile())),
        )
}
