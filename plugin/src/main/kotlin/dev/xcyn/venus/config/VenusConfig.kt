package dev.xcyn.venus.config

import org.bukkit.plugin.java.JavaPlugin

object VenusConfig {

    private const val DEFAULT_MAX_USERS = 1
    private const val DEFAULT_SESSION_TIMEOUT = 60
    private const val DEFAULT_CACHE_VERIFIED_UUID = true

    var maxUsers: Int = DEFAULT_MAX_USERS
        private set
    var sessionTimeoutSeconds: Int = DEFAULT_SESSION_TIMEOUT
        private set
    var cacheVerifiedUuid: Boolean = DEFAULT_CACHE_VERIFIED_UUID
        private set

    fun load(plugin: JavaPlugin) {
        plugin.reloadConfig()
        plugin.saveDefaultConfig()
        val config = plugin.config

        maxUsers = config.getInt("max_users", DEFAULT_MAX_USERS)
        sessionTimeoutSeconds = config.getInt("session_timeout_seconds", DEFAULT_SESSION_TIMEOUT)
        cacheVerifiedUuid = config.getBoolean("cache_verified_uuid", DEFAULT_CACHE_VERIFIED_UUID)

        plugin.logger.info("Config loaded — max_users: $maxUsers, session_timeout: ${sessionTimeoutSeconds}s, cache_uuid: $cacheVerifiedUuid")
    }
}