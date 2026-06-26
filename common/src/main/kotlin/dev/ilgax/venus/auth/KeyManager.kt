package dev.ilgax.venus.auth

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class KeyManager(
    dataFolder: java.io.File,
    privateKeyName: String = "server_private.key",
    publicKeyName: String = "server_public.key",
) {
    private val keysFolder = dataFolder.resolve("keys")
    private val privateKeyFile = keysFolder.resolve(privateKeyName)
    private val publicKeyFile = keysFolder.resolve(publicKeyName)

    @Volatile private var _privateKey: PrivateKey? = null

    @Volatile private var _publicKey: PublicKey? = null

    @Volatile private var _publicKeyBase64: String? = null

    val privateKey: PrivateKey
        get() = _privateKey ?: error("KeyManager not initialized; call loadOrGenerate() first")

    val publicKey: PublicKey
        get() = _publicKey ?: error("KeyManager not initialized; call loadOrGenerate() first")

    val publicKeyBase64: String
        get() = _publicKeyBase64 ?: error("KeyManager not initialized; call loadOrGenerate() first")

    @Synchronized
    fun loadOrGenerate() {
        synchronized(lockFor(privateKeyFile.toPath(), publicKeyFile.toPath())) {
            val privateExists = privateKeyFile.exists()
            val publicExists = publicKeyFile.exists()
            when {
                privateExists && publicExists -> load()
                !privateExists && !publicExists -> generate()
                else ->
                    throw IllegalStateException(
                        "Venus key files in inconsistent state: private exists=$privateExists, public exists=$publicExists. " +
                            "Refusing to silently regenerate server identity. Delete both files to regenerate, or restore the missing file.",
                    )
            }
        }
    }

    private fun load() {
        val keyFactory = KeyFactory.getInstance("Ed25519")
        val privKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyFile.readBytes()))
        val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyFile.readBytes()))
        if (!keysMatch(privKey, pubKey)) {
            throw IllegalStateException(
                "Venus key pair mismatch: public key does not correspond to private key. Refusing to load inconsistent key files.",
            )
        }
        _privateKey = privKey
        _publicKey = pubKey
        _publicKeyBase64 = Base64.getEncoder().encodeToString(pubKey.encoded)
        restrictPermissions(privateKeyFile.toPath(), privateOnly = true)
        restrictPermissions(publicKeyFile.toPath(), privateOnly = false)
        restrictDirPermissions()
    }

    private fun generate() {
        if (!keysFolder.exists() && !keysFolder.mkdirs()) {
            throw IllegalStateException("Failed to create Venus keys directory: ${keysFolder.absolutePath}")
        }
        restrictDirPermissions()
        val keyPairGenerator = KeyPairGenerator.getInstance("Ed25519")
        val keyPair = keyPairGenerator.generateKeyPair()
        _privateKey = keyPair.private
        _publicKey = keyPair.public
        _publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        atomicWrite(privateKeyFile.toPath(), keyPair.private.encoded, privateOnly = true)
        atomicWrite(publicKeyFile.toPath(), keyPair.public.encoded, privateOnly = false)
    }

    private fun keysMatch(
        privateKey: PrivateKey,
        publicKey: PublicKey,
    ): Boolean {
        val data = Handshake.generateChallenge()
        val sig = Handshake.sign(data, privateKey)
        return Handshake.verify(data, sig, publicKey)
    }

    private fun atomicWrite(
        target: Path,
        bytes: ByteArray,
        privateOnly: Boolean,
    ) {
        val tmp = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
        try {
            Files.write(tmp, bytes)
            restrictPermissions(tmp, privateOnly)
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun restrictPermissions(
        path: Path,
        privateOnly: Boolean,
    ) {
        try {
            val perms =
                if (privateOnly) {
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                } else {
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.OTHERS_READ,
                    )
                }
            Files.setPosixFilePermissions(path, perms)
        } catch (_: UnsupportedOperationException) {
            restrictFilePermissionsFallback(path, privateOnly)
        }
    }

    private fun restrictDirPermissions() {
        try {
            Files.setPosixFilePermissions(
                keysFolder.toPath(),
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
        } catch (_: UnsupportedOperationException) {
            restrictDirectoryPermissionsFallback()
        }
    }

    private fun restrictFilePermissionsFallback(
        path: Path,
        privateOnly: Boolean,
    ) {
        val file = path.toFile()
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, !privateOnly)
        file.setWritable(true, true)
    }

    private fun restrictDirectoryPermissionsFallback() {
        keysFolder.setReadable(false, false)
        keysFolder.setWritable(false, false)
        keysFolder.setExecutable(false, false)
        keysFolder.setReadable(true, true)
        keysFolder.setWritable(true, true)
        keysFolder.setExecutable(true, true)
    }

    private fun lockFor(
        privateKeyPath: Path,
        publicKeyPath: Path,
    ): Any {
        val key =
            privateKeyPath.toAbsolutePath().normalize().toString() +
                "|" +
                publicKeyPath.toAbsolutePath().normalize().toString()
        return loadLocks.computeIfAbsent(key) { Any() }
    }

    companion object {
        private val loadLocks = ConcurrentHashMap<String, Any>()
    }
}
