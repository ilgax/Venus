package dev.ilgax.venus.auth

import java.nio.file.Files
import java.security.KeyPairGenerator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeyManagerTest {
    private lateinit var tempDir: java.io.File

    @BeforeTest
    fun setup() {
        tempDir =
            kotlin.io.path
                .createTempDirectory("venus-test-keys")
                .toFile()
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `loadOrGenerate creates key files with default server prefix`() {
        val km = KeyManager(tempDir)
        km.loadOrGenerate()
        val keysDir = tempDir.resolve("keys")
        assertTrue(keysDir.isDirectory)
        assertTrue(keysDir.resolve("server_private.key").exists())
        assertTrue(keysDir.resolve("server_public.key").exists())
    }

    @Test
    fun `loadOrGenerate creates key files with custom client prefix`() {
        val km = KeyManager(tempDir, "client_private.key", "client_public.key")
        km.loadOrGenerate()
        val keysDir = tempDir.resolve("keys")
        assertTrue(keysDir.resolve("client_private.key").exists())
        assertTrue(keysDir.resolve("client_public.key").exists())
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
    fun `loadOrGenerate throws when only private key exists`() {
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val keysDir = tempDir.resolve("keys")
        keysDir.mkdirs()
        keysDir.resolve("server_private.key").writeBytes(kp.private.encoded)

        val km = KeyManager(tempDir)
        assertFailsWith<IllegalStateException> { km.loadOrGenerate() }
    }

    @Test
    fun `loadOrGenerate throws when only public key exists`() {
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val keysDir = tempDir.resolve("keys")
        keysDir.mkdirs()
        keysDir.resolve("server_public.key").writeBytes(kp.public.encoded)

        val km = KeyManager(tempDir)
        assertFailsWith<IllegalStateException> { km.loadOrGenerate() }
    }

    @Test
    fun `loadOrGenerate throws on corrupt private key bytes`() {
        val keysDir = tempDir.resolve("keys")
        keysDir.mkdirs()
        keysDir.resolve("server_private.key").writeBytes(ByteArray(64) { 0x42 })
        keysDir.resolve("server_public.key").writeBytes(ByteArray(64) { 0x42 })

        val km = KeyManager(tempDir)
        assertFailsWith<Exception> { km.loadOrGenerate() }
    }

    @Test
    fun `loadOrGenerate throws on key-pair mismatch`() {
        val kp1 = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val kp2 = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val keysDir = tempDir.resolve("keys")
        keysDir.mkdirs()
        keysDir.resolve("server_private.key").writeBytes(kp1.private.encoded)
        keysDir.resolve("server_public.key").writeBytes(kp2.public.encoded)

        val km = KeyManager(tempDir)
        assertFailsWith<IllegalStateException> { km.loadOrGenerate() }
    }

    @Test
    fun `private key file has owner-only permissions on POSIX`() {
        val km = KeyManager(tempDir)
        km.loadOrGenerate()
        val privateKeyPath = tempDir.resolve("keys").resolve("server_private.key").toPath()
        try {
            val perms = Files.getPosixFilePermissions(privateKeyPath)
            assertEquals(
                setOf(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                ),
                perms,
            )
        } catch (_: UnsupportedOperationException) {
            // Windows / non-POSIX; skip
        }
    }

    @Test
    fun `concurrent loadOrGenerate on same instance does not corrupt keys`() {
        val km = KeyManager(tempDir)
        val threadCount = 10
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val results = ConcurrentHashMap.newKeySet<String>()

        repeat(threadCount) {
            Thread {
                startLatch.await()
                km.loadOrGenerate()
                results.add(km.publicKeyBase64)
                doneLatch.countDown()
            }.start()
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS))
        assertEquals(1, results.size, "All threads should see the same public key")

        val km2 = KeyManager(tempDir)
        km2.loadOrGenerate()
        assertEquals(km.publicKeyBase64, km2.publicKeyBase64)
    }

    @Test
    fun `concurrent loadOrGenerate across instances does not corrupt keys`() {
        val threadCount = 10
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val results = ConcurrentHashMap.newKeySet<String>()
        val errors = ConcurrentLinkedQueue<Throwable>()

        repeat(threadCount) {
            Thread {
                try {
                    startLatch.await()
                    val km = KeyManager(tempDir)
                    km.loadOrGenerate()
                    results.add(km.publicKeyBase64)
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    doneLatch.countDown()
                }
            }.start()
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS))
        assertTrue(errors.isEmpty(), "No thread should fail: ${errors.map { it.message }}")
        assertEquals(1, results.size, "All instances should see the same public key")

        val km = KeyManager(tempDir)
        km.loadOrGenerate()
        assertEquals(results.single(), km.publicKeyBase64)
    }
}
