package dev.ilgax.venus.client.ui.profile

object UiProfileValidator {
    const val MAX_PROFILES = 32
    const val MAX_PAGES = 24
    const val MAX_MODULES = 128
    const val MAX_TEXT_LENGTH = 64

    fun validate(file: UiProfilesFile) {
        require(file.schemaVersion == UiProfilesFile.CURRENT_SCHEMA_VERSION) { "Unsupported UI profile schema" }
        require(file.profiles.size <= MAX_PROFILES) { "Too many UI profiles" }
        require(file.profiles.none { it.id == UiProfilesFile.FACTORY_PROFILE_ID }) { "Factory profile cannot be persisted" }
        require(
            file.profiles
                .map { it.id }
                .distinct()
                .size == file.profiles.size,
        ) { "Duplicate UI profile id" }
        val ids = file.profiles.mapTo(mutableSetOf()) { it.id }.apply { add(UiProfilesFile.FACTORY_PROFILE_ID) }
        require(file.activeProfileId in ids) { "Active UI profile does not exist" }
        require(file.serverAssignments.values.all { it in ids }) { "Server assignment references missing profile" }
        file.profiles.forEach(::validateProfile)
    }

    fun validateProfile(profile: UiProfile) {
        require(profile.id.matches(ID_PATTERN)) { "Invalid UI profile id" }
        require(profile.name.isValidText()) { "Invalid UI profile name" }
        require(profile.pages.size in 1..MAX_PAGES) { "Invalid UI page count" }
        require(profile.modules.size <= MAX_MODULES) { "Too many UI modules" }
        require(
            profile.pages
                .map { it.id }
                .distinct()
                .size == profile.pages.size,
        ) { "Duplicate UI page id" }
        require(
            profile.modules
                .map { it.id }
                .distinct()
                .size == profile.modules.size,
        ) { "Duplicate UI module id" }
        profile.pages.forEach {
            require(it.id.matches(ID_PATTERN)) { "Invalid UI page id" }
            require(it.title.isValidText()) { "Invalid UI page title" }
            require(it.icon.isValidText()) { "Invalid UI page icon" }
        }
        profile.modules.forEach {
            require(it.id.matches(ID_PATTERN)) { "Invalid UI module id" }
            require(it.title == null || it.title.isValidText()) { "Invalid UI module title" }
        }
        UiModuleType.entries.filter { it.singleton }.forEach { type ->
            require(profile.modules.count { it.type == type } <= 1) { "$type may only appear once" }
        }
        validateTheme(profile.theme)
        validateShell(profile.shell)
        validateLayout(profile, profile.normalLayout, UiLayoutMode.NORMAL)
        validateLayout(profile, profile.compactLayout, UiLayoutMode.COMPACT)
    }

    private fun validateTheme(theme: UiTheme) {
        require(theme.spacing in 0..32) { "Theme spacing is out of range" }
        require(theme.cornerRadius in 0..16) { "Theme corner radius is out of range" }
        require(theme.borderWidth in 0..4) { "Theme border width is out of range" }
        require(theme.animationScale in 0f..4f) { "Theme animation scale is out of range" }
    }

    private fun validateShell(shell: UiShell) {
        require(shell.widthPercent in 0.25f..1f) { "Window width is out of range" }
        require(shell.heightPercent in 0.25f..1f) { "Window height is out of range" }
        require(shell.margin in 0..64) { "Window margin is out of range" }
        require(shell.backgroundOpacity in 0f..1f) { "Background opacity is out of range" }
    }

    private fun validateLayout(
        profile: UiProfile,
        layout: UiLayout,
        mode: UiLayoutMode,
    ) {
        val moduleIds = profile.modules.mapTo(mutableSetOf()) { it.id }
        val pageIds = profile.pages.mapTo(mutableSetOf()) { it.id }
        require(layout.placements.size == profile.modules.size) { "$mode layout must place every module" }
        require(layout.placements.map { it.moduleId }.toSet() == moduleIds) { "$mode layout has unknown or missing modules" }
        layout.placements.forEach { placement ->
            require(placement.pageId in pageIds) { "$mode layout references missing page" }
            require(placement.column >= 0 && placement.row >= 0) { "$mode layout position is negative" }
            require(placement.width > 0 && placement.height > 0) { "$mode layout size is invalid" }
            require(placement.column + placement.width <= mode.columns) { "$mode layout exceeds grid width" }
        }
        layout.placements.groupBy { it.pageId }.values.forEach { placements ->
            placements.forEachIndexed { index, placement ->
                require(placements.drop(index + 1).none { placement.overlaps(it) }) { "$mode layout modules overlap" }
            }
        }
    }

    private fun UiPlacement.overlaps(other: UiPlacement): Boolean =
        visible &&
            other.visible &&
            column < other.column + other.width &&
            column + width > other.column &&
            row < other.row + other.height &&
            row + height > other.row

    private fun String.isValidText(): Boolean = isNotBlank() && length <= MAX_TEXT_LENGTH

    private val ID_PATTERN = Regex("[a-zA-Z0-9_-]{1,64}")
}
