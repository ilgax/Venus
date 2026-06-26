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
                compactMode =
                    boolOrDefault(values["compact_mode"], BackendConfig.DEFAULT_COMPACT_MODE, "compact_mode"),
                animationsEnabled =
                    boolOrDefault(values["animations_enabled"], BackendConfig.DEFAULT_ANIMATIONS_ENABLED, "animations_enabled"),
                backgroundOpacity =
                    opacityOrDefault(values["background_opacity"], BackendConfig.DEFAULT_BACKGROUND_OPACITY, "background_opacity"),
                showPlayerHeads =
                    boolOrDefault(values["show_player_heads"], BackendConfig.DEFAULT_SHOW_PLAYER_HEADS, "show_player_heads"),
                confirmDangerousActions =
                    boolOrDefault(
                        values["confirm_dangerous_actions"],
                        BackendConfig.DEFAULT_CONFIRM_DANGEROUS_ACTIONS,
                        "confirm_dangerous_actions",
                    ),
                consoleHistoryLimit =
                    historyOrDefault(
                        value = values["console_history_limit"]?.toIntOrNull() ?: BackendConfig.DEFAULT_CONSOLE_HISTORY_LIMIT,
                        key = "console_history_limit",
                    ),
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
        """
        max_users: ${config.maxUsers}
        auth_timeout_seconds: ${config.authTimeoutSeconds}
        compact_mode: ${config.compactMode}
        animations_enabled: ${config.animationsEnabled}
        background_opacity: ${"%.2f".format(config.backgroundOpacity)}
        show_player_heads: ${config.showPlayerHeads}
        confirm_dangerous_actions: ${config.confirmDangerousActions}
        console_history_limit: ${config.consoleHistoryLimit}
        """.trimIndent() + "\n"

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

    private fun boolOrDefault(
        raw: String?,
        defaultValue: Boolean,
        key: String,
    ): Boolean {
        raw ?: return defaultValue
        return when (raw.lowercase()) {
            "true" -> true
            "false" -> false
            else -> {
                logger.warn("Invalid Fabric Venus config {}={}; using default {}.", key, raw, defaultValue)
                defaultValue
            }
        }
    }

    private fun opacityOrDefault(
        raw: String?,
        defaultValue: Float,
        key: String,
    ): Float {
        raw ?: return defaultValue
        val parsed = raw.toFloatOrNull()
        if (parsed != null && parsed in 0f..1f) return parsed
        logger.warn("Invalid Fabric Venus config {}={}; using default {}.", key, raw, defaultValue)
        return defaultValue
    }

    private fun historyOrDefault(
        value: Int,
        key: String,
    ): Int {
        if (value in 1..BackendConfig.MAX_CONSOLE_HISTORY_LIMIT) return value
        logger.warn("Invalid Fabric Venus config {}={}; using default {}.", key, value, BackendConfig.DEFAULT_CONSOLE_HISTORY_LIMIT)
        return BackendConfig.DEFAULT_CONSOLE_HISTORY_LIMIT
    }

    private fun defaultConfigContents(): String =
        """
        max_users: ${BackendConfig.DEFAULT_MAX_USERS}
        auth_timeout_seconds: ${BackendConfig.DEFAULT_AUTH_TIMEOUT_SECONDS}
        compact_mode: ${BackendConfig.DEFAULT_COMPACT_MODE}
        animations_enabled: ${BackendConfig.DEFAULT_ANIMATIONS_ENABLED}
        background_opacity: ${"%.2f".format(BackendConfig.DEFAULT_BACKGROUND_OPACITY)}
        show_player_heads: ${BackendConfig.DEFAULT_SHOW_PLAYER_HEADS}
        confirm_dangerous_actions: ${BackendConfig.DEFAULT_CONFIRM_DANGEROUS_ACTIONS}
        console_history_limit: ${BackendConfig.DEFAULT_CONSOLE_HISTORY_LIMIT}
        """.trimIndent() + "\n"

    companion object {
        private const val CONFIG_FILE_NAME = "config.yml"
    }
}
