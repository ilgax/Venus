package dev.ilgax.venus.transfer

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClientFileTransferManagerTest {
    @Test
    fun `V31 download temp file uses local random name inside target directory`() {
        val parent = createTempDirectory("venus-download")
        val temp = openDownloadTemp(parent)
        try {
            temp.output.write("content".toByteArray())
            temp.output.close()

            assertEquals(parent, temp.path.parent)
            assertTrue(
                temp.path.fileName
                    .toString()
                    .startsWith(".venus-"),
            )
            assertTrue(Files.isRegularFile(temp.path))
        } finally {
            runCatching { temp.output.close() }
            Files.deleteIfExists(temp.path)
            Files.deleteIfExists(parent)
        }
    }
}
