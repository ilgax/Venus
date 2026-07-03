package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.protocol.FileAckPacket
import dev.ilgax.venus.protocol.FileActionPacket
import dev.ilgax.venus.protocol.FileChunkPacket
import dev.ilgax.venus.protocol.FileDownloadStartPacket
import dev.ilgax.venus.protocol.FileFinishPacket
import dev.ilgax.venus.protocol.FileListGetPacket
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
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun await(queue: ConcurrentLinkedQueue<String>): String {
        repeat(200) {
            queue.poll()?.let { return it }
            Thread.sleep(10)
        }
        error("Timed out waiting for packet")
    }
}
