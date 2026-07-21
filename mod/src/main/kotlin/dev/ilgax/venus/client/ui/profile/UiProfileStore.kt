package dev.ilgax.venus.client.ui.profile

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

class UiProfileStore(
    private val dataFolder: File,
) {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = false
        }

    fun load(): UiProfileLoadResult {
        val primary = dataFolder.resolve(FILE_NAME)
        val backup = dataFolder.resolve(BACKUP_FILE_NAME)
        if (!primary.isFile) return UiProfileLoadResult(UiProfilesFile(), UiProfileRecovery.NONE)
        read(primary)?.let { return UiProfileLoadResult(it, UiProfileRecovery.NONE) }
        read(backup)?.let { return UiProfileLoadResult(it, UiProfileRecovery.BACKUP) }
        return UiProfileLoadResult(UiProfilesFile(), UiProfileRecovery.FACTORY)
    }

    fun save(file: UiProfilesFile) {
        UiProfileValidator.validate(file)
        dataFolder.mkdirs()
        val primary = dataFolder.resolve(FILE_NAME).toPath()
        val backup = dataFolder.resolve(BACKUP_FILE_NAME).toPath()
        val temporary = dataFolder.resolve(TEMP_FILE_NAME).toPath()
        Files.writeString(temporary, json.encodeToString(file), StandardCharsets.UTF_8)
        if (Files.isRegularFile(primary)) {
            Files.copy(primary, backup, StandardCopyOption.REPLACE_EXISTING)
        }
        try {
            Files.move(temporary, primary, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, primary, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun resolve(
        file: UiProfilesFile,
        serverAddress: String?,
    ): UiProfile {
        val assigned = serverAddress?.let(::canonicalServerAddress)?.let(file.serverAssignments::get)
        val selected = assigned ?: file.activeProfileId
        return file.profiles.firstOrNull { it.id == selected } ?: FactoryUiProfile.profile
    }

    fun export(profile: UiProfile): String {
        UiProfileValidator.validateProfile(profile)
        val bytes = json.encodeToString(profile).toByteArray(StandardCharsets.UTF_8)
        val compressed =
            ByteArrayOutputStream().use { output ->
                DeflaterOutputStream(output).use { it.write(bytes) }
                output.toByteArray()
            }
        return EXPORT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    fun import(code: String): UiProfile {
        require(code.startsWith(EXPORT_PREFIX)) { "Invalid Venus UI profile code" }
        val compressed = Base64.getUrlDecoder().decode(code.removePrefix(EXPORT_PREFIX))
        val decoded =
            InflaterInputStream(compressed.inputStream()).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    require(output.size() <= MAX_IMPORT_BYTES) { "Venus UI profile code is too large" }
                }
                output.toString(StandardCharsets.UTF_8)
            }
        return json.decodeFromString<UiProfile>(decoded).also(UiProfileValidator::validateProfile)
    }

    private fun read(file: File): UiProfilesFile? =
        runCatching {
            json.decodeFromString<UiProfilesFile>(file.readText()).also(UiProfileValidator::validate)
        }.getOrNull()

    companion object {
        const val EXPORT_PREFIX = "VENUSUI1:"
        const val MAX_IMPORT_BYTES = 512 * 1024
        private const val FILE_NAME = "ui-profiles.json"
        private const val BACKUP_FILE_NAME = "ui-profiles.json.bak"
        private const val TEMP_FILE_NAME = "ui-profiles.json.tmp"

        fun canonicalServerAddress(address: String): String {
            val trimmed = address.trim().lowercase()
            return if (trimmed.startsWith("[") || trimmed.count { it == ':' } == 1) trimmed else "$trimmed:25565"
        }
    }
}

data class UiProfileLoadResult(
    val file: UiProfilesFile,
    val recovery: UiProfileRecovery,
)

enum class UiProfileRecovery {
    NONE,
    BACKUP,
    FACTORY,
}
