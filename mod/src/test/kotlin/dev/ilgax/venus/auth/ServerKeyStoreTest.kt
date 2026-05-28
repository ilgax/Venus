package dev.ilgax.venus.auth

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerKeyStoreTest {
    private lateinit var tempDir: java.io.File

    @BeforeTest
    fun setup() {
        tempDir =
            kotlin.io.path
                .createTempDirectory("venus-test-sks")
                .toFile()
        ServerKeyStore.init(tempDir)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `init creates servers directory`() {
        assertTrue(tempDir.resolve("servers").isDirectory)
    }

    @Test
    fun `getStoredKey returns null for unknown host`() {
        assertNull(ServerKeyStore.getStoredKey("unknown.example.com", 25565))
    }

    @Test
    fun `storeKey and getStoredKey roundtrip`() {
        val key = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtestKey123"
        ServerKeyStore.storeKey("example.com", 25565, key)
        val stored = ServerKeyStore.getStoredKey("example.com", 25565)
        assertNotNull(stored)
        assertEquals(key, stored)
    }

    @Test
    fun `different ports store separately`() {
        val key1 = "key_for_25565"
        val key2 = "key_for_25566"
        ServerKeyStore.storeKey("example.com", 25565, key1)
        ServerKeyStore.storeKey("example.com", 25566, key2)
        assertEquals(key1, ServerKeyStore.getStoredKey("example.com", 25565))
        assertEquals(key2, ServerKeyStore.getStoredKey("example.com", 25566))
    }

    @Test
    fun `different hosts store separately`() {
        ServerKeyStore.storeKey("a.example.com", 25565, "key_a")
        ServerKeyStore.storeKey("b.example.com", 25565, "key_b")
        assertEquals("key_a", ServerKeyStore.getStoredKey("a.example.com", 25565))
        assertEquals("key_b", ServerKeyStore.getStoredKey("b.example.com", 25565))
    }

    @Test
    fun `host and port together define stored server identity`() {
        ServerKeyStore.storeKey("example.com", 25565, "host_key")
        ServerKeyStore.storeKey("example.com:25565", 25565, "address_key")

        assertEquals("host_key", ServerKeyStore.getStoredKey("example.com", 25565))
        assertEquals("address_key", ServerKeyStore.getStoredKey("example.com:25565", 25565))
    }

    @Test
    fun `normalizeHost lowercases and removes trailing dot`() {
        assertEquals("example.com", ServerKeyStore.normalizeHost(" Example.COM. "))
    }

    @Test
    fun `host with special characters is sanitized`() {
        val key = "some_key_value"
        ServerKeyStore.storeKey("host with spaces:8080", 25565, key)
        val stored = ServerKeyStore.getStoredKey("host with spaces:8080", 25565)
        assertNotNull(stored)
        assertEquals(key, stored)
    }

    @Test
    fun `host with colon in address is sanitized`() {
        val key = "ipv6_key"
        ServerKeyStore.storeKey("mc.example.com:25565", 25565, key)
        val stored = ServerKeyStore.getStoredKey("mc.example.com:25565", 25565)
        assertNotNull(stored)
        assertEquals(key, stored)
    }

    @Test
    fun `stored key is trimmed`() {
        val key = "  key_with_spaces  "
        ServerKeyStore.storeKey("trim.example.com", 25565, key)
        val stored = ServerKeyStore.getStoredKey("trim.example.com", 25565)
        assertEquals(key.trim(), stored)
    }
}
