package dev.ilgax.venus.auth

import java.io.File
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
        keyFile(host, port).writeText(publicKeyBase64)
    }

    fun storeKey(
        identity: ServerIdentity,
        publicKeyBase64: String,
    ) {
        storeKey(identity.host, identity.port, publicKeyBase64)
    }
}
