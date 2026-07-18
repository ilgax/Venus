package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.client.ui.UiTestFixture
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.ModalKind
import dev.ilgax.venus.client.ui.core.ToastKind
import dev.ilgax.venus.protocol.FileActionResultPacket
import dev.ilgax.venus.protocol.FileEntryPacket
import dev.ilgax.venus.protocol.FileListPacket
import dev.ilgax.venus.protocol.FileRootPacket
import dev.ilgax.venus.protocol.FileRootsPacket
import dev.ilgax.venus.state.FileEditorState
import dev.ilgax.venus.state.FileTransferView
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.components.MultiLineEditBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilesPageBehaviorTest : UiTestFixture() {
    @Test
    fun `roots lists navigation and pagination stay correlated to latest request`() {
        val harness = Harness()
        val page = harness.page()
        page.layout(Bounds(0, 0, 800, 500))

        page.onEnter()
        assertEquals(0, harness.rootRequests)
        SessionState.markActive()
        page.onEnter()
        page.onEnter()
        assertEquals(1, harness.rootRequests)

        SessionState.updateFileRoots(roots())
        page.render(graphics, font, 0, 0, 0f)
        val firstRequest = page.currentUiState().lastListRequest!!
        assertEquals("r1", page.currentUiState().selectedRootId)
        assertEquals(Triple("r1", "", 0), harness.listRequests.single().second)

        SessionState.updateFileList(fileList("stale", nextOffset = 25))
        page.render(graphics, font, 0, 0, 0f)
        assertNull(page.currentUiState().nextOffset)

        SessionState.updateFileList(fileList(firstRequest, nextOffset = 50))
        page.render(graphics, font, 0, 0, 0f)
        assertEquals(50, page.currentUiState().nextOffset)
        assertTrue(page.mouseScrolled(20.0, 80.0, 0.0, -1.0))
        assertFalse(page.mouseScrolled(0.0, 0.0, 0.0, -1.0))

        assertTrue(page.mouseClicked(20.0, 62.0, 0))
        assertEquals("notes.txt", page.currentUiState().selectedPath)
        assertTrue(page.currentUiState().selectedEditable)
        assertTrue(page.mouseClicked(700.0, 465.0, 0))
        assertEquals(
            50,
            harness.listRequests
                .last()
                .second.third,
        )

        SessionState.updateFileList(
            fileList(
                page.currentUiState().lastListRequest!!,
                entries = listOf(directory()),
            ),
        )
        page.render(graphics, font, 0, 0, 0f)
        assertTrue(page.mouseClicked(20.0, 62.0, 0))
        assertTrue(page.mouseClicked(20.0, 465.0, 0))
        assertEquals("world", page.currentUiState().currentPath)
        assertEquals(Triple("r1", "world", 0), harness.listRequests.last().second)

        page.render(graphics, font, 0, 0, 0f)
        assertTrue(page.mouseClicked(20.0, 38.0, 0))
        assertEquals("r2", page.currentUiState().selectedRootId)
        assertEquals("", page.currentUiState().currentPath)
    }

    @Test
    fun `file actions confirmations failures results and transfer cancellation route callbacks`() {
        val harness = Harness()
        val page = readyPage(harness)
        val widgets = page.widgets()

        assertTrue(page.mouseClicked(20.0, 62.0, 0))
        (widgets[2] as net.minecraft.client.gui.components.EditBox).setValue("missing-local.txt")

        assertTrue(page.mouseClicked(210.0, 465.0, 0))
        assertEquals(Download("r1", "notes.txt", "missing-local.txt", false), harness.downloads.single())
        assertFalse(harness.downloads.single().overwrite)

        assertTrue(page.mouseClicked(115.0, 465.0, 0))
        assertEquals("Upload file", harness.confirmations.last().title)
        harness.confirmations.last().action()
        assertEquals(1, harness.uploads.size)

        assertTrue(page.mouseClicked(500.0, 465.0, 0))
        harness.confirmations.last().action()
        assertEquals("move", harness.actions.last().action)

        assertTrue(page.mouseClicked(600.0, 465.0, 0))
        assertEquals(ModalKind.DANGER, harness.confirmations.last().kind)
        harness.confirmations.last().action()
        assertEquals("delete", harness.actions.last().action)

        assertTrue(page.mouseClicked(305.0, 465.0, 0))
        val actionRequest = page.currentUiState().lastActionRequest!!
        SessionState.updateFileActionResult(FileActionResultPacket("file_action_result", "unrelated", true, "ok", "wrong"))
        page.render(graphics, font, 0, 0, 0f)
        assertTrue(harness.toasts.isEmpty())

        SessionState.updateFileActionResult(FileActionResultPacket("file_action_result", actionRequest, true, "ok", "created"))
        page.render(graphics, font, 0, 0, 0f)
        assertEquals(ToastKind.SUCCESS, harness.toasts.single().kind)

        SessionState.updateFileTransfer(FileTransferView("transfer-1", "download", "notes.txt", 10, 100, 5, "running"))
        page.render(graphics, font, 0, 0, 0f)
        assertTrue(page.mouseClicked(750.0, 30.0, 0))
        assertEquals(listOf("transfer-1"), harness.cancelledTransfers)
    }

    @Test
    fun `editor supports ordinary save force confirmation close and request failure toast`() {
        val harness = Harness()
        val page = readyPage(harness)
        SessionState.openFileEditor(FileEditorState("r1", "notes.txt", "before", "hash-1"))
        page.render(graphics, font, 0, 0, 0f)
        assertEquals("hash-1", page.currentUiState().loadedEditorHash)

        val editor = page.widgets().last() as MultiLineEditBox
        editor.setValue("after")
        assertTrue(page.mouseClicked(630.0, 465.0, 0))
        assertEquals(EditorSave("r1", "notes.txt", "after", "hash-1", false), harness.editorSaves.last())

        assertTrue(page.mouseClicked(710.0, 465.0, 0))
        assertEquals("Force save", harness.confirmations.last().title)
        harness.confirmations.last().action()
        assertTrue(harness.editorSaves.last().force)

        harness.failNextAction = true
        SessionState.closeFileEditor()
        page.render(graphics, font, 0, 0, 0f)
        assertTrue(page.mouseClicked(305.0, 465.0, 0))
        assertEquals(ToastKind.DANGER, harness.toasts.last().kind)

        SessionState.openFileEditor(FileEditorState("r1", "notes.txt", "again", "hash-2"))
        page.render(graphics, font, 0, 0, 0f)
        assertTrue(page.mouseClicked(20.0, 465.0, 0))
        assertNull(SessionState.fileEditor)
        assertNull(page.currentUiState().loadedEditorHash)
    }

    private fun readyPage(harness: Harness): FilesPage {
        val page = harness.page()
        page.layout(Bounds(0, 0, 800, 500))
        SessionState.markActive()
        page.onEnter()
        SessionState.updateFileRoots(roots())
        page.render(graphics, font, 0, 0, 0f)
        SessionState.updateFileList(fileList(page.currentUiState().lastListRequest!!))
        page.render(graphics, font, 0, 0, 0f)
        return page
    }

    private fun roots(): FileRootsPacket =
        FileRootsPacket(
            "file_roots",
            "roots-1",
            listOf(FileRootPacket("r1", "World", true), FileRootPacket("r2", "Logs", false)),
        )

    private fun fileList(
        requestId: String,
        entries: List<FileEntryPacket> = listOf(file()),
        nextOffset: Int? = null,
    ): FileListPacket = FileListPacket("file_list", requestId, "r1", "", entries, nextOffset)

    private fun file(): FileEntryPacket = FileEntryPacket("notes.txt", "notes.txt", "file", 2_048, 1, true)

    private fun directory(): FileEntryPacket = FileEntryPacket("world", "world", "directory", 0, 1, false)

    private data class Confirmation(
        val title: String,
        val action: () -> Unit,
        val kind: ModalKind,
    )

    private data class Toast(
        val kind: ToastKind,
        val title: String,
        val message: String,
    )

    private data class FileAction(
        val root: String,
        val action: String,
        val path: String,
        val destination: String?,
        val overwrite: Boolean,
    )

    private data class Download(
        val root: String,
        val path: String,
        val local: String,
        val overwrite: Boolean,
    )

    private data class EditorSave(
        val root: String,
        val path: String,
        val content: String,
        val hash: String,
        val force: Boolean,
    )

    private class Harness {
        var rootRequests = 0
        var sequence = 0
        var failNextAction = false
        val listRequests = mutableListOf<Pair<String, Triple<String, String, Int>>>()
        val actions = mutableListOf<FileAction>()
        val uploads = mutableListOf<List<Any>>()
        val downloads = mutableListOf<Download>()
        val editorSaves = mutableListOf<EditorSave>()
        val cancelledTransfers = mutableListOf<String>()
        val confirmations = mutableListOf<Confirmation>()
        val toasts = mutableListOf<Toast>()

        fun page(): FilesPage =
            FilesPage(
                requestRoots = {
                    rootRequests++
                    "roots-$rootRequests"
                },
                requestList = { root, path, offset ->
                    val id = "list-${++sequence}"
                    listRequests += id to Triple(root, path, offset)
                    id
                },
                action = { root, action, path, destination, overwrite ->
                    if (failNextAction) {
                        failNextAction = false
                        error("invalid path")
                    }
                    actions += FileAction(root, action, path, destination, overwrite)
                    "action-${++sequence}"
                },
                upload = { local, root, destination, overwrite ->
                    uploads += listOf(local, root, destination, overwrite)
                    "upload-${++sequence}"
                },
                download = { root, path, local, overwrite ->
                    downloads += Download(root, path, local, overwrite)
                    "download-${++sequence}"
                },
                openEditor = { _, _ -> "open-${++sequence}" },
                saveEditor = { root, path, content, hash, force ->
                    editorSaves += EditorSave(root, path, content, hash, force)
                    "save-${++sequence}"
                },
                cancelTransfer = cancelledTransfers::add,
                confirm = { title, _, action, kind -> confirmations += Confirmation(title, action, kind) },
                toast = { kind, title, message -> toasts += Toast(kind, title, message) },
            )
    }
}
