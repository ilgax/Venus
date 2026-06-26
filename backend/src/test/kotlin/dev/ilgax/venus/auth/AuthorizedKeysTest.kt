package dev.ilgax.venus.auth

import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthorizedKeysTest {
    private lateinit var tempDir: java.io.File

    @BeforeTest
    fun setup() {
        tempDir =
            kotlin.io.path
                .createTempDirectory("venus-test-authkeys")
                .toFile()
        AuthorizedKeys.init(tempDir)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `init creates authorized keys file`() {
        val keysFile = tempDir.resolve("keys").resolve("authorized_keys.txt")
        assertTrue(keysFile.exists())
    }

    @Test
    fun `isAuthorized returns false for unknown key`() {
        assertFalse(AuthorizedKeys.isAuthorized("unknown_key_base64_string"))
    }

    @Test
    fun `authorize and isAuthorized roundtrip`() {
        val key = "public_key_base64_value"
        assertFalse(AuthorizedKeys.isAuthorized(key))
        AuthorizedKeys.authorize(key, "testuser")
        assertTrue(AuthorizedKeys.isAuthorized(key))
    }

    @Test
    fun `count returns zero on empty file`() {
        assertEquals(0, AuthorizedKeys.count())
    }

    @Test
    fun `count increments with each authorize`() {
        assertEquals(0, AuthorizedKeys.count())
        AuthorizedKeys.authorize("key1", "user1")
        assertEquals(1, AuthorizedKeys.count())
        AuthorizedKeys.authorize("key2", "user2")
        assertEquals(2, AuthorizedKeys.count())
    }

    @Test
    fun `isAuthorized matches only first whitespace delimited field`() {
        val key = "actual_key_value"
        AuthorizedKeys.authorize(key, "comment with spaces and extra data")
        assertTrue(AuthorizedKeys.isAuthorized(key))
    }

    @Test
    fun `isAuthorized with trimmed whitespace`() {
        val key = "trimmed_key"
        AuthorizedKeys.authorize("  $key  ", "user")
        assertTrue(AuthorizedKeys.isAuthorized(key))
    }

    @Test
    fun `authorize deduplicates same key`() {
        AuthorizedKeys.authorize("dup_key", "user1")
        assertEquals(1, AuthorizedKeys.count())
        AuthorizedKeys.authorize("dup_key", "user2")
        assertEquals(1, AuthorizedKeys.count())
        assertTrue(AuthorizedKeys.isAuthorized("dup_key"))
    }

    @Test
    fun `tryAuthorize refuses new key when max users reached`() {
        AuthorizedKeys.authorize("key1", "user1")

        assertFalse(AuthorizedKeys.tryAuthorize("key2", "user2", maxUsers = 1))

        assertFalse(AuthorizedKeys.isAuthorized("key2"))
        assertEquals(1, AuthorizedKeys.count())
    }

    @Test
    fun `tryAuthorize allows existing key when max users reached`() {
        AuthorizedKeys.authorize("key1", "user1")

        assertTrue(AuthorizedKeys.tryAuthorize("key1", "user1", maxUsers = 1))

        assertTrue(AuthorizedKeys.isAuthorized("key1"))
        assertEquals(1, AuthorizedKeys.count())
    }

    @Test
    fun `remove by base64 returns true and removes key`() {
        AuthorizedKeys.authorize("removable_key", "user")
        assertTrue(AuthorizedKeys.remove("removable_key"))
        assertFalse(AuthorizedKeys.isAuthorized("removable_key"))
    }

    @Test
    fun `remove by base64 returns false for unknown key`() {
        assertFalse(AuthorizedKeys.remove("nonexistent_key"))
    }

    @Test
    fun `removeByFingerprint removes correct entry`() {
        val keyB64 = genKeyB64()
        AuthorizedKeys.authorize(keyB64, "FingerprintUser")
        val fp = Handshake.fingerprint(Handshake.decodePublicKey(keyB64))
        assertTrue(AuthorizedKeys.removeByFingerprint(fp))
        assertFalse(AuthorizedKeys.isAuthorized(keyB64))
    }

    @Test
    fun `removeEntryByFingerprint returns removed entry`() {
        val keyB64 = genKeyB64()
        AuthorizedKeys.authorize(keyB64, "FingerprintUser")
        val fp = Handshake.fingerprint(Handshake.decodePublicKey(keyB64))

        val removed = AuthorizedKeys.removeEntryByFingerprint(fp)

        assertEquals(keyB64, removed?.publicKeyBase64)
        assertEquals("FingerprintUser", removed?.comment)
        assertEquals(fp, removed?.fingerprint)
        assertFalse(AuthorizedKeys.isAuthorized(keyB64))
    }

    @Test
    fun `removeByFingerprint returns false for unknown fingerprint`() {
        AuthorizedKeys.authorize(genKeyB64(), "Someone")
        assertFalse(AuthorizedKeys.removeByFingerprint("SHA256:nonexistent=="))
    }

    @Test
    fun `list returns entries with fingerprints`() {
        val key1 = genKeyB64()
        val key2 = genKeyB64()
        AuthorizedKeys.authorize(key1, "Alice")
        AuthorizedKeys.authorize(key2, "Bob")

        val entries = AuthorizedKeys.list()
        assertEquals(2, entries.size)

        val fps = entries.map { it.fingerprint }.toSet()
        assertEquals(2, fps.size)
        assertTrue(fps.all { it.startsWith("SHA256:") })

        val entry1 = entries.first { it.comment == "Alice" }
        assertEquals(key1, entry1.publicKeyBase64)
        assertEquals(
            Handshake.fingerprint(Handshake.decodePublicKey(key1)),
            entry1.fingerprint,
        )
    }

    @Test
    fun `list returns empty list when no keys`() {
        assertTrue(AuthorizedKeys.list().isEmpty())
    }

    @Test
    fun `list fingerprint is deterministic across calls`() {
        val keyB64 = genKeyB64()
        AuthorizedKeys.authorize(keyB64, "Stable")
        val fp1 = AuthorizedKeys.list().first().fingerprint
        val fp2 = AuthorizedKeys.list().first().fingerprint
        assertEquals(fp1, fp2)
    }

    private fun genKeyB64(): String {
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        return Base64.getEncoder().encodeToString(kp.public.encoded)
    }
}
