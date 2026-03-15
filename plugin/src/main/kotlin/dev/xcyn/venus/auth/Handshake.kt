package dev.xcyn.venus.auth

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object Handshake {
    private val secureRandom = SecureRandom()

    fun generateChallenge(): ByteArray {
        val challenge = ByteArray(32)
        secureRandom.nextBytes(challenge)
        return challenge
    }

    fun sign(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val signature = Signature.getInstance("Ed25519")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    fun verify(data: ByteArray, signatureBytes: ByteArray, publicKey: PublicKey): Boolean {
        return try {
            val signature = Signature.getInstance("Ed25519")
            signature.initVerify(publicKey)
            signature.update(data)
            signature.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }

    fun decodePublicKey(base64: String): PublicKey {
        val keyBytes = Base64.getDecoder().decode(base64)
        val keyFactory = KeyFactory.getInstance("Ed25519")
        return keyFactory.generatePublic(X509EncodedKeySpec(keyBytes))
    }
}