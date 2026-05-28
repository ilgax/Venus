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

    private val responses = mutableListOf<CmdResponsePacket>()
    private val console = mutableListOf<String>()

    val commandResponses: List<CmdResponsePacket>
        get() = synchronized(responses) { responses.toList() }

    val consoleLines: List<String>
        get() = synchronized(console) { console.toList() }

    fun activate() {
        sessionActive = true
    }

    fun updateStats(stats: StatsPacket) {
        latestStats = stats
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

    fun reset() {
        sessionActive = false
        latestStats = null
        synchronized(responses) {
            responses.clear()
        }
        synchronized(console) {
            console.clear()
        }
    }

    private const val MAX_CONSOLE_LINES = 500
}
