package dev.xcyn.venus.auth

import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

object Handshake {
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
        val keyBytes = java.util.Base64.getDecoder().decode(base64)
        val keyFactory = java.security.KeyFactory.getInstance("Ed25519")
        return keyFactory.generatePublic(java.security.spec.X509EncodedKeySpec(keyBytes))
    }
}