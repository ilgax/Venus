package dev.ilgax.venus.transfer

import dev.ilgax.venus.protocol.FILE_CHUNK_BYTES
import dev.ilgax.venus.protocol.FileAckPacket
import dev.ilgax.venus.protocol.FileCancelPacket
import dev.ilgax.venus.protocol.FileChunkPacket
import dev.ilgax.venus.protocol.FileFinishPacket
import dev.ilgax.venus.protocol.FileTransferReadyPacket
import dev.ilgax.venus.protocol.FileTransferResultPacket
import dev.ilgax.venus.protocol.MAX_EDITABLE_FILE_BYTES
import dev.ilgax.venus.state.FileEditorState
import dev.ilgax.venus.state.FileTransferView
import dev.ilgax.venus.state.SessionState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.client.Minecraft
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ClientFileTransferManager(
    private val json: Json,
    private val sendRaw: (String) -> Unit,
    private val log: (String) -> Unit,
) {
    private val pendingUploads = ConcurrentHashMap<String, PendingUpload>()
    private val pendingDownloads = ConcurrentHashMap<String, PendingDownload>()
    private val transfers = ConcurrentHashMap<String, ClientTransfer>()

    fun prepareUpload(
        requestId: String,
        localPath: String,
        displayPath: String,
    ): Long {
        val source = resolveLocalPath(localPath)
        require(Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) { "Local source is not a regular file." }
        val size = Files.size(source)
        pendingUploads[requestId] = PendingUpload(source, displayPath, size, Files.getLastModifiedTime(source).toMillis(), false)
        return size
    }

    fun prepareDownload(
        requestId: String,
        localPath: String,
        displayPath: String,
        overwrite: Boolean,
    ) {
        val target = resolveLocalPath(localPath)
        val parent = target.parent ?: error("Local destination has no parent.")
        require(Files.isDirectory(parent)) { "Local destination directory does not exist." }
        require(overwrite || !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { "Local destination already exists." }
        pendingDownloads[requestId] = PendingDownload(target, displayPath, overwrite, null)
    }

    fun prepareEditorDownload(
        requestId: String,
        rootId: String,
        path: String,
    ) {
        pendingDownloads[requestId] = PendingDownload(null, path, overwrite = false, EditorTarget(rootId, path))
    }

    fun prepareEditorUpload(
        requestId: String,
        content: String,
        displayPath: String,
    ): Long {
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_EDITABLE_FILE_BYTES) { "Edited file exceeds 1 MiB." }
        require('\u0000' !in content) { "Edited file contains a NUL character." }
        val source = Files.createTempFile("venus-editor-", ".tmp")
        Files.write(source, bytes)
        pendingUploads[requestId] =
            PendingUpload(source, displayPath, bytes.size.toLong(), Files.getLastModifiedTime(source).toMillis(), true)
        return bytes.size.toLong()
    }

    fun discardPending(requestId: String) {
        pendingUploads.remove(requestId)?.takeIf { it.deleteSource }?.let { runCatching { Files.deleteIfExists(it.source) } }
        pendingDownloads.remove(requestId)
    }

    fun handleReady(packet: FileTransferReadyPacket) {
        when (packet.direction) {
            "upload" -> startUpload(packet)
            "download" -> startDownload(packet)
            else -> log("Venus: unknown file transfer direction ${packet.direction}")
        }
    }

    fun handle(data: String) {
        val type =
            runCatching {
                json
                    .parseToJsonElement(data)
                    .jsonObject["type"]
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull() ?: return
        try {
            when (type) {
                "file_ack" -> handleAck(json.decodeFromString(FileAckPacket.serializer(), data))
                "file_chunk" -> handleChunk(json.decodeFromString(FileChunkPacket.serializer(), data))
                "file_finish" -> handleFinish(json.decodeFromString(FileFinishPacket.serializer(), data))
                "file_transfer_result" -> handleResult(json.decodeFromString(FileTransferResultPacket.serializer(), data))
                "file_cancel" -> handleCancel(json.decodeFromString(FileCancelPacket.serializer(), data))
            }
        } catch (e: Exception) {
            log("Venus: invalid transfer packet - ${e.message}")
        }
    }

    fun cancel(transferId: String) {
        val transfer = transfers[transferId] ?: return
        send(FileCancelPacket("file_cancel", transferId, "cancelled"), FileCancelPacket.serializer())
        closeTransfer(transfer, "cancelled", "Transfer cancelled.")
    }

    fun reset() {
        pendingUploads.values.filter { it.deleteSource }.forEach { runCatching { Files.deleteIfExists(it.source) } }
        pendingUploads.clear()
        pendingDownloads.clear()
        transfers.values.toList().forEach { closeTransfer(it, "cancelled", "Disconnected.") }
    }

    private fun startUpload(packet: FileTransferReadyPacket) {
        val pending = pendingUploads.remove(packet.requestId) ?: return
        if (packet.sizeBytes != pending.size || transfers.values.any { it is UploadTransfer }) {
            if (pending.deleteSource) runCatching { Files.deleteIfExists(pending.source) }
            send(FileCancelPacket("file_cancel", packet.transferId, "invalid_request"), FileCancelPacket.serializer())
            return
        }
        try {
            val transfer =
                UploadTransfer(
                    packet.transferId,
                    pending.displayPath,
                    pending.source,
                    pending.size,
                    pending.modifiedAt,
                    pending.deleteSource,
                    BufferedInputStream(Files.newInputStream(pending.source)),
                )
            transfers[transfer.id] = transfer
            update(transfer, "running")
            sendNextUpload(transfer)
        } catch (e: Exception) {
            if (pending.deleteSource) runCatching { Files.deleteIfExists(pending.source) }
            send(FileCancelPacket("file_cancel", packet.transferId, "io_error"), FileCancelPacket.serializer())
            log("Venus: could not open upload source - ${e.message}")
        }
    }

    private fun startDownload(packet: FileTransferReadyPacket) {
        val pending = pendingDownloads.remove(packet.requestId) ?: return
        if (transfers.values.any { it is DownloadTransfer }) {
            send(FileCancelPacket("file_cancel", packet.transferId, "busy"), FileCancelPacket.serializer())
            return
        }
        var createdTemp: Path? = null
        try {
            if (pending.editor != null && packet.sizeBytes > MAX_EDITABLE_FILE_BYTES) error("File is too large for the editor.")
            val created = pending.target?.parent?.let(::openDownloadTemp)
            val temp = created?.path
            createdTemp = created?.path
            val output: OutputStream =
                if (pending.editor != null) {
                    ByteArrayOutputStream()
                } else {
                    created?.output ?: error("Missing download destination.")
                }
            val transfer =
                DownloadTransfer(
                    packet.transferId,
                    pending.displayPath,
                    pending.target,
                    temp,
                    pending.overwrite,
                    pending.editor,
                    packet.sizeBytes,
                    output,
                )
            transfers[transfer.id] = transfer
            update(transfer, "running")
            send(FileAckPacket("file_ack", transfer.id, 0), FileAckPacket.serializer())
        } catch (e: Exception) {
            createdTemp?.let { runCatching { Files.deleteIfExists(it) } }
            send(FileCancelPacket("file_cancel", packet.transferId, "io_error"), FileCancelPacket.serializer())
            log("Venus: could not open download destination - ${e.message}")
        }
    }

    private fun handleAck(packet: FileAckPacket) {
        val transfer = transfers[packet.transferId] as? UploadTransfer ?: return
        transfer.executor.execute {
            if (packet.nextOffset != transfer.offset) {
                failLocal(transfer, "invalid_request", "Unexpected upload acknowledgement.")
            } else {
                sendNextUpload(transfer)
            }
        }
    }

    private fun sendNextUpload(transfer: UploadTransfer) {
        transfer.executor.execute {
            try {
                val buffer = ByteArray(FILE_CHUNK_BYTES)
                val read = transfer.input.read(buffer)
                if (read < 0) {
                    if (Files.size(transfer.source) != transfer.size ||
                        Files.getLastModifiedTime(transfer.source).toMillis() != transfer.modifiedAt
                    ) {
                        failLocal(transfer, "conflict", "Local source changed during upload.")
                        return@execute
                    }
                    transfer.input.close()
                    send(
                        FileFinishPacket("file_finish", transfer.id, transfer.size, transfer.digest.digest().toHex()),
                        FileFinishPacket.serializer(),
                    )
                    return@execute
                }
                val bytes = buffer.copyOf(read)
                val offset = transfer.offset
                transfer.offset += read
                transfer.digest.update(bytes)
                update(transfer, "running")
                send(
                    FileChunkPacket("file_chunk", transfer.id, offset, Base64.getEncoder().encodeToString(bytes)),
                    FileChunkPacket.serializer(),
                )
            } catch (e: Exception) {
                failLocal(transfer, "io_error", "Could not read local upload source.")
            }
        }
    }

    private fun handleChunk(packet: FileChunkPacket) {
        val transfer = transfers[packet.transferId] as? DownloadTransfer ?: return
        transfer.executor.execute {
            try {
                val bytes = Base64.getDecoder().decode(packet.data)
                if (bytes.size > FILE_CHUNK_BYTES || packet.offset != transfer.offset || transfer.offset + bytes.size > transfer.size) {
                    failLocal(transfer, "invalid_request", "Unexpected download chunk.")
                    return@execute
                }
                transfer.output.write(bytes)
                transfer.digest.update(bytes)
                transfer.offset += bytes.size
                update(transfer, "running")
                send(FileAckPacket("file_ack", transfer.id, transfer.offset), FileAckPacket.serializer())
            } catch (e: Exception) {
                failLocal(transfer, "io_error", "Could not write local download.")
            }
        }
    }

    private fun handleFinish(packet: FileFinishPacket) {
        val transfer = transfers[packet.transferId] as? DownloadTransfer ?: return
        transfer.executor.execute {
            try {
                if (packet.sizeBytes != transfer.size || transfer.offset != transfer.size) error("size mismatch")
                val hash = transfer.digest.digest().toHex()
                if (!hash.equals(packet.sha256, ignoreCase = true)) error("checksum mismatch")
                transfer.output.flush()
                transfer.output.close()
                if (transfer.editor != null) {
                    val bytes = (transfer.output as ByteArrayOutputStream).toByteArray()
                    require(bytes.none { it == 0.toByte() }) { "File contains NUL bytes." }
                    val content =
                        Charsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(bytes))
                            .toString()
                    SessionState.openFileEditor(FileEditorState(transfer.editor.rootId, transfer.editor.path, content, hash))
                } else {
                    val target = transfer.target ?: error("missing destination")
                    val temp = transfer.temp ?: error("missing partial file")
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !transfer.overwrite) error("destination exists")
                    val options = mutableListOf(StandardCopyOption.ATOMIC_MOVE)
                    if (transfer.overwrite) options += StandardCopyOption.REPLACE_EXISTING
                    Files.move(temp, target, *options.toTypedArray())
                }
                send(
                    FileTransferResultPacket("file_transfer_result", transfer.id, true, "ok", "Download completed.", hash),
                    FileTransferResultPacket.serializer(),
                )
                completeTransfer(transfer, "Download completed.")
            } catch (e: Exception) {
                failLocal(transfer, "integrity_failed", "Download could not be verified or committed.")
            }
        }
    }

    private fun handleResult(packet: FileTransferResultPacket) {
        val transfer = transfers[packet.transferId] ?: return
        if (packet.success) {
            if (transfer is UploadTransfer && transfer.deleteSource) SessionState.closeFileEditor()
            completeTransfer(transfer, packet.message)
        } else {
            closeTransfer(transfer, packet.code, packet.message)
        }
    }

    private fun handleCancel(packet: FileCancelPacket) {
        transfers[packet.transferId]?.let { closeTransfer(it, "cancelled", packet.reason) }
    }

    private fun failLocal(
        transfer: ClientTransfer,
        code: String,
        message: String,
    ) {
        send(FileCancelPacket("file_cancel", transfer.id, code), FileCancelPacket.serializer())
        closeTransfer(transfer, code, message)
    }

    private fun completeTransfer(
        transfer: ClientTransfer,
        message: String,
    ) {
        if (!transfer.closed.compareAndSet(false, true)) return
        transfers.remove(transfer.id)
        runCatching { transfer.close() }
        if (transfer is UploadTransfer && transfer.deleteSource) runCatching { Files.deleteIfExists(transfer.source) }
        transfer.executor.shutdown()
        update(transfer, "completed", message)
    }

    private fun closeTransfer(
        transfer: ClientTransfer,
        status: String,
        message: String,
    ) {
        if (!transfer.closed.compareAndSet(false, true)) return
        transfers.remove(transfer.id)
        runCatching { transfer.close() }
        if (transfer is UploadTransfer && transfer.deleteSource) runCatching { Files.deleteIfExists(transfer.source) }
        if (transfer is DownloadTransfer && transfer.temp != null) runCatching { Files.deleteIfExists(transfer.temp) }
        transfer.executor.shutdownNow()
        update(transfer, status, message)
    }

    private fun update(
        transfer: ClientTransfer,
        status: String,
        message: String = "",
    ) {
        SessionState.updateFileTransfer(
            FileTransferView(
                transfer.id,
                transfer.direction,
                transfer.displayPath,
                transfer.offset,
                transfer.size,
                transfer.bytesPerSecond(),
                status,
                message,
            ),
        )
    }

    private fun <T> send(
        packet: T,
        serializer: kotlinx.serialization.KSerializer<T>,
    ) {
        val data = json.encodeToString(serializer, packet)
        Minecraft.getInstance().execute { sendRaw(data) }
    }

    private fun resolveLocalPath(raw: String): Path {
        require(raw.isNotBlank()) { "Local path is required." }
        val path = Path.of(raw)
        return (
            if (path.isAbsolute) {
                path
            } else {
                Minecraft
                    .getInstance()
                    .gameDirectory
                    .toPath()
                    .resolve(path)
            }
        ).toAbsolutePath().normalize()
    }

    private data class PendingUpload(
        val source: Path,
        val displayPath: String,
        val size: Long,
        val modifiedAt: Long,
        val deleteSource: Boolean,
    )

    private data class PendingDownload(
        val target: Path?,
        val displayPath: String,
        val overwrite: Boolean,
        val editor: EditorTarget?,
    )

    private data class EditorTarget(
        val rootId: String,
        val path: String,
    )

    private sealed class ClientTransfer(
        val id: String,
        val displayPath: String,
        val size: Long,
        val direction: String,
    ) {
        val executor: ExecutorService =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "venus-client-transfer-$id").apply {
                    isDaemon =
                        true
                }
            }
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
        val closed = AtomicBoolean(false)
        private val startedAt = System.currentTimeMillis()
        var offset: Long = 0

        fun bytesPerSecond(): Long {
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1)
            return offset * 1000L / elapsed
        }

        abstract fun close()
    }

    private class UploadTransfer(
        id: String,
        displayPath: String,
        val source: Path,
        size: Long,
        val modifiedAt: Long,
        val deleteSource: Boolean,
        val input: BufferedInputStream,
    ) : ClientTransfer(id, displayPath, size, "upload") {
        override fun close() = input.close()
    }

    private class DownloadTransfer(
        id: String,
        displayPath: String,
        val target: Path?,
        val temp: Path?,
        val overwrite: Boolean,
        val editor: EditorTarget?,
        size: Long,
        val output: OutputStream,
    ) : ClientTransfer(id, displayPath, size, "download") {
        override fun close() = output.close()
    }
}

internal data class DownloadTemp(
    val path: Path,
    val output: OutputStream,
)

internal fun openDownloadTemp(parent: Path): DownloadTemp {
    val path = Files.createTempFile(parent, ".venus-", ".part")
    return try {
        DownloadTemp(
            path,
            BufferedOutputStream(
                Files.newOutputStream(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
            ),
        )
    } catch (e: Exception) {
        Files.deleteIfExists(path)
        throw e
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
