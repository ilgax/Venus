package dev.xcyn.venus.auth

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
    fun `init creates authorized_keys file`() {
        val keysFile = tempDir.resolve("keys").resolve("authorized_keys.txt")
        assertTrue(keysFile.exists())
    }

    @Test
    fun `isAuthorized returns false for unknown key`() {
        assertFalse(AuthorizedKeys.isAuthorized("unknown_key_base64_string"))
    }

    @Test
    fun `authorize and isAuthorized roundtrip`() {
        val key = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEabcdefgh"
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
    fun `isAuthorized matches only first whitespace-delimited field`() {
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
}
