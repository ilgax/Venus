package dev.xcyn.venus.auth

import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class KeyManager(private val dataFolder: java.io.File) {
    private val keysFolder = dataFolder.resolve("keys")
    private val privateKeyFile = keysFolder.resolve("server_private.key")
    private val publicKeyFile = keysFolder.resolve("server_public.key")

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