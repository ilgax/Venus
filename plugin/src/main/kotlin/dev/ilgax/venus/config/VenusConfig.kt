package dev.ilgax.venus.config

import dev.ilgax.venus.backend.BackendConfig
import org.bukkit.plugin.java.JavaPlugin

object VenusConfig {
    private const val DEFAULT_MAX_USERS = BackendConfig.DEFAULT_MAX_USERS
    private const val DEFAULT_AUTH_TIMEOUT = BackendConfig.DEFAULT_AUTH_TIMEOUT_SECONDS

    var maxUsers: Int = DEFAULT_MAX_USERS
        private set
    var authTimeoutSeconds: Int = DEFAULT_AUTH_TIMEOUT
        private set

    fun load(plugin: JavaPlugin) {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        val config = plugin.config

        maxUsers =
            positiveOrDefault(
                value = config.getInt("max_users", DEFAULT_MAX_USERS),
                defaultValue = DEFAULT_MAX_USERS,
                key = "max_users",
                plugin = plugin,
            )
        authTimeoutSeconds =
            positiveOrDefault(
                value = config.getInt("auth_timeout_seconds", DEFAULT_AUTH_TIMEOUT),
                defaultValue = DEFAULT_AUTH_TIMEOUT,
                key = "auth_timeout_seconds",
                plugin = plugin,
            )

        plugin.logger.info(
            "Config loaded — max_users: $maxUsers, auth_timeout: ${authTimeoutSeconds}s",
        )
    }

    private fun positiveOrDefault(
        value: Int,
        defaultValue: Int,
        key: String,
        plugin: JavaPlugin,
    ): Int {
        if (value >= 1) return value
        plugin.logger.warning("Invalid Venus config $key=$value; using default $defaultValue.")
        return defaultValue
    }
}
