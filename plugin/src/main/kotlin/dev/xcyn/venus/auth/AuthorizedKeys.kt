package dev.xcyn.venus.auth

import java.io.File

object AuthorizedKeys {

    private lateinit var keysFile: File

    fun init(dataFolder: File) {
        val keysDir = File(dataFolder, "keys")
        keysDir.mkdirs()
        keysFile = File(keysDir, "authorized_keys.txt")
        if (!keysFile.exists()) keysFile.createNewFile()
    }

    fun isAuthorized(publicKeyBase64: String): Boolean {
        return keysFile.readLines().any {
            it.trim().split(" ")[0] == publicKeyBase64.trim()
        }
    }

    fun authorize(publicKeyBase64: String, comment: String) {
        keysFile.appendText("$publicKeyBase64 $comment\n")
    }
}