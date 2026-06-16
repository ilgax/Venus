package dev.ilgax.venus.auth

import java.security.KeyPairGenerator
import java.util.UUID
import kotlin.test.AfterTest
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

    @AfterTest
    fun teardown() {
        SessionManager.deactivate(uuid)
        SessionManager.removePending(uuid)
        SessionManager.removePendingApproval(uuid)
    }

    @Test
    fun `addPending and getPending roundtrip`() {
        val challenge = ByteArray(32) { it.toByte() }
        val session = PendingSession(keyPair.public, challenge)
        SessionManager.addPending(uuid, session)
        val stored = SessionManager.getPending(uuid)
        assertNotNull(stored)
        assertEquals(session, stored)
    }

    @Test
    fun `getPending returns null for unknown uuid`() {
        assertNull(SessionManager.getPending(UUID.randomUUID()))
    }

    @Test
    fun `removePending removes and returns session`() {
        val session = PendingSession(keyPair.public, ByteArray(32) { 42 })
        SessionManager.addPending(uuid, session)
        val removed = SessionManager.removePending(uuid)
        assertEquals(session, removed)
        assertNull(SessionManager.getPending(uuid))
    }

    @Test
    fun `addPendingApproval and getPendingApproval roundtrip`() {
        val approval = PendingApproval(keyPair.public, keyPair.public.encoded.toString())
        SessionManager.addPendingApproval(uuid, approval)
        assertEquals(approval, SessionManager.getPendingApproval(uuid))
    }

    @Test
    fun `removePendingApproval removes and returns`() {
        val approval = PendingApproval(keyPair.public, keyPair.public.encoded.toString())
        SessionManager.addPendingApproval(uuid, approval)
        assertEquals(approval, SessionManager.removePendingApproval(uuid))
        assertNull(SessionManager.getPendingApproval(uuid))
    }

    @Test
    fun `getNextPendingApproval returns first entry`() {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        val approval1 = PendingApproval(keyPair.public, "key1")
        val approval2 = PendingApproval(keyPair.public, "key2")
        try {
            SessionManager.addPendingApproval(uuid1, approval1)
            SessionManager.addPendingApproval(uuid2, approval2)
            assertNotNull(SessionManager.getNextPendingApproval())
        } finally {
            SessionManager.removePendingApproval(uuid1)
            SessionManager.removePendingApproval(uuid2)
        }
    }

    @Test
    fun `getNextPendingApproval returns null when empty`() {
        assertNull(SessionManager.getNextPendingApproval())
    }

    @Test
    fun `activate and isActive`() {
        assertFalse(SessionManager.isActive(uuid))
        SessionManager.activate(uuid, keyPair.public)
        assertTrue(SessionManager.isActive(uuid))
    }

    @Test
    fun `deactivate removes from active sessions`() {
        SessionManager.activate(uuid, keyPair.public)
        SessionManager.deactivate(uuid)
        assertFalse(SessionManager.isActive(uuid))
    }

    @Test
    fun `deactivate removes all three associations`() {
        SessionManager.addPending(uuid, PendingSession(keyPair.public, ByteArray(32) { 7 }))
        SessionManager.addPendingApproval(uuid, PendingApproval(keyPair.public, "key"))
        SessionManager.activate(uuid, keyPair.public)
        SessionManager.deactivate(uuid)
        assertFalse(SessionManager.isActive(uuid))
        assertNull(SessionManager.getPending(uuid))
        assertNull(SessionManager.getPendingApproval(uuid))
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
