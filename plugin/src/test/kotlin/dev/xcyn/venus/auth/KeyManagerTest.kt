package dev.xcyn.venus.auth

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeyManagerTest {

    private lateinit var tempDir: java.io.File

    @BeforeTest
    fun setup() {
        tempDir = kotlin.io.path.createTempDirectory("venus-test-keys").toFile()
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `loadOrGenerate creates key files on fresh directory`() {
        val km = KeyManager(tempDir)
        km.loadOrGenerate()
        val keysDir = tempDir.resolve("keys")
        assertTrue(keysDir.isDirectory)
        assertTrue(keysDir.resolve("server_private.key").exists())
        assertTrue(keysDir.resolve("server_public.key").exists())
    }

    @Test
    fun `loadOrGenerate populates publicKeyBase64`() {
        val km = KeyManager(tempDir)
        km.loadOrGenerate()
        assertNotNull(km.publicKeyBase64)
        assertTrue(km.publicKeyBase64.isNotBlank())
    }

    @Test
    fun `loadOrGenerate produces valid publicKey and privateKey`() {
        val km = KeyManager(tempDir)
        km.loadOrGenerate()
        assertNotNull(km.publicKey)
        assertNotNull(km.privateKey)
    }

    @Test
    fun `second instance loads existing keys instead of generating`() {
        val km1 = KeyManager(tempDir)
        km1.loadOrGenerate()
        val firstPublicKey = km1.publicKeyBase64

        val km2 = KeyManager(tempDir)
        km2.loadOrGenerate()
        assertEquals(firstPublicKey, km2.publicKeyBase64)
        assertEquals(km1.publicKey, km2.publicKey)
        assertEquals(km1.privateKey, km2.privateKey)
    }

    @Test
    fun `generated publicKeyBase64 can be decoded back`() {
        val km = KeyManager(tempDir)
        km.loadOrGenerate()
        val decoded = Handshake.decodePublicKey(km.publicKeyBase64)
        assertEquals(km.publicKey, decoded)
    }

    @Test
    fun `key files use server prefix`() {
        val km = KeyManager(tempDir)
        km.loadOrGenerate()
        val keysDir = tempDir.resolve("keys")
        assertTrue(keysDir.resolve("server_private.key").exists())
        assertTrue(keysDir.resolve("server_public.key").exists())
    }
}
