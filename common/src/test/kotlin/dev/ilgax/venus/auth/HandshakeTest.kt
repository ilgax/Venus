package dev.ilgax.venus.auth

import java.security.KeyPairGenerator
import java.security.spec.InvalidKeySpecException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandshakeTest {
    private val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    @Test
    fun `generateChallenge returns 32 bytes`() {
        val challenge = Handshake.generateChallenge()
        assertEquals(32, challenge.size)
    }

    @Test
    fun `generateChallenge produces different values each call`() {
        val a = Handshake.generateChallenge()
        val b = Handshake.generateChallenge()
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `sign and verify roundtrip`() {
        val data = "hello venus".toByteArray()
        val sig = Handshake.sign(data, keyPair.private)
        assertTrue(Handshake.verify(data, sig, keyPair.public))
    }

    @Test
    fun `verify fails with tampered data`() {
        val data = "hello venus".toByteArray()
        val sig = Handshake.sign(data, keyPair.private)
        val tampered = "hello v3nus".toByteArray()
        assertFalse(Handshake.verify(tampered, sig, keyPair.public))
    }

    @Test
    fun `verify fails with wrong public key`() {
        val otherKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val data = "hello venus".toByteArray()
        val sig = Handshake.sign(data, keyPair.private)
        assertFalse(Handshake.verify(data, sig, otherKeyPair.public))
    }

    @Test
    fun `verify fails with invalid signature bytes`() {
        val data = "hello".toByteArray()
        val badSig = ByteArray(64) { 0 }
        assertFalse(Handshake.verify(data, badSig, keyPair.public))
    }

    @Test
    fun `verify returns false on any exception`() {
        val data = "hello".toByteArray()
        val shortSig = ByteArray(10)
        assertFalse(Handshake.verify(data, shortSig, keyPair.public))
    }

    @Test
    fun `decodePublicKey roundtrip`() {
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString(keyPair.public.encoded)
        val decoded = Handshake.decodePublicKey(b64)
        assertEquals(keyPair.public, decoded)
    }

    @Test
    fun `decodePublicKey throws on invalid base64`() {
        assertFailsWith<IllegalArgumentException> {
            Handshake.decodePublicKey("not-valid-base64!!!")
        }
    }

    @Test
    fun `decodePublicKey fails on empty string`() {
        assertFailsWith<InvalidKeySpecException> {
            Handshake.decodePublicKey("")
        }
    }

    @Test
    fun `sign returns 64 byte signature for Ed25519`() {
        val data = "test".toByteArray()
        val sig = Handshake.sign(data, keyPair.private)
        assertEquals(64, sig.size)
    }

    @Test
    fun `sign produces deterministic Ed25519 signatures`() {
        val data = "same data".toByteArray()
        val sig1 = Handshake.sign(data, keyPair.private)
        val sig2 = Handshake.sign(data, keyPair.private)
        assertTrue(sig1.contentEquals(sig2))
    }

    @Test
    fun `signTranscript and verifyTranscript roundtrip succeeds`() {
        val serverKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val clientKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val challenge = Handshake.generateChallenge()

        val serverSig =
            Handshake.signTranscript(serverKp.public, clientKp.public, challenge, Handshake.ROLE_SERVER, serverKp.private)
        val clientSig =
            Handshake.signTranscript(serverKp.public, clientKp.public, challenge, Handshake.ROLE_CLIENT, clientKp.private)

        assertTrue(
            Handshake.verifyTranscript(serverKp.public, clientKp.public, challenge, Handshake.ROLE_SERVER, serverSig, serverKp.public),
        )
        assertTrue(
            Handshake.verifyTranscript(serverKp.public, clientKp.public, challenge, Handshake.ROLE_CLIENT, clientSig, clientKp.public),
        )
    }

    @Test
    fun `server-role signature does not verify as client-role (reflection prevention)`() {
        val serverKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val clientKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val challenge = Handshake.generateChallenge()

        val serverSig =
            Handshake.signTranscript(serverKp.public, clientKp.public, challenge, Handshake.ROLE_SERVER, serverKp.private)

        assertFalse(
            Handshake.verifyTranscript(serverKp.public, clientKp.public, challenge, Handshake.ROLE_CLIENT, serverSig, serverKp.public),
        )
    }

    @Test
    fun `verifyTranscript fails with different server key`() {
        val serverKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val otherServerKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val clientKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val challenge = Handshake.generateChallenge()

        val serverSig =
            Handshake.signTranscript(serverKp.public, clientKp.public, challenge, Handshake.ROLE_SERVER, serverKp.private)

        assertFalse(
            Handshake.verifyTranscript(otherServerKp.public, clientKp.public, challenge, Handshake.ROLE_SERVER, serverSig, serverKp.public),
        )
    }

    @Test
    fun `verifyTranscript fails with different client key`() {
        val serverKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val clientKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val otherClientKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val challenge = Handshake.generateChallenge()

        val clientSig =
            Handshake.signTranscript(serverKp.public, clientKp.public, challenge, Handshake.ROLE_CLIENT, clientKp.private)

        assertFalse(
            Handshake.verifyTranscript(serverKp.public, otherClientKp.public, challenge, Handshake.ROLE_CLIENT, clientSig, clientKp.public),
        )
    }

    @Test
    fun `verifyTranscript fails with different challenge`() {
        val serverKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val clientKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val challenge = Handshake.generateChallenge()
        val otherChallenge = Handshake.generateChallenge()

        val serverSig =
            Handshake.signTranscript(serverKp.public, clientKp.public, challenge, Handshake.ROLE_SERVER, serverKp.private)

        assertFalse(
            Handshake.verifyTranscript(serverKp.public, clientKp.public, otherChallenge, Handshake.ROLE_SERVER, serverSig, serverKp.public),
        )
    }

    @Test
    fun `fingerprint is deterministic and prefixed`() {
        val fp = Handshake.fingerprint(keyPair.public)
        assertTrue(fp.startsWith("SHA256:"))
        assertEquals(fp, Handshake.fingerprint(keyPair.public))
    }

    @Test
    fun `fingerprint differs across keys`() {
        val otherKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val fp1 = Handshake.fingerprint(keyPair.public)
        val fp2 = Handshake.fingerprint(otherKp.public)
        assertFalse(fp1 == fp2)
    }
}
