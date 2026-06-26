package dev.ilgax.venus.auth

import java.security.KeyPairGenerator
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerTest {
    private val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val uuid = UUID.randomUUID()
    private val sessionManager = SessionManager()

    @Test
    fun `addPending and getPending roundtrip`() {
        val challenge = ByteArray(32) { it.toByte() }
        val session = PendingSession(keyPair.public, challenge)
        sessionManager.addPending(uuid, session)
        val stored = sessionManager.getPending(uuid)
        assertNotNull(stored)
        assertEquals(session, stored)
    }

    @Test
    fun `getPending returns null for unknown uuid`() {
        assertNull(sessionManager.getPending(UUID.randomUUID()))
    }

    @Test
    fun `removePending removes and returns session`() {
        val session = PendingSession(keyPair.public, ByteArray(32) { 42 })
        sessionManager.addPending(uuid, session)
        val removed = sessionManager.removePending(uuid)
        assertEquals(session, removed)
        assertNull(sessionManager.getPending(uuid))
    }

    @Test
    fun `addPendingApproval and getPendingApproval roundtrip`() {
        val approval = PendingApproval(keyPair.public, keyPair.public.encoded.toString())
        sessionManager.addPendingApproval(uuid, approval)
        assertEquals(approval, sessionManager.getPendingApproval(uuid))
    }

    @Test
    fun `removePendingApproval removes and returns`() {
        val approval = PendingApproval(keyPair.public, keyPair.public.encoded.toString())
        sessionManager.addPendingApproval(uuid, approval)
        assertEquals(approval, sessionManager.removePendingApproval(uuid))
        assertNull(sessionManager.getPendingApproval(uuid))
    }

    @Test
    fun `getNextPendingApproval returns first entry`() {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        val approval1 = PendingApproval(keyPair.public, "key1")
        val approval2 = PendingApproval(keyPair.public, "key2")
        sessionManager.addPendingApproval(uuid1, approval1)
        sessionManager.addPendingApproval(uuid2, approval2)
        assertNotNull(sessionManager.getNextPendingApproval())
    }

    @Test
    fun `getNextPendingApproval skips stale queue entries`() {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        val approval1 = PendingApproval(keyPair.public, "key1")
        val approval2 = PendingApproval(keyPair.public, "key2")
        sessionManager.addPendingApproval(uuid1, approval1)
        sessionManager.addPendingApproval(uuid2, approval2)
        sessionManager.removePendingApproval(uuid1)

        val next = sessionManager.getNextPendingApproval()

        assertNotNull(next)
        assertEquals(uuid2, next.key)
        assertEquals(approval2, next.value)
    }

    @Test
    fun `getNextPendingApproval returns null when empty`() {
        assertNull(sessionManager.getNextPendingApproval())
    }

    @Test
    fun `activate and isActive`() {
        assertFalse(sessionManager.isActive(uuid))
        sessionManager.activate(uuid, keyPair.public)
        assertTrue(sessionManager.isActive(uuid))
    }

    @Test
    fun `deactivate removes from active sessions`() {
        sessionManager.activate(uuid, keyPair.public)
        sessionManager.deactivate(uuid)
        assertFalse(sessionManager.isActive(uuid))
    }

    @Test
    fun `deactivate removes all three associations`() {
        sessionManager.addPending(uuid, PendingSession(keyPair.public, ByteArray(32) { 7 }))
        sessionManager.addPendingApproval(uuid, PendingApproval(keyPair.public, "key"))
        sessionManager.activate(uuid, keyPair.public)
        sessionManager.deactivate(uuid)
        assertFalse(sessionManager.isActive(uuid))
        assertNull(sessionManager.getPending(uuid))
        assertNull(sessionManager.getPendingApproval(uuid))
    }

    @Test
    fun `PendingSession equals with same challenge content`() {
        val challenge = ByteArray(32) { 1 }
        val s1 = PendingSession(keyPair.public, challenge)
        val s2 = PendingSession(keyPair.public, challenge.copyOf())
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun `PendingSession not equal with different challenge`() {
        val s1 = PendingSession(keyPair.public, ByteArray(32) { 1 })
        val s2 = PendingSession(keyPair.public, ByteArray(32) { 2 })
        assertNotEquals(s1, s2)
    }

    @Test
    fun `PendingSession not equal with different key`() {
        val otherKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val s1 = PendingSession(keyPair.public, ByteArray(32) { 1 })
        val s2 = PendingSession(otherKeyPair.public, ByteArray(32) { 1 })
        assertNotEquals(s1, s2)
    }
}
