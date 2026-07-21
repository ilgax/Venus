package dev.ilgax.venus.client.ui.profile

import java.util.UUID

class UiProfileController(
    private val store: UiProfileStore,
) {
    var loadResult: UiProfileLoadResult = store.load()
        private set

    val file: UiProfilesFile
        get() = loadResult.file

    fun resolve(serverAddress: String?): UiProfile = store.resolve(file, serverAddress)

    fun allProfiles(): List<UiProfile> = listOf(FactoryUiProfile.profile) + file.profiles

    fun reload() {
        loadResult = store.load()
    }

    internal fun replace(file: UiProfilesFile) {
        store.save(file)
        loadResult = UiProfileLoadResult(file, UiProfileRecovery.NONE)
    }

    internal fun export(profile: UiProfile): String = store.export(profile)

    internal fun import(code: String): UiProfile = store.import(code)

    fun editableCopy(profile: UiProfile): UiProfile {
        val id = uniqueId()
        val baseName = if (profile.id == UiProfilesFile.FACTORY_PROFILE_ID) "My Profile" else "${profile.name} Copy"
        return profile.copy(id = id, name = uniqueName(baseName))
    }

    fun saveProfile(
        profile: UiProfile,
        activate: Boolean = true,
    ) {
        require(profile.id != UiProfilesFile.FACTORY_PROFILE_ID) { "Factory Default is immutable" }
        UiProfileValidator.validateProfile(profile)
        val profiles = file.profiles.filterNot { it.id == profile.id } + profile
        replace(file.copy(activeProfileId = if (activate) profile.id else file.activeProfileId, profiles = profiles))
    }

    fun activate(profileId: String) {
        require(profileId == UiProfilesFile.FACTORY_PROFILE_ID || file.profiles.any { it.id == profileId }) { "Unknown UI profile" }
        replace(file.copy(activeProfileId = profileId))
    }

    fun assignCurrentServer(
        address: String,
        profileId: String?,
    ) {
        val key = UiProfileStore.canonicalServerAddress(address)
        val assignments = file.serverAssignments.toMutableMap()
        if (profileId == null) {
            assignments.remove(key)
        } else {
            require(profileId == UiProfilesFile.FACTORY_PROFILE_ID || file.profiles.any { it.id == profileId }) { "Unknown UI profile" }
            assignments[key] = profileId
        }
        replace(file.copy(serverAssignments = assignments))
    }

    fun delete(profileId: String) {
        require(profileId != UiProfilesFile.FACTORY_PROFILE_ID) { "Factory Default cannot be deleted" }
        val profiles = file.profiles.filterNot { it.id == profileId }
        require(profiles.size != file.profiles.size) { "Unknown UI profile" }
        val assignments = file.serverAssignments.filterValues { it != profileId }
        val active = if (file.activeProfileId == profileId) UiProfilesFile.FACTORY_PROFILE_ID else file.activeProfileId
        replace(file.copy(activeProfileId = active, serverAssignments = assignments, profiles = profiles))
    }

    fun importAsCopy(code: String): UiProfile {
        val imported = import(code)
        val copy = imported.copy(id = uniqueId(), name = uniqueName(imported.name))
        saveProfile(copy)
        return copy
    }

    private fun uniqueId(): String {
        var candidate: String
        do {
            candidate = "profile-${UUID.randomUUID().toString().take(8)}"
        } while (file.profiles.any { it.id == candidate })
        return candidate
    }

    private fun uniqueName(base: String): String {
        val names = file.profiles.mapTo(mutableSetOf()) { it.name }
        if (base !in names) return base
        var suffix = 2
        while ("$base $suffix" in names) suffix++
        return "$base $suffix"
    }
}
