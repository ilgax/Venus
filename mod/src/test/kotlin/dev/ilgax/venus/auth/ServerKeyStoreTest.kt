package dev.ilgax.venus.auth

import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

    private fun genKeyB64(): String {
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        return Base64.getEncoder().encodeToString(kp.public.encoded)
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
        val key = genKeyB64()
        ServerKeyStore.storeKey("example.com", 25565, key)
        val stored = ServerKeyStore.getStoredKey("example.com", 25565)
        assertNotNull(stored)
        assertEquals(key, stored)
    }

    @Test
    fun `storeKey recreates servers directory when deleted`() {
        val serversDir = tempDir.resolve("servers")
        serversDir.deleteRecursively()

        val key = genKeyB64()
        ServerKeyStore.storeKey("localhost", 25565, key)

        assertTrue(serversDir.isDirectory)
        assertEquals(key, ServerKeyStore.getStoredKey("localhost", 25565))
    }

    @Test
    fun `different ports store separately`() {
        val key1 = genKeyB64()
        val key2 = genKeyB64()
        ServerKeyStore.storeKey("example.com", 25565, key1)
        ServerKeyStore.storeKey("example.com", 25566, key2)
        assertEquals(key1, ServerKeyStore.getStoredKey("example.com", 25565))
        assertEquals(key2, ServerKeyStore.getStoredKey("example.com", 25566))
    }

    @Test
    fun `different hosts store separately`() {
        val keyA = genKeyB64()
        val keyB = genKeyB64()
        ServerKeyStore.storeKey("a.example.com", 25565, keyA)
        ServerKeyStore.storeKey("b.example.com", 25565, keyB)
        assertEquals(keyA, ServerKeyStore.getStoredKey("a.example.com", 25565))
        assertEquals(keyB, ServerKeyStore.getStoredKey("b.example.com", 25565))
    }

    @Test
    fun `host and port together define stored server identity`() {
        val key1 = genKeyB64()
        val key2 = genKeyB64()
        ServerKeyStore.storeKey("example.com", 25565, key1)
        ServerKeyStore.storeKey("example.com:25565", 25565, key2)

        assertEquals(key1, ServerKeyStore.getStoredKey("example.com", 25565))
        assertEquals(key2, ServerKeyStore.getStoredKey("example.com:25565", 25565))
    }

    @Test
    fun `normalizeHost lowercases and removes trailing dot`() {
        assertEquals("example.com", ServerKeyStore.normalizeHost(" Example.COM. "))
    }

    @Test
    fun `host with special characters is sanitized`() {
        val key = genKeyB64()
        ServerKeyStore.storeKey("host with spaces:8080", 25565, key)
        val stored = ServerKeyStore.getStoredKey("host with spaces:8080", 25565)
        assertNotNull(stored)
        assertEquals(key, stored)
    }

    @Test
    fun `host with colon in address is sanitized`() {
        val key = genKeyB64()
        ServerKeyStore.storeKey("mc.example.com:25565", 25565, key)
        val stored = ServerKeyStore.getStoredKey("mc.example.com:25565", 25565)
        assertNotNull(stored)
        assertEquals(key, stored)
    }

    @Test
    fun `stored key is trimmed`() {
        val key = genKeyB64()
        ServerKeyStore.storeKey("trim.example.com", 25565, "  $key  ")
        val stored = ServerKeyStore.getStoredKey("trim.example.com", 25565)
        assertEquals(key, stored)
    }

    @Test
    fun `storeKey with non-decodable base64 throws and does not write`() {
        val file = tempDir.resolve("servers").resolve("bad_example_com_25565.key")
        assertFailsWith<Exception> {
            ServerKeyStore.storeKey("bad.example.com", 25565, "not-a-valid-key!!!")
        }
        assertFalse(file.exists())
    }

    @Test
    fun `storeKey with empty string throws`() {
        assertFailsWith<Exception> {
            ServerKeyStore.storeKey("empty.example.com", 25565, "")
        }
    }
}
