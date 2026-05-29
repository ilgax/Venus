package dev.ilgax.venus.state

import dev.ilgax.venus.protocol.CmdResponsePacket
import dev.ilgax.venus.protocol.StatsPacket

object SessionState {
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

    private val responses = mutableListOf<CmdResponsePacket>()
    private val console = mutableListOf<String>()
    private val statHistory = mutableListOf<StatsPacket>()

    val commandResponses: List<CmdResponsePacket>
        get() = synchronized(responses) { responses.toList() }

    val consoleLines: List<String>
        get() = synchronized(console) { console.toList() }

    val statsHistory: List<StatsPacket>
        get() = synchronized(statHistory) { statHistory.toList() }

    fun activate() {
        sessionActive = true
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
            while (statHistory.size > MAX_STAT_HISTORY) {
                statHistory.removeAt(0)
            }
        }
    }

    fun addCommandResponse(response: CmdResponsePacket) {
        synchronized(responses) {
            responses.add(response)
        }
        addConsoleLines(listOf("> ${response.command}") + response.lines)
    }

    fun addConsoleLines(lines: List<String>) {
        synchronized(console) {
            console.addAll(lines)
            while (console.size > MAX_CONSOLE_LINES) {
                console.removeAt(0)
            }
        }
    }

    fun clearConsole() {
        synchronized(console) {
            console.clear()
        }
    }

    fun reset() {
        sessionActive = false
        latestStats = null
        serverAddress = null
        serverListName = null
        synchronized(responses) {
            responses.clear()
        }
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
