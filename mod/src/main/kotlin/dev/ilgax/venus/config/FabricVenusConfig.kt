package dev.ilgax.venus.config

import dev.ilgax.venus.backend.BackendConfig
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
                maxUsers = values["max_users"]?.toIntOrNull() ?: BackendConfig.DEFAULT_MAX_USERS,
                authTimeoutSeconds =
                    values["auth_timeout_seconds"]?.toIntOrNull() ?: BackendConfig.DEFAULT_AUTH_TIMEOUT_SECONDS,
            )
        logger.info(
            "Fabric config loaded - max_users: ${current.maxUsers}, auth_timeout: ${current.authTimeoutSeconds}s",
        )
        return current
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

    private fun defaultConfigContents(): String =
        """
        max_users: ${BackendConfig.DEFAULT_MAX_USERS}
        auth_timeout_seconds: ${BackendConfig.DEFAULT_AUTH_TIMEOUT_SECONDS}
        """.trimIndent() + "\n"

    companion object {
        private const val CONFIG_FILE_NAME = "config.yml"
    }
}
