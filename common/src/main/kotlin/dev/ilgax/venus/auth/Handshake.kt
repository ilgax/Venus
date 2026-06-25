package dev.ilgax.venus.auth

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object Handshake {
    const val PROTOCOL_VERSION: Short = 1
    const val ROLE_SERVER: Byte = 1
    const val ROLE_CLIENT: Byte = 2

    private val secureRandom = SecureRandom()

    fun generateChallenge(): ByteArray {
        val challenge = ByteArray(32)
        secureRandom.nextBytes(challenge)
        return challenge
    }

    fun sign(
        data: ByteArray,
        privateKey: PrivateKey,
    ): ByteArray {
        val signature = Signature.getInstance("Ed25519")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    fun verify(
        data: ByteArray,
        signatureBytes: ByteArray,
        publicKey: PublicKey,
    ): Boolean =
        try {
            val signature = Signature.getInstance("Ed25519")
            signature.initVerify(publicKey)
            signature.update(data)
            signature.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }

    fun decodePublicKey(base64: String): PublicKey {
        val keyBytes = Base64.getDecoder().decode(base64)
        val keyFactory = KeyFactory.getInstance("Ed25519")
        return keyFactory.generatePublic(X509EncodedKeySpec(keyBytes))
    }

    fun transcript(
        serverPublicKey: PublicKey,
        clientPublicKey: PublicKey,
        challenge: ByteArray,
        role: Byte,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeShort(PROTOCOL_VERSION.toInt())
            d.writeByte(role.toInt())
            val serverEncoded = serverPublicKey.encoded
            d.writeInt(serverEncoded.size)
            d.write(serverEncoded)
            val clientEncoded = clientPublicKey.encoded
            d.writeInt(clientEncoded.size)
            d.write(clientEncoded)
            d.writeInt(challenge.size)
            d.write(challenge)
        }
        return out.toByteArray()
    }

    fun signTranscript(
        serverPublicKey: PublicKey,
        clientPublicKey: PublicKey,
        challenge: ByteArray,
        role: Byte,
        privateKey: PrivateKey,
    ): ByteArray = sign(transcript(serverPublicKey, clientPublicKey, challenge, role), privateKey)

    fun verifyTranscript(
        serverPublicKey: PublicKey,
        clientPublicKey: PublicKey,
        challenge: ByteArray,
        role: Byte,
        signatureBytes: ByteArray,
        publicKey: PublicKey,
    ): Boolean =
        verify(
            transcript(serverPublicKey, clientPublicKey, challenge, role),
            signatureBytes,
            publicKey,
        )

    fun fingerprint(publicKey: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
        return "SHA256:" + Base64.getEncoder().encodeToString(digest)
    }
}
