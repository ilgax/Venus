package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.protocol.FileListPacket
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilesPageStateTest {
    @Test
    fun `V32 roots are requested when authentication becomes active`() {
        assertFalse(shouldRequestFileRoots(sessionActive = false, requestedRoots = false))
        assertTrue(shouldRequestFileRoots(sessionActive = true, requestedRoots = false))
        assertFalse(shouldRequestFileRoots(sessionActive = true, requestedRoots = true))
    }

    @Test
    fun `V32 file list must match latest request`() {
        val packet = FileListPacket("file_list", "latest", "root", "path", emptyList(), null)

        assertTrue(isCurrentFileList(packet, "root", "path", "latest"))
        assertFalse(isCurrentFileList(packet, "root", "path", "stale"))
    }
}
