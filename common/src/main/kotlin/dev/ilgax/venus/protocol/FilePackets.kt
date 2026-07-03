package dev.ilgax.venus.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileRootsGetPacket(
    val type: String,
    @SerialName("request_id") val requestId: String,
)

@Serializable
data class FileRootPacket(
    val id: String,
    val label: String,
    val writable: Boolean,
)

@Serializable
data class FileRootsPacket(
    val type: String,
    @SerialName("request_id") val requestId: String,
    val roots: List<FileRootPacket>,
)

@Serializable
data class FileListGetPacket(
    val type: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("root_id") val rootId: String,
    val path: String = "",
    val offset: Int = 0,
    val limit: Int = MAX_FILE_LIST_ENTRIES,
) {
    init {
        require(path.length <= MAX_FILE_PATH_LENGTH) { "path is too long" }
        require(offset >= 0) { "offset must be non-negative" }
        require(limit in 1..MAX_FILE_LIST_ENTRIES) { "limit must be in 1..$MAX_FILE_LIST_ENTRIES" }
    }
}

@Serializable
data class FileEntryPacket(
    val name: String,
    val path: String,
    val kind: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("modified_at") val modifiedAt: Long,
    val editable: Boolean,
)

@Serializable
data class FileListPacket(
    val type: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("root_id") val rootId: String,
    val path: String,
    val entries: List<FileEntryPacket>,
    @SerialName("next_offset") val nextOffset: Int? = null,
) {
    init {
        require(entries.size <= MAX_FILE_LIST_ENTRIES) { "entries must have at most $MAX_FILE_LIST_ENTRIES items" }
    }
}

@Serializable
data class FileActionPacket(
    val type: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("root_id") val rootId: String,
    val action: String,
    val path: String,
    val destination: String? = null,
    val overwrite: Boolean = false,
) {
    init {
        require(path.length <= MAX_FILE_PATH_LENGTH) { "path is too long" }
        require(destination == null || destination.length <= MAX_FILE_PATH_LENGTH) { "destination is too long" }
    }
}

@Serializable
data class FileActionResultPacket(
    val type: String,
    @SerialName("request_id") val requestId: String,
    val success: Boolean,
    val code: String,
    val message: String,
)

@Serializable
data class FileUploadStartPacket(
    val type: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("root_id") val rootId: String,
    val path: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    val overwrite: Boolean = false,
    @SerialName("expected_sha256") val expectedSha256: String? = null,
) {
    init {
        require(path.length <= MAX_FILE_PATH_LENGTH) { "path is too long" }
        require(sizeBytes >= 0) { "size_bytes must be non-negative" }
    }
}

@Serializable
data class FileDownloadStartPacket(
    val type: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("root_id") val rootId: String,
    val path: String,
) {
    init {
        require(path.length <= MAX_FILE_PATH_LENGTH) { "path is too long" }
    }
}

@Serializable
data class FileTransferReadyPacket(
    val type: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("transfer_id") val transferId: String,
    val direction: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("chunk_bytes") val chunkBytes: Int = FILE_CHUNK_BYTES,
    @SerialName("window_chunks") val windowChunks: Int = FILE_WINDOW_CHUNKS,
)

@Serializable
data class FileChunkPacket(
    val type: String,
    @SerialName("transfer_id") val transferId: String,
    val offset: Long,
    val data: String,
) {
    init {
        require(offset >= 0) { "offset must be non-negative" }
        require(data.isNotEmpty()) { "chunk data must not be empty" }
        require(data.length <= FILE_CHUNK_BASE64_MAX) { "chunk data is too long" }
    }
}

@Serializable
data class FileAckPacket(
    val type: String,
    @SerialName("transfer_id") val transferId: String,
    @SerialName("next_offset") val nextOffset: Long,
)

@Serializable
data class FileFinishPacket(
    val type: String,
    @SerialName("transfer_id") val transferId: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    val sha256: String,
)

@Serializable
data class FileTransferResultPacket(
    val type: String,
    @SerialName("transfer_id") val transferId: String,
    val success: Boolean,
    val code: String,
    val message: String,
    val sha256: String? = null,
)

@Serializable
data class FileCancelPacket(
    val type: String,
    @SerialName("transfer_id") val transferId: String,
    val reason: String = "cancelled",
)

const val MAX_FILE_PATH_LENGTH = 1024
const val MAX_FILE_LIST_ENTRIES = 50
const val FILE_CHUNK_BYTES = 8192
const val FILE_WINDOW_CHUNKS = 16
const val FILE_CHUNK_BASE64_MAX = 10_924
const val MAX_EDITABLE_FILE_BYTES = 1_048_576L
