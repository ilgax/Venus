package dev.ilgax.venus.client.ui.editor

import dev.ilgax.venus.client.ui.profile.FactoryUiProfile
import dev.ilgax.venus.client.ui.profile.UiLayoutMode
import dev.ilgax.venus.client.ui.profile.UiModuleType
import dev.ilgax.venus.client.ui.profile.UiProfileController
import dev.ilgax.venus.client.ui.profile.UiProfileStore
import org.junit.Test
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UiEditorSessionTest {
    @Test
    fun `factory editing uses copy and supports undo redo apply`() {
        val controller = controller()
        val session = UiEditorSession(FactoryUiProfile.profile, controller)

        assertNotEquals("factory", session.draft.id)
        assertEquals("My Profile", session.draft.name)
        assertTrue(session.renameProfile("Operations"))
        assertTrue(session.canUndo)
        assertTrue(session.undo())
        assertEquals("My Profile", session.draft.name)
        assertTrue(session.redo())
        assertEquals("Operations", session.apply().name)
        assertEquals("Operations", controller.resolve(null).name)
    }

    @Test
    fun `grid edits reject overlap and add module to both layouts`() {
        val session = UiEditorSession(FactoryUiProfile.profile, controller())

        assertFalse(session.move("tps", UiLayoutMode.NORMAL, 3, 1))
        val page = assertNotNull(session.addPage())
        val module = assertNotNull(session.addModule(UiModuleType.METRIC_CARD, page.id))

        assertTrue(
            session.draft.normalLayout.placements
                .any { it.moduleId == module.id && it.pageId == page.id },
        )
        assertTrue(
            session.draft.compactLayout.placements
                .any { it.moduleId == module.id && it.pageId == page.id },
        )
    }

    @Test
    fun `profile controller assigns imports and protects factory`() {
        val controller = controller()
        val custom = controller.editableCopy(FactoryUiProfile.profile)
        controller.saveProfile(custom)
        controller.assignCurrentServer("Example.org", custom.id)

        assertEquals(custom.id, controller.resolve("example.org:25565").id)
        val imported = controller.importAsCopy(controller.export(custom))
        assertNotEquals(custom.id, imported.id)
        assertFailsWith<IllegalArgumentException> { controller.delete("factory") }

        controller.delete(custom.id)
        assertEquals(imported.id, controller.resolve("example.org").id)
    }

    private fun controller(): UiProfileController = UiProfileController(UiProfileStore(createTempDirectory("venus-ui-editor").toFile()))
}
