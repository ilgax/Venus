package dev.ilgax.venus.auth

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.Locale

object ServerKeyStore {
    data class ServerIdentity(
        val host: String,
        val port: Int,
    ) {
        override fun toString(): String = "$host:$port"
    }

    private lateinit var serversDir: File

    fun init(venusFolder: File) {
        serversDir = File(venusFolder, "servers")
        serversDir.mkdirs()
        restrictDirPermissions()
    }

    private fun keyFile(
        host: String,
        port: Int,
    ): File {
        val sanitized = normalizeHost(host).replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return File(serversDir, "${sanitized}_$port.key")
    }

    fun normalizeHost(host: String): String =
        host
            .trim()
            .trimEnd('.')
            .lowercase(Locale.ROOT)

    fun getStoredKey(
        host: String,
        port: Int,
    ): String? {
        val file = keyFile(host, port)
        return if (file.exists()) file.readText().trim() else null
    }

    fun getStoredKey(identity: ServerIdentity): String? = getStoredKey(identity.host, identity.port)

    fun storeKey(
        host: String,
        port: Int,
        publicKeyBase64: String,
    ) {
        Handshake.decodePublicKey(publicKeyBase64.trim())
        if (!serversDir.exists() && !serversDir.mkdirs()) {
            throw IllegalStateException("Failed to create Venus servers directory: ${serversDir.absolutePath}")
        }
        restrictDirPermissions()
        val target = keyFile(host, port).toPath()
        val tmp = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.write(tmp, publicKeyBase64.trim().toByteArray(Charsets.UTF_8))
        restrictFilePermissions(tmp)
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun storeKey(
        identity: ServerIdentity,
        publicKeyBase64: String,
    ) {
        storeKey(identity.host, identity.port, publicKeyBase64)
    }

    private fun restrictFilePermissions(path: java.nio.file.Path) {
        try {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        } catch (_: UnsupportedOperationException) {
            // Non-POSIX FS (e.g. Windows); no action.
        }
    }

    private fun restrictDirPermissions() {
        try {
            Files.setPosixFilePermissions(
                serversDir.toPath(),
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
        } catch (_: UnsupportedOperationException) {
            // Non-POSIX FS (e.g. Windows); no action.
        }
    }
}
