package dev.ilgax.venus.state

import dev.ilgax.venus.protocol.CmdResponsePacket
import dev.ilgax.venus.protocol.PlayerActionResultPacket
import dev.ilgax.venus.protocol.PlayerDetail
import dev.ilgax.venus.protocol.PlayerListPacket
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

    private val console = mutableListOf<String>()
    private val statHistory = mutableListOf<StatsPacket>()

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
    }

    fun updatePlayerDetail(playerDetail: PlayerDetail) {
        latestPlayerDetail = playerDetail
    }

    fun updatePlayerActionResult(result: PlayerActionResultPacket) {
        latestPlayerActionResult = result
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
        synchronized(console) {
            console.clear()
        }
        synchronized(statHistory) {
            statHistory.clear()
        }
    }

    private const val MAX_CONSOLE_LINES = 500
    private const val MAX_STAT_HISTORY = 60
}
