package dev.ilgax.venus.backend

data class BackendConfig(
    val maxUsers: Int = DEFAULT_MAX_USERS,
    val authTimeoutSeconds: Int = DEFAULT_AUTH_TIMEOUT_SECONDS,
    val files: BackendFileConfig = BackendFileConfig(),
) {
    init {
        require(maxUsers in 1..MAX_USERS_LIMIT) { "maxUsers must be in 1..$MAX_USERS_LIMIT" }
        require(authTimeoutSeconds in 1..MAX_AUTH_TIMEOUT_LIMIT) { "authTimeoutSeconds must be in 1..$MAX_AUTH_TIMEOUT_LIMIT" }
    }

    companion object {
        const val DEFAULT_MAX_USERS = 1
        const val DEFAULT_AUTH_TIMEOUT_SECONDS = 60
        const val MAX_USERS_LIMIT = 100
        const val MAX_AUTH_TIMEOUT_LIMIT = 600
    }
}

enum class BackendFileRootMode {
    READ_ONLY,
    READ_WRITE,
}

data class BackendFileRoot(
    val id: String,
    val label: String,
    val path: String,
    val mode: BackendFileRootMode,
) {
    init {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "file root id is invalid" }
        require(label.isNotBlank() && label.length <= 64) { "file root label is invalid" }
        require(path.isNotBlank()) { "file root path must not be blank" }
    }
}

data class BackendFileConfig(
    val roots: List<BackendFileRoot> = emptyList(),
    val reservedFreeBytes: Long = DEFAULT_RESERVED_FREE_BYTES,
    val maxConcurrentTransfers: Int = DEFAULT_MAX_CONCURRENT_TRANSFERS,
    val idleTimeoutSeconds: Int = DEFAULT_IDLE_TIMEOUT_SECONDS,
) {
    init {
        require(roots.map { it.id }.distinct().size == roots.size) { "file root ids must be unique" }
        require(reservedFreeBytes >= 0) { "reservedFreeBytes must be non-negative" }
        require(maxConcurrentTransfers in 1..8) { "maxConcurrentTransfers must be in 1..8" }
        require(idleTimeoutSeconds in 5..600) { "idleTimeoutSeconds must be in 5..600" }
    }

    companion object {
        const val DEFAULT_RESERVED_FREE_BYTES = 1_073_741_824L
        const val DEFAULT_MAX_CONCURRENT_TRANSFERS = 2
        const val DEFAULT_IDLE_TIMEOUT_SECONDS = 30
    }
}
