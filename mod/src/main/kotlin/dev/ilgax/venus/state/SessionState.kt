package dev.ilgax.venus.state

import dev.ilgax.venus.protocol.CmdResponsePacket
import dev.ilgax.venus.protocol.FileActionResultPacket
import dev.ilgax.venus.protocol.FileListPacket
import dev.ilgax.venus.protocol.FileRootsPacket
import dev.ilgax.venus.protocol.PlayerActionResultPacket
import dev.ilgax.venus.protocol.PlayerDetail
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import dev.ilgax.venus.protocol.StatsPacket

object SessionState {
    enum class HandshakeState { IDLE, EXPECTING_READY, ACTIVE }

    @Volatile
    var handshakeState: HandshakeState = HandshakeState.IDLE
        private set

    @Volatile
    var sessionActive: Boolean = false
        private set

    @Volatile
    var latestStats: StatsPacket? = null
        private set

    @Volatile
    var serverAddress: String? = null
        private set

    @Volatile
    var serverListName: String? = null
        private set

    @Volatile
    var latestPlayerList: PlayerListPacket? = null
        private set

    @Volatile
    var latestPlayerDetail: PlayerDetail? = null
        private set

    @Volatile
    var latestPlayerActionResult: PlayerActionResultPacket? = null
        private set

    @Volatile
    var latestFileRoots: FileRootsPacket? = null
        private set

    @Volatile
    var latestFileList: FileListPacket? = null
        private set

    @Volatile
    var latestFileActionResult: FileActionResultPacket? = null
        private set

    @Volatile
    var fileEditor: FileEditorState? = null
        private set

    private val fileTransfers = linkedMapOf<String, FileTransferView>()

    val activeFileTransfers: List<FileTransferView>
        get() = synchronized(fileTransfers) { fileTransfers.values.toList() }

    private val console = mutableListOf<String>()
    private val statHistory = mutableListOf<StatsPacket>()
    private val playerListLock = Any()
    private var playerListRequest: PlayerListRequest? = null

    val consoleLines: List<String>
        get() = synchronized(console) { console.toList() }

    val statsHistory: List<StatsPacket>
        get() = synchronized(statHistory) { statHistory.toList() }

    fun markExpectingReady() {
        handshakeState = HandshakeState.EXPECTING_READY
    }

    fun markActive() {
        handshakeState = HandshakeState.ACTIVE
        sessionActive = true
    }

    fun markIdle() {
        handshakeState = HandshakeState.IDLE
        sessionActive = false
        synchronized(playerListLock) { playerListRequest = null }
    }

    fun activate() {
        markActive()
    }

    fun setServerInfo(
        address: String?,
        name: String?,
    ) {
        serverAddress = address
        serverListName = name
    }

    fun updateStats(stats: StatsPacket) {
        latestStats = stats
        synchronized(statHistory) {
            statHistory.add(stats)
            if (statHistory.size > MAX_STAT_HISTORY) {
                statHistory.subList(0, statHistory.size - MAX_STAT_HISTORY).clear()
            }
        }
    }

    fun updatePlayerList(playerList: PlayerListPacket) {
        latestPlayerList = playerList
        synchronized(playerListLock) { playerListRequest = null }
    }

    fun beginPlayerListRequest(requestId: String) {
        synchronized(playerListLock) {
            playerListRequest = PlayerListRequest(requestId)
        }
    }

    fun mergePlayerList(page: PlayerListPacket): String? =
        synchronized(playerListLock) {
            val request = playerListRequest ?: return@synchronized null
            if (page.requestId != request.requestId || page.cursor != request.expectedCursor) return@synchronized null
            request.onlinePlayers += page.onlinePlayers
            request.whitelistedPlayers += page.whitelistedPlayers
            request.blockedPlayers += page.blockedPlayers
            latestPlayerList =
                PlayerListPacket(
                    type = page.type,
                    requestId = page.requestId,
                    onlineCount = page.onlineCount,
                    maxPlayers = page.maxPlayers,
                    onlinePlayers = request.onlinePlayers.toList(),
                    whitelistedPlayers = request.whitelistedPlayers.toList(),
                    blockedPlayers = request.blockedPlayers.toList(),
                    nextCursor = page.nextCursor,
                )
            request.expectedCursor = page.nextCursor
            if (page.nextCursor == null) playerListRequest = null
            page.nextCursor
        }

    fun updatePlayerDetail(playerDetail: PlayerDetail) {
        latestPlayerDetail = playerDetail
    }

    fun updatePlayerActionResult(result: PlayerActionResultPacket) {
        latestPlayerActionResult = result
    }

    fun updateFileRoots(roots: FileRootsPacket) {
        latestFileRoots = roots
    }

    fun updateFileList(list: FileListPacket) {
        latestFileList = list
    }

    fun updateFileActionResult(result: FileActionResultPacket) {
        latestFileActionResult = result
    }

    fun updateFileTransfer(view: FileTransferView) {
        synchronized(fileTransfers) {
            fileTransfers[view.transferId] = view
            while (fileTransfers.size > MAX_FILE_TRANSFER_HISTORY) fileTransfers.remove(fileTransfers.keys.first())
        }
    }

    fun openFileEditor(state: FileEditorState) {
        fileEditor = state
    }

    fun closeFileEditor() {
        fileEditor = null
    }

    fun addCommandResponse(response: CmdResponsePacket) {
        addConsoleLines(listOf("> ${response.command}") + response.lines)
    }

    fun addConsoleLines(lines: List<String>) {
        synchronized(console) {
            val bounded =
                if (lines.size > MAX_CONSOLE_LINES) {
                    lines.takeLast(MAX_CONSOLE_LINES)
                } else {
                    lines
                }
            console.addAll(bounded)
            if (console.size > MAX_CONSOLE_LINES) {
                console.subList(0, console.size - MAX_CONSOLE_LINES).clear()
            }
        }
    }

    fun clearConsole() {
        synchronized(console) {
            console.clear()
        }
    }

    fun reset() {
        markIdle()
        latestStats = null
        serverAddress = null
        serverListName = null
        latestPlayerList = null
        latestPlayerDetail = null
        latestPlayerActionResult = null
        latestFileRoots = null
        latestFileList = null
        latestFileActionResult = null
        fileEditor = null
        synchronized(fileTransfers) { fileTransfers.clear() }
        synchronized(console) {
            console.clear()
        }
        synchronized(statHistory) {
            statHistory.clear()
        }
    }

    private const val MAX_CONSOLE_LINES = 500
    private const val MAX_STAT_HISTORY = 60
    private const val MAX_FILE_TRANSFER_HISTORY = 8
}

private data class PlayerListRequest(
    val requestId: String,
    var expectedCursor: String? = null,
    val onlinePlayers: MutableList<PlayerSummaryPacket> = mutableListOf(),
    val whitelistedPlayers: MutableList<PlayerSummaryPacket> = mutableListOf(),
    val blockedPlayers: MutableList<PlayerSummaryPacket> = mutableListOf(),
)

data class FileTransferView(
    val transferId: String,
    val direction: String,
    val path: String,
    val transferredBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val status: String,
    val message: String = "",
)

data class FileEditorState(
    val rootId: String,
    val path: String,
    val content: String,
    val sha256: String,
)
