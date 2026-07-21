package dev.ilgax.venus.config

import dev.ilgax.venus.backend.BackendConfig
import dev.ilgax.venus.backend.BackendFileConfig
import dev.ilgax.venus.backend.BackendFileRoot
import dev.ilgax.venus.backend.BackendFileRootMode
import org.slf4j.Logger
import java.io.File

class FabricVenusConfig(
    private val dataFolder: File,
    private val logger: Logger,
) {
    @Volatile
    private var current = BackendConfig()

    val backendConfig: BackendConfig
        get() = current

    fun load(): BackendConfig {
        val configFile = File(dataFolder, CONFIG_FILE_NAME)
        if (!configFile.exists()) {
            dataFolder.mkdirs()
            configFile.writeText(defaultConfigContents())
        }

        val values = parse(configFile.readLines())
        current =
            BackendConfig(
                maxUsers =
                    positiveOrDefault(
                        value = values["max_users"]?.toIntOrNull() ?: BackendConfig.DEFAULT_MAX_USERS,
                        defaultValue = BackendConfig.DEFAULT_MAX_USERS,
                        key = "max_users",
                    ),
                authTimeoutSeconds =
                    positiveOrDefault(
                        value = values["auth_timeout_seconds"]?.toIntOrNull() ?: BackendConfig.DEFAULT_AUTH_TIMEOUT_SECONDS,
                        defaultValue = BackendConfig.DEFAULT_AUTH_TIMEOUT_SECONDS,
                        key = "auth_timeout_seconds",
                    ),
                files = parseFileConfig(configFile.readLines()),
            )
        logger.info(
            "Fabric config loaded - max_users: ${current.maxUsers}, auth_timeout: ${current.authTimeoutSeconds}s",
        )
        return current
    }

    fun save(config: BackendConfig) {
        dataFolder.mkdirs()
        current = config
        File(dataFolder, CONFIG_FILE_NAME).writeText(serialize(config))
    }

    private fun serialize(config: BackendConfig): String =
        buildString {
            appendLine("max_users: ${config.maxUsers}")
            appendLine("auth_timeout_seconds: ${config.authTimeoutSeconds}")
            appendLine("files:")
            appendLine("  reserved_free_bytes: ${config.files.reservedFreeBytes}")
            appendLine("  max_concurrent_transfers: ${config.files.maxConcurrentTransfers}")
            appendLine("  idle_timeout_seconds: ${config.files.idleTimeoutSeconds}")
            if (config.files.roots.isEmpty()) {
                appendLine("  roots: {}")
            } else {
                appendLine("  roots:")
                config.files.roots.forEach { root ->
                    appendLine("    ${root.id}:")
                    appendLine("      label: ${root.label}")
                    appendLine("      path: ${root.path}")
                    appendLine("      mode: ${root.mode.name.lowercase()}")
                }
            }
        }

    private fun parseFileConfig(lines: List<String>): BackendFileConfig {
        var reserved = BackendFileConfig.DEFAULT_RESERVED_FREE_BYTES
        var concurrent = BackendFileConfig.DEFAULT_MAX_CONCURRENT_TRANSFERS
        var timeout = BackendFileConfig.DEFAULT_IDLE_TIMEOUT_SECONDS
        val rootValues = linkedMapOf<String, MutableMap<String, String>>()
        var inFiles = false
        var inRoots = false
        var currentRoot: String? = null
        lines.forEach { raw ->
            val line = raw.substringBefore('#').trimEnd()
            if (line.isBlank()) return@forEach
            val indent = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val trimmed = line.trim()
            when {
                indent == 0 -> {
                    inFiles = trimmed == "files:"
                    inRoots = false
                    currentRoot = null
                }
                inFiles && indent == 2 && trimmed == "roots:" -> {
                    inRoots = true
                    currentRoot = null
                }
                inFiles && indent == 2 -> {
                    inRoots = false
                    val key = trimmed.substringBefore(':')
                    val value = trimmed.substringAfter(':').trim()
                    when (key) {
                        "reserved_free_bytes" -> reserved = value.toLongOrNull()?.coerceAtLeast(0) ?: reserved
                        "max_concurrent_transfers" -> concurrent = value.toIntOrNull()?.coerceIn(1, 8) ?: concurrent
                        "idle_timeout_seconds" -> timeout = value.toIntOrNull()?.coerceIn(5, 600) ?: timeout
                    }
                }
                inFiles && inRoots && indent == 4 && trimmed.endsWith(':') -> {
                    val rootId = trimmed.removeSuffix(":")
                    currentRoot = rootId
                    rootValues.getOrPut(rootId) { linkedMapOf() }
                }
                inFiles && inRoots && indent >= 6 && currentRoot != null -> {
                    rootValues[currentRoot]!![trimmed.substringBefore(':')] = trimmed.substringAfter(':').trim()
                }
            }
        }
        val roots =
            rootValues.mapNotNull { (id, values) ->
                val mode =
                    when (values["mode"]?.lowercase()) {
                        "read_write" -> BackendFileRootMode.READ_WRITE
                        "read_only", null -> BackendFileRootMode.READ_ONLY
                        else -> return@mapNotNull null
                    }
                runCatching {
                    BackendFileRoot(id, values["label"] ?: id, values["path"].orEmpty(), mode)
                }.getOrNull()
            }
        return BackendFileConfig(roots, reserved, concurrent, timeout)
    }

    private fun parse(lines: List<String>): Map<String, String> =
        lines
            .asSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() && ':' in it }
            .associate { line ->
                val key = line.substringBefore(':').trim()
                val value = line.substringAfter(':').trim()
                key to value
            }

    private fun positiveOrDefault(
        value: Int,
        defaultValue: Int,
        key: String,
    ): Int {
        if (value >= 1) return value
        logger.warn("Invalid Fabric Venus config {}={}; using default {}.", key, value, defaultValue)
        return defaultValue
    }

    private fun defaultConfigContents(): String =
        """
        max_users: ${BackendConfig.DEFAULT_MAX_USERS}
        auth_timeout_seconds: ${BackendConfig.DEFAULT_AUTH_TIMEOUT_SECONDS}
        files:
          reserved_free_bytes: ${BackendFileConfig.DEFAULT_RESERVED_FREE_BYTES}
          max_concurrent_transfers: ${BackendFileConfig.DEFAULT_MAX_CONCURRENT_TRANSFERS}
          idle_timeout_seconds: ${BackendFileConfig.DEFAULT_IDLE_TIMEOUT_SECONDS}
          roots:
        """.trimIndent() + "\n"

    companion object {
        private const val CONFIG_FILE_NAME = "config.yml"
    }
}
