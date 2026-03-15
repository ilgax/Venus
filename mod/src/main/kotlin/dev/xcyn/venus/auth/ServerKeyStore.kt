package dev.xcyn.venus.auth

import java.io.File

object ServerKeyStore {

    private lateinit var serversDir: File

    fun init(venusFolder: File) {
        serversDir = File(venusFolder, "servers")
        serversDir.mkdirs()
    }

    private fun keyFile(host: String, port: Int): File {
        val sanitized = host.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return File(serversDir, "${sanitized}_${port}.key")
    }

    fun getStoredKey(host: String, port: Int): String? {
        val file = keyFile(host, port)
        return if (file.exists()) file.readText().trim() else null
    }

    fun storeKey(host: String, port: Int, publicKeyBase64: String) {
        keyFile(host, port).writeText(publicKeyBase64)
    }
}