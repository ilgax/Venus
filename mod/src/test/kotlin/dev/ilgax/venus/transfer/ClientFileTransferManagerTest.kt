package dev.ilgax.venus.transfer

import dev.ilgax.venus.protocol.FileAckPacket
import dev.ilgax.venus.protocol.FileCancelPacket
import dev.ilgax.venus.protocol.FileChunkPacket
import dev.ilgax.venus.protocol.FileFinishPacket
import dev.ilgax.venus.protocol.FileTransferReadyPacket
import dev.ilgax.venus.protocol.FileTransferResultPacket
import dev.ilgax.venus.protocol.MAX_EDITABLE_FILE_BYTES
import dev.ilgax.venus.state.SessionState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientFileTransferManagerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @AfterTest
    fun resetState() {
        SessionState.reset()
    }

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

    @Test
    fun `upload streams chunks then completes from server result`() {
        withFixture { fixture, directory ->
            val source = directory.resolve("upload.txt")
            val bytes = "hello venus".toByteArray()
            Files.write(source, bytes)
            assertEquals(bytes.size.toLong(), fixture.manager.prepareUpload("request", source.toString(), "upload.txt"))

            fixture.manager.handleReady(ready("request", "transfer", "upload", bytes.size.toLong()))
            val chunk = fixture.await(FileChunkPacket.serializer())
            assertEquals(0, chunk.offset)
            assertTrue(Base64.getDecoder().decode(chunk.data).contentEquals(bytes))
            fixture.manager.handle(
                json.encodeToString(
                    FileAckPacket.serializer(),
                    FileAckPacket("file_ack", "transfer", bytes.size.toLong()),
                ),
            )
            val finish = fixture.await(FileFinishPacket.serializer())
            assertEquals(bytes.sha256(), finish.sha256)
            fixture.manager.handle(
                json.encodeToString(
                    FileTransferResultPacket.serializer(),
                    FileTransferResultPacket("file_transfer_result", "transfer", true, "ok", "Uploaded."),
                ),
            )

            assertEquals("completed", SessionState.activeFileTransfers.last().status)
            assertEquals("Uploaded.", SessionState.activeFileTransfers.last().message)
        }
    }

    @Test
    fun `upload rejects mismatched ready size and concurrent upload`() {
        withFixture { fixture, directory ->
            val first = directory.resolve("first.txt")
            val second = directory.resolve("second.txt")
            Files.writeString(first, "first")
            Files.writeString(second, "second")
            fixture.manager.prepareUpload("bad", first.toString(), "first")
            fixture.manager.handleReady(ready("bad", "bad-transfer", "upload", 99))
            assertEquals("invalid_request", fixture.await(FileCancelPacket.serializer()).reason)

            fixture.manager.prepareUpload("first", first.toString(), "first")
            fixture.manager.prepareUpload("second", second.toString(), "second")
            fixture.manager.handleReady(ready("first", "first-transfer", "upload", 5))
            fixture.await(FileChunkPacket.serializer())
            fixture.manager.handleReady(ready("second", "second-transfer", "upload", 6))

            assertEquals("invalid_request", fixture.await(FileCancelPacket.serializer()).reason)
            fixture.manager.cancel("first-transfer")
            assertEquals("cancelled", fixture.await(FileCancelPacket.serializer()).reason)
        }
    }

    @Test
    fun `changed upload source fails after acknowledgement`() {
        withFixture { fixture, directory ->
            val source = directory.resolve("upload.txt")
            Files.writeString(source, "initial")
            fixture.manager.prepareUpload("request", source.toString(), "upload")
            fixture.manager.handleReady(ready("request", "transfer", "upload", 7))
            val chunk = fixture.await(FileChunkPacket.serializer())
            val modified = Files.getLastModifiedTime(source).toMillis()
            Files.setLastModifiedTime(source, FileTime.fromMillis(modified + 5000))

            fixture.manager.handle(
                json.encodeToString(
                    FileAckPacket.serializer(),
                    FileAckPacket("file_ack", "transfer", chunk.data.decodedSize()),
                ),
            )

            assertEquals("conflict", fixture.await(FileCancelPacket.serializer()).reason)
            assertEquals("conflict", SessionState.activeFileTransfers.last().status)
        }
    }

    @Test
    fun `download verifies and atomically commits file`() {
        withFixture { fixture, directory ->
            val target = directory.resolve("download.txt")
            val bytes = "downloaded".toByteArray()
            fixture.manager.prepareDownload("request", target.toString(), "download.txt", overwrite = false)
            fixture.manager.handleReady(ready("request", "transfer", "download", bytes.size.toLong()))
            assertEquals(0, fixture.await(FileAckPacket.serializer()).nextOffset)

            fixture.manager.handle(json.encodeToString(FileChunkPacket.serializer(), chunk("transfer", 0, bytes)))
            assertEquals(bytes.size.toLong(), fixture.await(FileAckPacket.serializer()).nextOffset)
            fixture.manager.handle(
                json.encodeToString(
                    FileFinishPacket.serializer(),
                    FileFinishPacket("file_finish", "transfer", bytes.size.toLong(), bytes.sha256()),
                ),
            )

            assertTrue(fixture.await(FileTransferResultPacket.serializer()).success)
            assertEquals("downloaded", Files.readString(target))
            assertEquals("completed", SessionState.activeFileTransfers.last().status)
        }
    }

    @Test
    fun `editor download opens verified UTF8 content`() {
        withFixture { fixture, _ ->
            val bytes = "motd=Venus".toByteArray()
            fixture.manager.prepareEditorDownload("request", "config", "server.properties")
            fixture.manager.handleReady(ready("request", "transfer", "download", bytes.size.toLong()))
            fixture.await(FileAckPacket.serializer())
            fixture.manager.handle(json.encodeToString(FileChunkPacket.serializer(), chunk("transfer", 0, bytes)))
            fixture.await(FileAckPacket.serializer())
            fixture.manager.handle(
                json.encodeToString(
                    FileFinishPacket.serializer(),
                    FileFinishPacket("file_finish", "transfer", bytes.size.toLong(), bytes.sha256()),
                ),
            )

            assertTrue(fixture.await(FileTransferResultPacket.serializer()).success)
            assertEquals("motd=Venus", SessionState.fileEditor?.content)
            assertEquals("config", SessionState.fileEditor?.rootId)
        }
    }

    @Test
    fun `download rejects invalid chunk and removes partial file`() {
        withFixture { fixture, directory ->
            val target = directory.resolve("download.txt")
            fixture.manager.prepareDownload("request", target.toString(), "download", overwrite = false)
            fixture.manager.handleReady(ready("request", "transfer", "download", 2))
            fixture.await(FileAckPacket.serializer())

            fixture.manager.handle(json.encodeToString(FileChunkPacket.serializer(), chunk("transfer", 1, byteArrayOf(1))))

            assertEquals("invalid_request", fixture.await(FileCancelPacket.serializer()).reason)
            assertFalse(Files.exists(target))
            assertTrue(Files.list(directory).use { stream -> stream.noneMatch { it.fileName.toString().endsWith(".part") } })
        }
    }

    @Test
    fun `download integrity mismatch fails without committing`() {
        withFixture { fixture, directory ->
            val target = directory.resolve("download.txt")
            val bytes = "data".toByteArray()
            fixture.manager.prepareDownload("request", target.toString(), "download", overwrite = false)
            fixture.manager.handleReady(ready("request", "transfer", "download", bytes.size.toLong()))
            fixture.await(FileAckPacket.serializer())
            fixture.manager.handle(json.encodeToString(FileChunkPacket.serializer(), chunk("transfer", 0, bytes)))
            fixture.await(FileAckPacket.serializer())

            fixture.manager.handle(
                json.encodeToString(
                    FileFinishPacket.serializer(),
                    FileFinishPacket("file_finish", "transfer", bytes.size.toLong(), "0".repeat(64)),
                ),
            )

            assertEquals("integrity_failed", fixture.await(FileCancelPacket.serializer()).reason)
            assertFalse(Files.exists(target))
        }
    }

    @Test
    fun `pending input validation and discard cover local setup`() {
        withFixture { fixture, directory ->
            val existing = directory.resolve("existing.txt")
            Files.writeString(existing, "data")

            assertFailsWith<IllegalArgumentException> { fixture.manager.prepareUpload("request", directory.toString(), "directory") }
            assertFailsWith<IllegalArgumentException> {
                fixture.manager.prepareDownload("request", existing.toString(), "existing", overwrite = false)
            }
            assertFailsWith<IllegalArgumentException> { fixture.manager.prepareDownload("request", "", "blank", overwrite = false) }
            assertFailsWith<IllegalArgumentException> {
                fixture.manager.prepareEditorUpload("request", "nul\u0000byte", "editor")
            }
            assertFailsWith<IllegalArgumentException> {
                fixture.manager.prepareEditorUpload("request", "x".repeat(MAX_EDITABLE_FILE_BYTES.toInt() + 1), "editor")
            }
            fixture.manager.prepareEditorUpload("discard", "temporary", "editor")
            fixture.manager.discardPending("discard")
            fixture.manager.handleReady(ready("discard", "transfer", "upload", 9))

            assertTrue(fixture.sent.isEmpty())
        }
    }

    @Test
    fun `unknown directions and malformed transfer packets are logged`() {
        withFixture { fixture, _ ->
            fixture.manager.handleReady(ready("request", "transfer", "sideways", 0))
            fixture.manager.handle("not-json")
            fixture.manager.handle("""{"type":"file_ack","transfer_id":"transfer"}""")

            assertTrue(fixture.logs.any { it.contains("unknown file transfer direction") })
            assertTrue(fixture.logs.any { it.contains("invalid transfer packet") })
        }
    }

    private fun withFixture(block: (TransferFixture, Path) -> Unit) {
        val directory = createTempDirectory("venus-transfer-test")
        val fixture = TransferFixture()
        try {
            block(fixture, directory)
        } finally {
            fixture.manager.reset()
            directory.toFile().deleteRecursively()
        }
    }

    private inner class TransferFixture {
        val sent = ConcurrentLinkedQueue<String>()
        val logs = ConcurrentLinkedQueue<String>()
        val manager =
            ClientFileTransferManager(json, sent::add, logs::add).apply {
                dispatch = { task -> task() }
            }

        fun <T> await(serializer: KSerializer<T>): T {
            repeat(300) {
                sent.poll()?.let { return json.decodeFromString(serializer, it) }
                Thread.sleep(10)
            }
            error("Timed out waiting for transfer packet")
        }
    }

    private fun ready(
        requestId: String,
        transferId: String,
        direction: String,
        size: Long,
    ) = FileTransferReadyPacket("file_transfer_ready", requestId, transferId, direction, size)

    private fun chunk(
        transferId: String,
        offset: Long,
        bytes: ByteArray,
    ) = FileChunkPacket("file_chunk", transferId, offset, Base64.getEncoder().encodeToString(bytes))

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHex()

    private fun String.decodedSize(): Long =
        Base64
            .getDecoder()
            .decode(this)
            .size
            .toLong()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
