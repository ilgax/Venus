package dev.xcyn.venus.auth

import java.security.PublicKey
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PendingSession(
    val clientPublicKey: PublicKey,
    val challenge: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingSession) return false
        return clientPublicKey == other.clientPublicKey &&
                challenge.contentEquals(other.challenge)
    }

    override fun hashCode(): Int {
        var result = clientPublicKey.hashCode()
        result = 31 * result + challenge.contentHashCode()
        return result
    }
}

object SessionManager {
    private val pendingSessions = ConcurrentHashMap<UUID, PendingSession>()
    private val activeSessions = ConcurrentHashMap<UUID, PublicKey>()

    fun addPending(uuid: UUID, session: PendingSession) {
        pendingSessions[uuid] = session
    }

    fun getPending(uuid: UUID): PendingSession? = pendingSessions[uuid]

    fun removePending(uuid: UUID) = pendingSessions.remove(uuid)

    fun activate(uuid: UUID, publicKey: PublicKey) {
        activeSessions[uuid] = publicKey
    }

    fun isActive(uuid: UUID) = activeSessions.containsKey(uuid)

    fun deactivate(uuid: UUID) {
        activeSessions.remove(uuid)
        pendingSessions.remove(uuid)
    }
}