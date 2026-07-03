package dev.ilgax.venus.protocol

import kotlinx.serialization.json.Json
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FilePacketsTest {
    private val json = Json

    @Test
    fun `file chunk stays below packet limit`() {
        val data = Base64.getEncoder().encodeToString(ByteArray(FILE_CHUNK_BYTES) { 0x5a })
        val encoded = json.encodeToString(FileChunkPacket.serializer(), FileChunkPacket("file_chunk", "transfer", 0, data))

        assertTrue(encoded.toByteArray(Charsets.UTF_8).size < MAX_PACKET_SIZE)
    }

    @Test
    fun `V19 file chunk requires transfer progress`() {
        assertFailsWith<IllegalArgumentException> {
            FileChunkPacket("file_chunk", "transfer", 0, "")
        }
    }

    @Test
    fun `file offsets and sizes use long range without file cap`() {
        val packet = FileUploadStartPacket("file_upload_start", "request", "root", "large.bin", Long.MAX_VALUE)
        val decoded =
            json.decodeFromString(
                FileUploadStartPacket.serializer(),
                json.encodeToString(FileUploadStartPacket.serializer(), packet),
            )

        assertEquals(Long.MAX_VALUE, decoded.sizeBytes)
    }

    @Test
    fun `file listing bounds pagination`() {
        assertFailsWith<IllegalArgumentException> {
            FileListGetPacket("file_list_get", "request", "root", limit = MAX_FILE_LIST_ENTRIES + 1)
        }
    }
}
