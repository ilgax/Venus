package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.protocol.FileAckPacket
import dev.ilgax.venus.protocol.FileActionPacket
import dev.ilgax.venus.protocol.FileCancelPacket
import dev.ilgax.venus.protocol.FileChunkPacket
import dev.ilgax.venus.protocol.FileDownloadStartPacket
import dev.ilgax.venus.protocol.FileFinishPacket
import dev.ilgax.venus.protocol.FileListGetPacket
import dev.ilgax.venus.protocol.FileListPacket
import dev.ilgax.venus.protocol.FileRootsGetPacket
import dev.ilgax.venus.protocol.FileRootsPacket
import dev.ilgax.venus.protocol.FileTransferReadyPacket
import dev.ilgax.venus.protocol.FileTransferResultPacket
import dev.ilgax.venus.protocol.FileUploadStartPacket
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendFilesHandlerTest {
    private lateinit var tempDir: java.io.File
    private lateinit var root: java.io.File
    private lateinit var platform: BackendPlatform
    private lateinit var sessions: SessionManager
    private lateinit var handler: BackendFilesHandler
    private val dataPackets = ConcurrentLinkedQueue<String>()
    private val transferPackets = ConcurrentLinkedQueue<String>()
    private val json = Json { ignoreUnknownKeys = true }
    private val player = BackendPlayer(UUID.randomUUID(), "Tester")

    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("venus-files-test").toFile()
        root = tempDir.resolve("root").apply { mkdirs() }
        platform = mockk(relaxed = true)
        sessions = SessionManager()
        val scheduler = mockk<BackendScheduler>()
        val later = slot<() -> Unit>()
        every { scheduler.runLater(any(), capture(later)) } answers {
            later.captured.invoke()
            BackendTask {}
        }
        every { scheduler.runRepeating(any(), any(), any()) } returns BackendTask {}
        every { platform.scheduler } returns scheduler
        every { platform.serverDirectory } returns tempDir.toPath()
        every { platform.config } returns
            BackendConfig(
                files =
                    BackendFileConfig(
                        roots = listOf(BackendFileRoot("files", "Files", "root", BackendFileRootMode.READ_WRITE)),
                        reservedFreeBytes = 0,
                    ),
            )
        every { platform.sendData(any(), any()) } answers { dataPackets += invocation.args[1] as String }
        every { platform.sendTransfer(any(), any()) } answers { transferPackets += invocation.args[1] as String }
        handler = BackendFilesHandler(platform, json, sessions)
    }

    @AfterTest
    fun teardown() {
        handler.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun `listing rejects traversal outside configured root`() {
        tempDir.resolve("outside").mkdirs()
        handler.handleList(
            player,
            json.encodeToString(
                FileListGetPacket.serializer(),
                FileListGetPacket("file_list_get", "request", "files", "../outside"),
            ),
        )

        val response = await(dataPackets)
        assertTrue(response.contains("invalid_path"))
        assertTrue(!response.contains(tempDir.absolutePath))
    }

    @Test
    fun `roots expose configured available directories`() {
        handler.handleRoots(
            player,
            json.encodeToString(FileRootsGetPacket.serializer(), FileRootsGetPacket("file_roots_get", "request")),
        )

        val packet = json.decodeFromString(FileRootsPacket.serializer(), await(dataPackets))
        assertEquals("request", packet.requestId)
        assertEquals(listOf("files"), packet.roots.map { it.id })
        assertTrue(packet.roots.single().writable)
    }

    @Test
    fun `listing returns directories first with file metadata`() {
        root.resolve("z-file.txt").writeText("content")
        root.resolve("a-directory").mkdir()
        handler.handleList(
            player,
            json.encodeToString(
                FileListGetPacket.serializer(),
                FileListGetPacket("file_list_get", "request", "files"),
            ),
        )

        val packet = json.decodeFromString(FileListPacket.serializer(), await(dataPackets))
        assertEquals(listOf("a-directory", "z-file.txt"), packet.entries.map { it.name })
        assertEquals("directory", packet.entries.first().kind)
        assertEquals(7, packet.entries.last().sizeBytes)
        assertTrue(packet.entries.last().editable)
    }

    @Test
    fun `file actions create move and recursively delete content`() {
        action("create_directory", "folder")
        action("create_file", "folder/file.txt")
        action("move", "folder/file.txt", "moved.txt")
        root.resolve("folder/nested").mkdirs()
        root.resolve("folder/nested/data.txt").writeText("data")
        action("delete", "folder")

        assertTrue(root.resolve("moved.txt").isFile)
        assertFalse(root.resolve("folder").exists())
    }

    @Test
    fun `unsupported action and existing upload return typed failures`() {
        root.resolve("existing.txt").writeText("existing")
        action("unsupported", "existing.txt", expectedSuccess = false)
        handler.handleUploadStart(
            player,
            json.encodeToString(
                FileUploadStartPacket.serializer(),
                FileUploadStartPacket("file_upload_start", "upload", "files", "existing.txt", 1),
            ),
        )

        assertTrue(await(dataPackets).contains("already_exists"))
    }

    @Test
    fun `read only root rejects mutation`() {
        every { platform.config } returns
            BackendConfig(
                files =
                    BackendFileConfig(
                        roots = listOf(BackendFileRoot("files", "Files", "root", BackendFileRootMode.READ_ONLY)),
                        reservedFreeBytes = 0,
                    ),
            )
        val packet = FileActionPacket("file_action", "request", "files", "create_file", "blocked.txt")
        handler.handleAction(
            player,
            json.encodeToString(FileActionPacket.serializer(), packet),
        )

        assertTrue(await(dataPackets).contains("read_only"))
        assertTrue(!root.resolve("blocked.txt").exists())
    }

    @Test
    fun `verified upload commits atomically`() {
        sessions.activate(player.uuid, KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public)
        val bytes = "hello venus".toByteArray()
        handler.handleUploadStart(
            player,
            json.encodeToString(
                FileUploadStartPacket.serializer(),
                FileUploadStartPacket("file_upload_start", "request", "files", "hello.txt", bytes.size.toLong()),
            ),
        )
        val readyJson = await(dataPackets)
        val ready = json.decodeFromString(FileTransferReadyPacket.serializer(), readyJson)
        handler.handleTransfer(
            player,
            json.encodeToString(
                FileChunkPacket.serializer(),
                FileChunkPacket("file_chunk", ready.transferId, 0, Base64.getEncoder().encodeToString(bytes)),
            ),
        )
        val ack = json.decodeFromString(FileAckPacket.serializer(), await(transferPackets))
        assertEquals(bytes.size.toLong(), ack.nextOffset)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        handler.handleTransfer(
            player,
            json.encodeToString(
                FileFinishPacket.serializer(),
                FileFinishPacket("file_finish", ready.transferId, bytes.size.toLong(), hash),
            ),
        )

        val result = await(transferPackets)
        assertTrue(result.contains("\"success\":true"))
        assertEquals("hello venus", root.resolve("hello.txt").readText())
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun `V27 player cleanup removes active upload partial file`() {
        sessions.activate(player.uuid, KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public)
        handler.handleUploadStart(
            player,
            json.encodeToString(
                FileUploadStartPacket.serializer(),
                FileUploadStartPacket("file_upload_start", "request", "files", "partial.txt", 10),
            ),
        )
        await(dataPackets)

        handler.cleanupPlayer(player.uuid)

        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun `V35 player cleanup discards queued file work`() {
        val executor = QueuedExecutorService()
        handler.shutdown()
        handler = BackendFilesHandler(platform, json, sessions, executor)
        val packet = FileActionPacket("file_action", "request", "files", "create_file", "stale.txt")
        handler.handleAction(player, json.encodeToString(FileActionPacket.serializer(), packet))

        handler.cleanupPlayer(player.uuid)
        executor.runNext()

        assertFalse(root.resolve("stale.txt").exists())
        assertTrue(dataPackets.isEmpty())
    }

    @Test
    fun `V35 file work that started before cleanup may finish`() {
        val executor = QueuedExecutorService()
        handler.shutdown()
        handler = BackendFilesHandler(platform, json, sessions, executor)
        val packet = FileActionPacket("file_action", "request", "files", "create_file", "started.txt")
        handler.handleAction(player, json.encodeToString(FileActionPacket.serializer(), packet))

        executor.runNext()
        handler.cleanupPlayer(player.uuid)

        assertTrue(root.resolve("started.txt").exists())
        assertTrue(await(dataPackets).contains("\"success\":true"))
    }

    @Test
    fun `V20 download success is rejected before server finishes streaming`() {
        sessions.activate(player.uuid, KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public)
        root.resolve("download.txt").writeText("content")
        handler.handleDownloadStart(
            player,
            json.encodeToString(
                FileDownloadStartPacket.serializer(),
                FileDownloadStartPacket("file_download_start", "request", "files", "download.txt"),
            ),
        )
        val ready = json.decodeFromString(FileTransferReadyPacket.serializer(), await(dataPackets))

        handler.handleTransfer(
            player,
            json.encodeToString(
                FileTransferResultPacket.serializer(),
                FileTransferResultPacket("file_transfer_result", ready.transferId, true, "ok", "done"),
            ),
        )

        assertTrue(await(transferPackets).contains("invalid_request"))
    }

    @Test
    fun `download streams chunks and completes after client result`() {
        sessions.activate(player.uuid, KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public)
        val bytes = "download content".toByteArray()
        root.resolve("download.txt").writeBytes(bytes)
        handler.handleDownloadStart(
            player,
            json.encodeToString(
                FileDownloadStartPacket.serializer(),
                FileDownloadStartPacket("file_download_start", "request", "files", "download.txt"),
            ),
        )
        val ready = json.decodeFromString(FileTransferReadyPacket.serializer(), await(dataPackets))

        handler.handleTransfer(
            player,
            json.encodeToString(FileAckPacket.serializer(), FileAckPacket("file_ack", ready.transferId, 0)),
        )
        val chunk = json.decodeFromString(FileChunkPacket.serializer(), await(transferPackets))
        assertTrue(Base64.getDecoder().decode(chunk.data).contentEquals(bytes))
        handler.handleTransfer(
            player,
            json.encodeToString(
                FileAckPacket.serializer(),
                FileAckPacket("file_ack", ready.transferId, bytes.size.toLong()),
            ),
        )
        val finish = json.decodeFromString(FileFinishPacket.serializer(), await(transferPackets))
        assertEquals(bytes.size.toLong(), finish.sizeBytes)
        handler.handleTransfer(
            player,
            json.encodeToString(
                FileTransferResultPacket.serializer(),
                FileTransferResultPacket("file_transfer_result", ready.transferId, true, "ok", "done"),
            ),
        )

        assertTrue(transferPackets.isEmpty())
    }

    @Test
    fun `inactive session transfer packets are ignored`() {
        handler.handleTransfer(
            player,
            json.encodeToString(FileCancelPacket.serializer(), FileCancelPacket("file_cancel", "missing")),
        )

        assertTrue(transferPackets.isEmpty())
    }

    private fun action(
        action: String,
        path: String,
        destination: String? = null,
        expectedSuccess: Boolean = true,
    ) {
        val packet = FileActionPacket("file_action", UUID.randomUUID().toString(), "files", action, path, destination)
        handler.handleAction(player, json.encodeToString(FileActionPacket.serializer(), packet))
        assertEquals(expectedSuccess, await(dataPackets).contains("\"success\":true"))
    }

    private fun await(queue: ConcurrentLinkedQueue<String>): String {
        repeat(200) {
            queue.poll()?.let { return it }
            Thread.sleep(10)
        }
        error("Timed out waiting for packet")
    }
}

private class QueuedExecutorService : AbstractExecutorService() {
    private val tasks = ArrayDeque<Runnable>()
    private var shutdown = false

    override fun execute(command: Runnable) {
        tasks.addLast(command)
    }

    fun runNext() {
        tasks.removeFirst().run()
    }

    override fun shutdown() {
        shutdown = true
    }

    override fun shutdownNow(): MutableList<Runnable> {
        shutdown = true
        return tasks.toMutableList().also { tasks.clear() }
    }

    override fun isShutdown(): Boolean = shutdown

    override fun isTerminated(): Boolean = shutdown && tasks.isEmpty()

    override fun awaitTermination(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = isTerminated
}
