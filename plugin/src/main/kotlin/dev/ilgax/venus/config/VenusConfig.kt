package dev.ilgax.venus.config

import dev.ilgax.venus.backend.BackendConfig
import dev.ilgax.venus.backend.BackendFileConfig
import dev.ilgax.venus.backend.BackendFileRoot
import dev.ilgax.venus.backend.BackendFileRootMode
import org.bukkit.plugin.java.JavaPlugin

object VenusConfig {
    private const val DEFAULT_MAX_USERS = BackendConfig.DEFAULT_MAX_USERS
    private const val DEFAULT_AUTH_TIMEOUT = BackendConfig.DEFAULT_AUTH_TIMEOUT_SECONDS

    var maxUsers: Int = DEFAULT_MAX_USERS
        private set
    var authTimeoutSeconds: Int = DEFAULT_AUTH_TIMEOUT
        private set
    var files: BackendFileConfig = BackendFileConfig()
        private set

    fun load(plugin: JavaPlugin) {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        val config = plugin.config

        maxUsers =
            boundedOrDefault(
                value = config.getInt("max_users", DEFAULT_MAX_USERS),
                defaultValue = DEFAULT_MAX_USERS,
                maxValue = BackendConfig.MAX_USERS_LIMIT,
                key = "max_users",
                plugin = plugin,
            )
        authTimeoutSeconds =
            boundedOrDefault(
                value = config.getInt("auth_timeout_seconds", DEFAULT_AUTH_TIMEOUT),
                defaultValue = DEFAULT_AUTH_TIMEOUT,
                maxValue = BackendConfig.MAX_AUTH_TIMEOUT_LIMIT,
                key = "auth_timeout_seconds",
                plugin = plugin,
            )
        files = loadFiles(config, plugin)

        plugin.logger.info(
            "Config loaded — max_users: $maxUsers, auth_timeout: ${authTimeoutSeconds}s",
        )
    }

    private fun loadFiles(
        config: org.bukkit.configuration.file.FileConfiguration,
        plugin: JavaPlugin,
    ): BackendFileConfig {
        val section = config.getConfigurationSection("files")
        val rootsSection = section?.getConfigurationSection("roots")
        val roots =
            rootsSection
                ?.getKeys(false)
                ?.mapNotNull { id ->
                    val root = rootsSection.getConfigurationSection(id) ?: return@mapNotNull null
                    val path = root.getString("path")?.trim().orEmpty()
                    val label = root.getString("label", id)?.trim().orEmpty()
                    val mode =
                        when (root.getString("mode", "read_only")?.lowercase()) {
                            "read_write" -> BackendFileRootMode.READ_WRITE
                            "read_only" -> BackendFileRootMode.READ_ONLY
                            else -> {
                                plugin.logger.warning("Invalid Venus file root mode for $id; skipping root.")
                                return@mapNotNull null
                            }
                        }
                    try {
                        BackendFileRoot(id, label, path, mode)
                    } catch (e: IllegalArgumentException) {
                        plugin.logger.warning("Invalid Venus file root $id: ${e.message}; skipping root.")
                        null
                    }
                }.orEmpty()
        return BackendFileConfig(
            roots = roots,
            reservedFreeBytes =
                section
                    ?.getLong("reserved_free_bytes", BackendFileConfig.DEFAULT_RESERVED_FREE_BYTES)
                    ?.coerceAtLeast(0) ?: BackendFileConfig.DEFAULT_RESERVED_FREE_BYTES,
            maxConcurrentTransfers =
                section
                    ?.getInt("max_concurrent_transfers", BackendFileConfig.DEFAULT_MAX_CONCURRENT_TRANSFERS)
                    ?.coerceIn(1, 8) ?: BackendFileConfig.DEFAULT_MAX_CONCURRENT_TRANSFERS,
            idleTimeoutSeconds =
                section
                    ?.getInt("idle_timeout_seconds", BackendFileConfig.DEFAULT_IDLE_TIMEOUT_SECONDS)
                    ?.coerceIn(5, 600) ?: BackendFileConfig.DEFAULT_IDLE_TIMEOUT_SECONDS,
        )
    }

    private fun boundedOrDefault(
        value: Int,
        defaultValue: Int,
        maxValue: Int,
        key: String,
        plugin: JavaPlugin,
    ): Int {
        if (value in 1..maxValue) return value
        plugin.logger.warning("Invalid Venus config $key=$value; using default $defaultValue.")
        return defaultValue
    }
}
