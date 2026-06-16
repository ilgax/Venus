package dev.ilgax.venus.backend

data class BackendConfig(
    val maxUsers: Int = DEFAULT_MAX_USERS,
    val authTimeoutSeconds: Int = DEFAULT_AUTH_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_MAX_USERS = 1
        const val DEFAULT_AUTH_TIMEOUT_SECONDS = 60
    }
}
