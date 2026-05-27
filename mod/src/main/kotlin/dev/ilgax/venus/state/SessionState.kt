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

    val commandResponses: List<CmdResponsePacket>
        get() = synchronized(responses) { responses.toList() }

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
    }

    fun reset() {
        sessionActive = false
        latestStats = null
        synchronized(responses) {
            responses.clear()
        }
    }
}
