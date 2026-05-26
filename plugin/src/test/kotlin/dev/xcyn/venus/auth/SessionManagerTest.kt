package dev.xcyn.venus.auth

import java.security.KeyPairGenerator
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        SessionManager.clearUUIDCache(uuid)
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
        val unknown = UUID.randomUUID()
        assertNull(SessionManager.getPending(unknown))
    }

    @Test
    fun `removePending removes and returns session`() {
        val challenge = ByteArray(32) { 42 }
        val session = PendingSession(keyPair.public, challenge)
        SessionManager.addPending(uuid, session)
        val removed = SessionManager.removePending(uuid)
        assertEquals(session, removed)
        assertNull(SessionManager.getPending(uuid))
    }

    @Test
    fun `addPendingApproval and getPendingApproval roundtrip`() {
        val b64 = java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val approval = PendingApproval(keyPair.public, b64)
        SessionManager.addPendingApproval(uuid, approval)
        val stored = SessionManager.getPendingApproval(uuid)
        assertEquals(approval, stored)
    }

    @Test
    fun `removePendingApproval removes and returns`() {
        val b64 = java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val approval = PendingApproval(keyPair.public, b64)
        SessionManager.addPendingApproval(uuid, approval)
        val removed = SessionManager.removePendingApproval(uuid)
        assertEquals(approval, removed)
        assertNull(SessionManager.getPendingApproval(uuid))
    }

    @Test
    fun `getNextPendingApproval returns first entry`() {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        val b64 = java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val approval1 = PendingApproval(keyPair.public, b64)
        val approval2 = PendingApproval(keyPair.public, "$b64-other")
        try {
            SessionManager.addPendingApproval(uuid1, approval1)
            SessionManager.addPendingApproval(uuid2, approval2)
            val next = SessionManager.getNextPendingApproval()
            assertNotNull(next)
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
        val challenge = ByteArray(32) { 7 }
        val b64 = java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)
        SessionManager.addPending(uuid, PendingSession(keyPair.public, challenge))
        SessionManager.addPendingApproval(uuid, PendingApproval(keyPair.public, b64))
        SessionManager.activate(uuid, keyPair.public)
        SessionManager.deactivate(uuid)
        assertFalse(SessionManager.isActive(uuid))
        assertNull(SessionManager.getPending(uuid))
        assertNull(SessionManager.getPendingApproval(uuid))
    }

    @Test
    fun `cacheUUID and getCachedKey roundtrip`() {
        val b64 = "cached_key_base64"
        SessionManager.cacheUUID(uuid, b64)
        assertEquals(b64, SessionManager.getCachedKey(uuid))
    }

    @Test
    fun `clearUUIDCache removes entry`() {
        SessionManager.cacheUUID(uuid, "test")
        assertEquals("test", SessionManager.clearUUIDCache(uuid))
        assertNull(SessionManager.getCachedKey(uuid))
    }

    @Test
    fun `getCachedKey returns null for unknown uuid`() {
        assertNull(SessionManager.getCachedKey(UUID.randomUUID()))
    }

    @Test
    fun `PendingSession equals with same challenge content`() {
        val challenge = ByteArray(32) { 1 }
        val challengeCopy = challenge.copyOf()
        val s1 = PendingSession(keyPair.public, challenge)
        val s2 = PendingSession(keyPair.public, challengeCopy)
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun `PendingSession not equal with different challenge`() {
        val c1 = ByteArray(32) { 1 }
        val c2 = ByteArray(32) { 2 }
        val s1 = PendingSession(keyPair.public, c1)
        val s2 = PendingSession(keyPair.public, c2)
        assertFalse(s1 == s2)
    }

    @Test
    fun `PendingSession not equal with different key`() {
        val otherKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val challenge = ByteArray(32) { 1 }
        val s1 = PendingSession(keyPair.public, challenge)
        val s2 = PendingSession(otherKeyPair.public, challenge.copyOf())
        assertFalse(s1 == s2)
    }
}
