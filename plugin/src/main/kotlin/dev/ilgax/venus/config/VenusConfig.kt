package dev.ilgax.venus.config

import org.bukkit.plugin.java.JavaPlugin

object VenusConfig {
    private const val DEFAULT_MAX_USERS = 1
    private const val DEFAULT_AUTH_TIMEOUT = 60

    var maxUsers: Int = DEFAULT_MAX_USERS
        private set
    var authTimeoutSeconds: Int = DEFAULT_AUTH_TIMEOUT
        private set

    fun load(plugin: JavaPlugin) {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        val config = plugin.config

        maxUsers = config.getInt("max_users", DEFAULT_MAX_USERS)
        authTimeoutSeconds = config.getInt("auth_timeout_seconds", DEFAULT_AUTH_TIMEOUT)

        plugin.logger.info(
            "Config loaded — max_users: $maxUsers, auth_timeout: ${authTimeoutSeconds}s",
        )
    }
}
