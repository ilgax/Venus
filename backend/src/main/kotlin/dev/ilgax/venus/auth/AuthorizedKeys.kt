package dev.ilgax.venus.auth

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

object AuthorizedKeys {
    private lateinit var keysFile: File
    private val keys = ConcurrentHashMap<String, String>()

    data class Entry(
        val publicKeyBase64: String,
        val comment: String,
        val fingerprint: String,
    )

    fun init(dataFolder: File) {
        val keysDir = File(dataFolder, "keys")
        keysDir.mkdirs()
        keysFile = File(keysDir, "authorized_keys.txt")
        if (!keysFile.exists()) keysFile.createNewFile()
        loadFromDisk()
    }

    private fun loadFromDisk() {
        keys.clear()
        keysFile.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            val base64 = parts[0]
            val comment = if (parts.size > 1) parts[1] else ""
            if (!keys.containsKey(base64)) {
                keys[base64] = comment
            }
        }
    }

    fun isAuthorized(publicKeyBase64: String): Boolean = keys.containsKey(publicKeyBase64.trim())

    fun authorize(
        publicKeyBase64: String,
        comment: String,
    ) {
        val normalized = publicKeyBase64.trim()
        if (keys.containsKey(normalized)) return
        keys[normalized] = comment
        rewriteFile()
    }

    fun remove(publicKeyBase64: String): Boolean {
        val normalized = publicKeyBase64.trim()
        val removed = keys.remove(normalized) != null
        if (removed) rewriteFile()
        return removed
    }

    fun removeByFingerprint(fingerprint: String): Boolean {
        val entry = keys.entries.firstOrNull { computeFingerprint(it.key) == fingerprint } ?: return false
        keys.remove(entry.key)
        rewriteFile()
        return true
    }

    fun list(): List<Entry> =
        keys.entries.map { (base64, comment) ->
            Entry(
                publicKeyBase64 = base64,
                comment = comment,
                fingerprint = computeFingerprint(base64),
            )
        }

    fun count(): Int = keys.size

    private fun computeFingerprint(publicKeyBase64: String): String =
        try {
            Handshake.fingerprint(Handshake.decodePublicKey(publicKeyBase64))
        } catch (_: Exception) {
            "(invalid key)"
        }

    private fun rewriteFile() {
        val content =
            keys.entries.joinToString("\n") { (base64, comment) ->
                if (comment.isEmpty()) base64 else "$base64 $comment"
            }
        val tmp = keysFile.toPath().resolveSibling("authorized_keys.txt.tmp")
        Files.write(tmp, content.toByteArray())
        Files.move(tmp, keysFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
