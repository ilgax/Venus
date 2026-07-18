package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.protocol.FILE_CHUNK_BYTES
import dev.ilgax.venus.protocol.FileAckPacket
import dev.ilgax.venus.protocol.FileActionPacket
import dev.ilgax.venus.protocol.FileActionResultPacket
import dev.ilgax.venus.protocol.FileCancelPacket
import dev.ilgax.venus.protocol.FileChunkPacket
import dev.ilgax.venus.protocol.FileDownloadStartPacket
import dev.ilgax.venus.protocol.FileEntryPacket
import dev.ilgax.venus.protocol.FileFinishPacket
import dev.ilgax.venus.protocol.FileListGetPacket
import dev.ilgax.venus.protocol.FileListPacket
import dev.ilgax.venus.protocol.FileRootPacket
import dev.ilgax.venus.protocol.FileRootsGetPacket
import dev.ilgax.venus.protocol.FileRootsPacket
import dev.ilgax.venus.protocol.FileTransferReadyPacket
import dev.ilgax.venus.protocol.FileTransferResultPacket
import dev.ilgax.venus.protocol.FileUploadStartPacket
import dev.ilgax.venus.protocol.MAX_EDITABLE_FILE_BYTES
import dev.ilgax.venus.protocol.MAX_PACKET_SIZE
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class BackendFilesHandler(
    private val platform: BackendPlatform,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val ioExecutor: ExecutorService =
        Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "venus-files").apply { isDaemon = true }
        },
) {
    private val transfers = ConcurrentHashMap<String, ServerTransfer>()
    private val stateLock = Any()
    private val sessionGenerations = WeakHashMap<UUID, Long>()
    private var reservedUploadBytes = 0L
    private val timeoutTask =
        platform.scheduler.runRepeating(20, 20) {
            val now = System.currentTimeMillis()
            transfers.values
                .filter { now - it.lastActivity > platform.config.files.idleTimeoutSeconds * 1000L }
                .forEach { cancelTransfer(it, "timeout", notify = true) }
        }

    fun handleRoots(
        player: BackendPlayer,
        data: String,
    ) {
        val packet = decode(FileRootsGetPacket.serializer(), data, player) ?: return
        val roots =
            platform.config.files.roots.mapNotNull { configured ->
                runCatching { resolveRoot(configured) }.getOrNull()?.let {
                    FileRootPacket(configured.id, configured.label, configured.mode == BackendFileRootMode.READ_WRITE)
                }
            }
        sendData(player, FileRootsPacket("file_roots", packet.requestId, roots), FileRootsPacket.serializer())
    }

    fun handleList(
        player: BackendPlayer,
        data: String,
    ) {
        val packet = decode(FileListGetPacket.serializer(), data, player) ?: return
        executeForSession(player) {
            try {
                val root = configuredRoot(packet.rootId)
                val directory = resolve(root, packet.path)
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) fail("not_directory", "Path is not a directory.")
                val entries =
                    Files.newDirectoryStream(directory).use { stream ->
                        stream
                            .map { entry -> toEntry(root, entry) }
                            .sortedWith(compareBy<FileEntryPacket>({ it.kind != "directory" }, { it.name.lowercase() }, { it.name }))
                    }
                val page = mutableListOf<FileEntryPacket>()
                var cursor = packet.offset.coerceAtMost(entries.size)
                while (cursor < entries.size && page.size < packet.limit) {
                    page += entries[cursor]
                    val candidate = FileListPacket("file_list", packet.requestId, root.id, cleanRelative(packet.path), page, cursor + 1)
                    if (json.encodeToString(FileListPacket.serializer(), candidate).toByteArray(Charsets.UTF_8).size > MAX_PACKET_SIZE) {
                        page.removeLast()
                        break
                    }
                    cursor += 1
                }
                val next = if (cursor < entries.size) cursor else null
                sendData(
                    player,
                    FileListPacket("file_list", packet.requestId, root.id, cleanRelative(packet.path), page, next),
                    FileListPacket.serializer(),
                )
            } catch (e: FileFailure) {
                sendFailure(player, packet.requestId, e)
            } catch (e: Exception) {
                platform.logger.warning("File list failed for ${player.name}: ${e.message}")
                sendFailure(player, packet.requestId, FileFailure("io_error", "Could not list directory."))
            }
        }
    }

    fun handleAction(
        player: BackendPlayer,
        data: String,
    ) {
        val packet = decode(FileActionPacket.serializer(), data, player) ?: return
        executeForSession(player) {
            try {
                val root = configuredRoot(packet.rootId)
                requireWritable(root)
                when (packet.action) {
                    "create_file" -> create(root, packet.path, directory = false)
                    "create_directory" -> create(root, packet.path, directory = true)
                    "move" ->
                        move(
                            root,
                            packet.path,
                            packet.destination ?: fail("invalid_request", "Destination is required."),
                            packet.overwrite,
                        )
                    "delete" -> delete(root, packet.path)
                    else -> fail("invalid_request", "Unsupported file action.")
                }
                sendData(
                    player,
                    FileActionResultPacket("file_action_result", packet.requestId, true, "ok", "File action completed."),
                    FileActionResultPacket.serializer(),
                )
            } catch (e: FileFailure) {
                sendFailure(player, packet.requestId, e)
            } catch (e: Exception) {
                platform.logger.warning("File action failed for ${player.name}: ${e.message}")
                sendFailure(player, packet.requestId, FileFailure("io_error", "File action failed."))
            }
        }
    }

    fun handleUploadStart(
        player: BackendPlayer,
        data: String,
    ) {
        val packet = decode(FileUploadStartPacket.serializer(), data, player) ?: return
        executeForSession(player) {
            try {
                val root = configuredRoot(packet.rootId)
                requireWritable(root)
                val target = resolve(root, packet.path, leafMayNotExist = true)
                val parent = target.parent ?: fail("invalid_path", "Destination has no parent.")
                ensureExistingDirectory(root, parent)
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) &&
                    !packet.overwrite
                ) {
                    fail("already_exists", "Destination already exists.")
                }
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    fail("not_file", "Destination is not a regular file.")
                }
                val expectedSha256 = packet.expectedSha256
                if (expectedSha256 != null && currentSha256(target) != expectedSha256.lowercase()) {
                    fail("conflict", "The file changed since it was opened.")
                }
                val transferId = UUID.randomUUID().toString()
                val temp = parent.resolve(".venus-$transferId.part")
                reserveUpload(player, packet.sizeBytes, parent)
                var transfer: UploadTransfer? = null
                try {
                    transfer =
                        UploadTransfer(
                            transferId,
                            packet.requestId,
                            player,
                            root,
                            target,
                            temp,
                            packet.sizeBytes,
                            packet.overwrite,
                            packet.expectedSha256,
                            BufferedOutputStream(Files.newOutputStream(temp)),
                        )
                    registerTransfer(transfer)
                    sendReady(transfer, "upload", packet.sizeBytes)
                } catch (e: Exception) {
                    runCatching { transfer?.close() }
                    releaseUpload(packet.sizeBytes)
                    Files.deleteIfExists(temp)
                    throw e
                }
            } catch (e: FileFailure) {
                sendFailure(player, packet.requestId, e)
            } catch (e: Exception) {
                platform.logger.warning("Upload start failed for ${player.name}: ${e.message}")
                sendFailure(player, packet.requestId, FileFailure("io_error", "Could not start upload."))
            }
        }
    }

    fun handleDownloadStart(
        player: BackendPlayer,
        data: String,
    ) {
        val packet = decode(FileDownloadStartPacket.serializer(), data, player) ?: return
        executeForSession(player) {
            try {
                val root = configuredRoot(packet.rootId)
                val source = resolve(root, packet.path)
                if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) fail("not_file", "Path is not a regular file.")
                val attrs = Files.readAttributes(source, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                val transfer =
                    DownloadTransfer(
                        UUID.randomUUID().toString(),
                        packet.requestId,
                        player,
                        source,
                        attrs.size(),
                        attrs.lastModifiedTime().toMillis(),
                        BufferedInputStream(Files.newInputStream(source)),
                    )
                try {
                    registerTransfer(transfer)
                    sendReady(transfer, "download", attrs.size())
                } catch (e: Exception) {
                    runCatching { transfer.close() }
                    throw e
                }
            } catch (e: FileFailure) {
                sendFailure(player, packet.requestId, e)
            } catch (e: Exception) {
                platform.logger.warning("Download start failed for ${player.name}: ${e.message}")
                sendFailure(player, packet.requestId, FileFailure("io_error", "Could not start download."))
            }
        }
    }

    fun handleTransfer(
        player: BackendPlayer,
        data: String,
    ) {
        if (!sessionManager.isActive(player.uuid)) return
        val type =
            runCatching {
                json
                    .parseToJsonElement(data)
                    .jsonObject["type"]
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull() ?: return
        when (type) {
            "file_chunk" -> decode(FileChunkPacket.serializer(), data, player)?.let { handleUploadChunk(player, it) }
            "file_ack" -> decode(FileAckPacket.serializer(), data, player)?.let { handleDownloadAck(player, it) }
            "file_finish" -> decode(FileFinishPacket.serializer(), data, player)?.let { handleUploadFinish(player, it) }
            "file_transfer_result" -> decode(FileTransferResultPacket.serializer(), data, player)?.let { handleDownloadResult(player, it) }
            "file_cancel" ->
                decode(FileCancelPacket.serializer(), data, player)?.let { packet ->
                    ownedTransfer(player, packet.transferId)?.let { cancelTransfer(it, "cancelled", notify = false) }
                }
        }
    }

    fun cleanupPlayer(uuid: UUID) {
        synchronized(stateLock) {
            sessionGenerations[uuid] = sessionGenerations.getOrDefault(uuid, 0L) + 1L
        }
        transfers.values.filter { it.player.uuid == uuid }.forEach { cancelTransfer(it, "cancelled", notify = false) }
    }

    fun reload() {
        transfers.values.forEach { cancelTransfer(it, "cancelled", notify = true) }
    }

    fun shutdown() {
        timeoutTask.cancel()
        reload()
        ioExecutor.shutdownNow()
    }

    private fun executeForSession(
        player: BackendPlayer,
        action: () -> Unit,
    ) {
        val generation = synchronized(stateLock) { sessionGenerations.getOrDefault(player.uuid, 0L) }
        ioExecutor.execute {
            val mayStart =
                synchronized(stateLock) {
                    sessionGenerations.getOrDefault(player.uuid, 0L) == generation
                }
            if (mayStart) action()
        }
    }

    private fun handleUploadChunk(
        player: BackendPlayer,
        packet: FileChunkPacket,
    ) {
        val transfer = ownedTransfer(player, packet.transferId) as? UploadTransfer ?: return
        transfer.executor.execute {
            try {
                requireActive(transfer)
                val bytes = Base64.getDecoder().decode(packet.data)
                if (bytes.isEmpty() ||
                    bytes.size > FILE_CHUNK_BYTES ||
                    packet.offset != transfer.offset ||
                    transfer.offset + bytes.size > transfer.size
                ) {
                    fail("invalid_request", "Unexpected upload chunk.")
                }
                transfer.output.write(bytes)
                transfer.digest.update(bytes)
                transfer.offset += bytes.size
                transfer.touch()
                sendTransfer(player, FileAckPacket("file_ack", transfer.id, transfer.offset), FileAckPacket.serializer())
            } catch (e: Exception) {
                cancelTransfer(transfer, if (e is FileFailure) e.code else "io_error", notify = true)
            }
        }
    }

    private fun handleUploadFinish(
        player: BackendPlayer,
        packet: FileFinishPacket,
    ) {
        val transfer = ownedTransfer(player, packet.transferId) as? UploadTransfer ?: return
        transfer.executor.execute {
            try {
                requireActive(transfer)
                if (packet.sizeBytes != transfer.size ||
                    transfer.offset != transfer.size
                ) {
                    fail("integrity_failed", "Upload size did not match.")
                }
                val actualHash = transfer.digest.digest().toHex()
                if (!actualHash.equals(packet.sha256, ignoreCase = true)) fail("integrity_failed", "Upload checksum did not match.")
                transfer.output.flush()
                transfer.output.close()
                val currentRoot = configuredRoot(transfer.root.id)
                requireWritable(currentRoot)
                resolve(currentRoot, relative(currentRoot, transfer.target), leafMayNotExist = true)
                if (Files.exists(transfer.target, LinkOption.NOFOLLOW_LINKS) &&
                    !transfer.overwrite
                ) {
                    fail("already_exists", "Destination already exists.")
                }
                if (transfer.expectedSha256 != null && currentSha256(transfer.target) != transfer.expectedSha256.lowercase()) {
                    fail("conflict", "The file changed since it was opened.")
                }
                requireActive(transfer)
                val options = mutableListOf(StandardCopyOption.ATOMIC_MOVE)
                if (transfer.overwrite) options += StandardCopyOption.REPLACE_EXISTING
                Files.move(transfer.temp, transfer.target, *options.toTypedArray())
                completeTransfer(transfer)
                sendTransfer(
                    player,
                    FileTransferResultPacket("file_transfer_result", transfer.id, true, "ok", "Upload completed.", actualHash),
                    FileTransferResultPacket.serializer(),
                )
            } catch (e: Exception) {
                val failure = if (e is FileFailure) e else FileFailure("io_error", "Upload could not be committed.")
                cancelTransfer(transfer, failure.code, notify = true, message = failure.message ?: "Upload failed.")
            }
        }
    }

    private fun handleDownloadAck(
        player: BackendPlayer,
        packet: FileAckPacket,
    ) {
        val transfer = ownedTransfer(player, packet.transferId) as? DownloadTransfer ?: return
        transfer.executor.execute {
            try {
                if (packet.nextOffset != transfer.offset ||
                    transfer.awaitingResult
                ) {
                    fail("invalid_request", "Unexpected download acknowledgement.")
                }
                val buffer = ByteArray(FILE_CHUNK_BYTES)
                val read = transfer.input.read(buffer)
                if (read < 0) {
                    val attrs = Files.readAttributes(transfer.source, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                    if (attrs.size() != transfer.size || attrs.lastModifiedTime().toMillis() != transfer.modifiedAt) {
                        fail("conflict", "Source changed during download.")
                    }
                    transfer.input.close()
                    transfer.awaitingResult = true
                    sendTransfer(
                        player,
                        FileFinishPacket("file_finish", transfer.id, transfer.size, transfer.digest.digest().toHex()),
                        FileFinishPacket.serializer(),
                    )
                    return@execute
                }
                val bytes = buffer.copyOf(read)
                transfer.digest.update(bytes)
                val offset = transfer.offset
                transfer.offset += read
                transfer.touch()
                sendTransfer(
                    player,
                    FileChunkPacket("file_chunk", transfer.id, offset, Base64.getEncoder().encodeToString(bytes)),
                    FileChunkPacket.serializer(),
                )
            } catch (e: Exception) {
                val failure = if (e is FileFailure) e else FileFailure("io_error", "Download failed.")
                cancelTransfer(transfer, failure.code, notify = true, message = failure.message ?: "Download failed.")
            }
        }
    }

    private fun handleDownloadResult(
        player: BackendPlayer,
        packet: FileTransferResultPacket,
    ) {
        val transfer = ownedTransfer(player, packet.transferId) as? DownloadTransfer ?: return
        if (packet.success && !transfer.awaitingResult) {
            cancelTransfer(transfer, "invalid_request", notify = true, message = "Unexpected download result.")
            return
        }
        if (packet.success) completeTransfer(transfer) else cancelTransfer(transfer, packet.code, notify = false)
    }

    private fun registerTransfer(transfer: ServerTransfer) {
        synchronized(stateLock) {
            if (transfers.size >= platform.config.files.maxConcurrentTransfers) fail("busy", "The server transfer limit is reached.")
            if (transfers.values.any { it.player.uuid == transfer.player.uuid && it::class == transfer::class }) {
                fail("busy", "A transfer in this direction is already active.")
            }
            transfers[transfer.id] = transfer
        }
    }

    private fun reserveUpload(
        player: BackendPlayer,
        size: Long,
        destinationDirectory: Path,
    ) {
        synchronized(stateLock) {
            if (transfers.size >= platform.config.files.maxConcurrentTransfers ||
                transfers.values.any { it.player.uuid == player.uuid && it is UploadTransfer }
            ) {
                fail("busy", "An upload slot is not available.")
            }
            val usable = Files.getFileStore(destinationDirectory).usableSpace
            if (size > usable - platform.config.files.reservedFreeBytes - reservedUploadBytes) {
                fail("insufficient_space", "Upload would exceed the server free-space reserve.")
            }
            reservedUploadBytes += size
        }
    }

    private fun releaseUpload(size: Long) {
        synchronized(stateLock) { reservedUploadBytes = (reservedUploadBytes - size).coerceAtLeast(0) }
    }

    private fun completeTransfer(transfer: ServerTransfer) {
        if (!transfer.closed.compareAndSet(false, true)) return
        transfers.remove(transfer.id)
        runCatching { transfer.close() }
        if (transfer is UploadTransfer) releaseUpload(transfer.size)
        transfer.executor.shutdown()
    }

    private fun requireActive(transfer: ServerTransfer) {
        if (transfer.closed.get() || !sessionManager.isActive(transfer.player.uuid)) {
            fail("cancelled", "Transfer session is no longer active.")
        }
    }

    private fun cancelTransfer(
        transfer: ServerTransfer,
        code: String,
        notify: Boolean,
        message: String = if (code == "timeout") "Transfer timed out." else "Transfer cancelled.",
    ) {
        if (!transfer.closed.compareAndSet(false, true)) return
        transfers.remove(transfer.id)
        runCatching { transfer.close() }
        if (transfer is UploadTransfer) {
            releaseUpload(transfer.size)
            runCatching { Files.deleteIfExists(transfer.temp) }
        }
        transfer.executor.shutdownNow()
        if (notify) {
            sendTransfer(
                transfer.player,
                FileTransferResultPacket("file_transfer_result", transfer.id, false, code, message),
                FileTransferResultPacket.serializer(),
            )
        }
    }

    private fun ownedTransfer(
        player: BackendPlayer,
        id: String,
    ): ServerTransfer? = transfers[id]?.takeIf { it.player.uuid == player.uuid }

    private fun configuredRoot(id: String): BackendFileRoot =
        platform.config.files.roots
            .firstOrNull { it.id == id } ?: fail("root_not_found", "File root was not found.")

    private fun resolveRoot(root: BackendFileRoot): Path {
        val configured = Path.of(root.path)
        val resolved =
            (
                if (configured.isAbsolute) {
                    configured
                } else {
                    platform.serverDirectory.resolve(
                        configured,
                    )
                }
            ).toAbsolutePath().normalize()
        if (!Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(resolved)) {
            fail("root_not_found", "File root is unavailable.")
        }
        return resolved
    }

    private fun resolve(
        root: BackendFileRoot,
        path: String,
        leafMayNotExist: Boolean = false,
        allowSymlinkLeaf: Boolean = false,
    ): Path {
        val base = resolveRoot(root)
        val relative = Path.of(path.ifBlank { "." })
        if (relative.isAbsolute) fail("invalid_path", "Absolute server paths are not allowed.")
        val normalized = relative.normalize()
        if (normalized.startsWith("..")) fail("invalid_path", "Path escapes the configured root.")
        val target = base.resolve(normalized).normalize()
        if (!target.startsWith(base)) fail("invalid_path", "Path escapes the configured root.")
        var current = base
        val names = base.relativize(target).toList()
        names.forEachIndexed { index, name ->
            current = current.resolve(name)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                if (!(allowSymlinkLeaf && index == names.lastIndex)) fail("invalid_path", "Symbolic links cannot be followed.")
            }
        }
        if (!leafMayNotExist && !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) fail("not_found", "Path was not found.")
        return target
    }

    private fun ensureExistingDirectory(
        root: BackendFileRoot,
        directory: Path,
    ) {
        resolve(root, relative(root, directory))
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) fail("not_directory", "Parent is not a directory.")
    }

    private fun requireWritable(root: BackendFileRoot) {
        if (root.mode != BackendFileRootMode.READ_WRITE) fail("read_only", "File root is read-only.")
    }

    private fun create(
        root: BackendFileRoot,
        path: String,
        directory: Boolean,
    ) {
        val target = resolve(root, path, leafMayNotExist = true)
        if (cleanRelative(path).isEmpty()) fail("invalid_path", "The root cannot be replaced.")
        ensureExistingDirectory(root, target.parent)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) fail("already_exists", "Destination already exists.")
        if (directory) Files.createDirectory(target) else Files.createFile(target)
    }

    private fun move(
        root: BackendFileRoot,
        sourcePath: String,
        destinationPath: String,
        overwrite: Boolean,
    ) {
        val source = resolve(root, sourcePath, allowSymlinkLeaf = true)
        val destination = resolve(root, destinationPath, leafMayNotExist = true)
        if (cleanRelative(sourcePath).isEmpty() ||
            cleanRelative(destinationPath).isEmpty()
        ) {
            fail("invalid_path", "The root cannot be moved.")
        }
        ensureExistingDirectory(root, destination.parent)
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !overwrite) fail("already_exists", "Destination already exists.")
        val options = mutableListOf(StandardCopyOption.ATOMIC_MOVE)
        if (overwrite) options += StandardCopyOption.REPLACE_EXISTING
        Files.move(source, destination, *options.toTypedArray())
    }

    private fun delete(
        root: BackendFileRoot,
        path: String,
    ) {
        if (cleanRelative(path).isEmpty()) fail("invalid_path", "The configured root cannot be deleted.")
        val target = resolve(root, path, allowSymlinkLeaf = true)
        if (Files.isSymbolicLink(target) || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(target)
            return
        }
        Files.walkFileTree(
            target,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    dir: Path,
                    exc: java.io.IOException?,
                ): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun toEntry(
        root: BackendFileRoot,
        entry: Path,
    ): FileEntryPacket {
        val attrs = Files.readAttributes(entry, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        val kind =
            when {
                attrs.isSymbolicLink -> "symlink"
                attrs.isDirectory -> "directory"
                attrs.isRegularFile -> "file"
                else -> "other"
            }
        return FileEntryPacket(
            name = entry.fileName.toString(),
            path = relative(root, entry),
            kind = kind,
            sizeBytes = if (attrs.isRegularFile) attrs.size() else 0,
            modifiedAt = attrs.lastModifiedTime().toMillis(),
            editable = attrs.isRegularFile && attrs.size() <= MAX_EDITABLE_FILE_BYTES,
        )
    }

    private fun relative(
        root: BackendFileRoot,
        path: Path,
    ): String = resolveRoot(root).relativize(path.toAbsolutePath().normalize()).joinToString("/")

    private fun cleanRelative(path: String): String =
        Path.of(path.ifBlank { "." }).normalize().joinToString("/").let {
            if (it ==
                "."
            ) {
                ""
            } else {
                it
            }
        }

    private fun currentSha256(path: Path): String {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) fail("conflict", "The original file no longer exists.")
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(FILE_CHUNK_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun sendReady(
        transfer: ServerTransfer,
        direction: String,
        size: Long,
    ) = sendData(
        transfer.player,
        FileTransferReadyPacket("file_transfer_ready", transfer.requestId, transfer.id, direction, size),
        FileTransferReadyPacket.serializer(),
    )

    private fun sendFailure(
        player: BackendPlayer,
        requestId: String,
        failure: FileFailure,
    ) = sendData(
        player,
        FileActionResultPacket("file_action_result", requestId, false, failure.code, failure.message ?: "File request failed."),
        FileActionResultPacket.serializer(),
    )

    private fun <T> sendData(
        player: BackendPlayer,
        packet: T,
        serializer: kotlinx.serialization.KSerializer<T>,
    ) {
        val data = json.encodeToString(serializer, packet)
        platform.scheduler.runLater(0) { platform.sendData(player, data) }
    }

    private fun <T> sendTransfer(
        player: BackendPlayer,
        packet: T,
        serializer: kotlinx.serialization.KSerializer<T>,
    ) {
        val data = json.encodeToString(serializer, packet)
        platform.scheduler.runLater(0) { platform.sendTransfer(player, data) }
    }

    private fun <T> decode(
        serializer: kotlinx.serialization.KSerializer<T>,
        data: String,
        player: BackendPlayer,
    ): T? =
        try {
            json.decodeFromString(serializer, data)
        } catch (e: Exception) {
            platform.logger.warning("Invalid file packet from ${player.name}: ${e.message}")
            null
        }

    private sealed class ServerTransfer(
        val id: String,
        val requestId: String,
        val player: BackendPlayer,
    ) {
        val executor: ExecutorService =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "venus-transfer-$id").apply {
                    isDaemon =
                        true
                }
            }
        val closed = AtomicBoolean(false)

        @Volatile
        var lastActivity: Long = System.currentTimeMillis()

        fun touch() {
            lastActivity = System.currentTimeMillis()
        }

        abstract fun close()
    }

    private class UploadTransfer(
        id: String,
        requestId: String,
        player: BackendPlayer,
        val root: BackendFileRoot,
        val target: Path,
        val temp: Path,
        val size: Long,
        val overwrite: Boolean,
        val expectedSha256: String?,
        val output: BufferedOutputStream,
    ) : ServerTransfer(id, requestId, player) {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
        var offset: Long = 0

        override fun close() = output.close()
    }

    private class DownloadTransfer(
        id: String,
        requestId: String,
        player: BackendPlayer,
        val source: Path,
        val size: Long,
        val modifiedAt: Long,
        val input: BufferedInputStream,
    ) : ServerTransfer(id, requestId, player) {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
        var offset: Long = 0
        var awaitingResult: Boolean = false

        override fun close() = input.close()
    }
}

private class FileFailure(
    val code: String,
    message: String,
) : RuntimeException(message)

private fun fail(
    code: String,
    message: String,
): Nothing = throw FileFailure(code, message)

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
