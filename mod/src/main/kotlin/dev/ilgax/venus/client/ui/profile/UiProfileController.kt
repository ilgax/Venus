package dev.ilgax.venus.client.ui.profile

class UiProfileController(
    private val store: UiProfileStore,
) {
    var loadResult: UiProfileLoadResult = store.load()
        private set

    val file: UiProfilesFile
        get() = loadResult.file

    fun resolve(serverAddress: String?): UiProfile = store.resolve(file, serverAddress)

    fun reload() {
        loadResult = store.load()
    }

    internal fun replace(file: UiProfilesFile) {
        store.save(file)
        loadResult = UiProfileLoadResult(file, UiProfileRecovery.NONE)
    }

    internal fun export(profile: UiProfile): String = store.export(profile)

    internal fun import(code: String): UiProfile = store.import(code)
}
