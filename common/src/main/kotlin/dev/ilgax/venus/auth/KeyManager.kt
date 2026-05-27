package dev.ilgax.venus.auth

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class KeyManager(
    dataFolder: java.io.File,
    privateKeyName: String = "server_private.key",
    publicKeyName: String = "server_public.key",
) {
    private val keysFolder = dataFolder.resolve("keys")
    private val privateKeyFile = keysFolder.resolve(privateKeyName)
    private val publicKeyFile = keysFolder.resolve(publicKeyName)

    lateinit var privateKey: PrivateKey
    lateinit var publicKey: PublicKey
    lateinit var publicKeyBase64: String

    fun loadOrGenerate() {
        if (privateKeyFile.exists() && publicKeyFile.exists()) {
            load()
        } else {
            generate()
        }
    }

    private fun load() {
        val keyFactory = KeyFactory.getInstance("Ed25519")
        privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyFile.readBytes()))
        publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyFile.readBytes()))
        publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.encoded)
    }

    private fun generate() {
        val keyPairGenerator = KeyPairGenerator.getInstance("Ed25519")
        val keyPair = keyPairGenerator.generateKeyPair()
        privateKey = keyPair.private
        publicKey = keyPair.public
        publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.encoded)
        keysFolder.mkdirs()
        privateKeyFile.writeBytes(privateKey.encoded)
        publicKeyFile.writeBytes(publicKey.encoded)
    }
}
