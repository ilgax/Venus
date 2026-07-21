package dev.ilgax.venus.client.ui.profile

import kotlinx.serialization.Serializable

@Serializable
data class UiProfilesFile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val activeProfileId: String = FACTORY_PROFILE_ID,
    val serverAssignments: Map<String, String> = emptyMap(),
    val profiles: List<UiProfile> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val FACTORY_PROFILE_ID = "factory"
    }
}

@Serializable
data class UiProfile(
    val id: String,
    val name: String,
    val theme: UiTheme = UiTheme(),
    val behavior: UiBehavior = UiBehavior(),
    val shell: UiShell = UiShell(),
    val pages: List<UiPageDefinition>,
    val modules: List<UiModuleInstance>,
    val normalLayout: UiLayout,
    val compactLayout: UiLayout,
)

@Serializable
data class UiTheme(
    val background: Int = 0xFF0A0E13.toInt(),
    val window: Int = 0xFF121821.toInt(),
    val topBar: Int = 0xFF0D1219.toInt(),
    val navigation: Int = 0xFF0F141B.toInt(),
    val surface: Int = 0xFF161D27.toInt(),
    val raised: Int = 0xFF1C2430.toInt(),
    val hover: Int = 0xFF243040.toInt(),
    val active: Int = 0xFF2F6F85.toInt(),
    val border: Int = 0xFF2A3340.toInt(),
    val accent: Int = 0xFF2BD9E0.toInt(),
    val text: Int = 0xFFEAF1F8.toInt(),
    val textMuted: Int = 0xFF8A99AC.toInt(),
    val success: Int = 0xFF3DDC84.toInt(),
    val warning: Int = 0xFFFFB454.toInt(),
    val danger: Int = 0xFFFF5C6C.toInt(),
    val spacing: Int = 8,
    val cornerRadius: Int = 0,
    val borderWidth: Int = 1,
    val animationScale: Float = 1f,
)

@Serializable
data class UiBehavior(
    val animationsEnabled: Boolean = true,
    val confirmDangerousActions: Boolean = true,
)

@Serializable
data class UiShell(
    val navigationPlacement: NavigationPlacement = NavigationPlacement.LEFT,
    val anchor: WindowAnchor = WindowAnchor.CENTER,
    val widthPercent: Float = 1f,
    val heightPercent: Float = 1f,
    val margin: Int = 12,
    val backgroundOpacity: Float = 0.75f,
    val showTopBar: Boolean = true,
    val showLogo: Boolean = true,
    val showTitle: Boolean = true,
    val showServerName: Boolean = true,
    val showConnectionState: Boolean = true,
    val showNavigationLabels: Boolean = true,
)

@Serializable
enum class NavigationPlacement {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}

@Serializable
enum class WindowAnchor {
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    LEFT,
    CENTER,
    RIGHT,
    BOTTOM_LEFT,
    BOTTOM,
    BOTTOM_RIGHT,
}

@Serializable
data class UiPageDefinition(
    val id: String,
    val title: String,
    val icon: String,
    val order: Int,
    val visible: Boolean = true,
)

@Serializable
data class UiModuleInstance(
    val id: String,
    val type: UiModuleType,
    val title: String? = null,
    val settings: UiModuleSettings = UiModuleSettings(),
    val style: UiModuleStyle = UiModuleStyle(),
)

@Serializable
enum class UiModuleType(
    val singleton: Boolean,
) {
    SERVER_STATUS(false),
    METRIC_CARD(false),
    STAT_GRAPH(false),
    ONLINE_PLAYERS(false),
    ACTIVE_TRANSFERS(false),
    PLAYERS_WORKFLOW(true),
    FILES_WORKFLOW(true),
    CONSOLE_WORKFLOW(true),
    AUTH_WORKFLOW(true),
    SETTINGS_WORKFLOW(true),
}

@Serializable
data class UiModuleSettings(
    val primaryMetric: String? = null,
    val secondaryMetric: String? = null,
    val showPlayerHeads: Boolean = true,
    val maxRows: Int = 8,
    val consoleHistoryLimit: Int = 500,
)

@Serializable
data class UiModuleStyle(
    val showTitle: Boolean = true,
    val accentOverride: Int? = null,
    val backgroundOverride: Int? = null,
    val borderOverride: Int? = null,
    val padding: Int? = null,
)

@Serializable
data class UiLayout(
    val placements: List<UiPlacement>,
)

@Serializable
data class UiPlacement(
    val moduleId: String,
    val pageId: String,
    val column: Int,
    val row: Int,
    val width: Int,
    val height: Int,
    val visible: Boolean = true,
)

enum class UiLayoutMode(
    val columns: Int,
) {
    NORMAL(12),
    COMPACT(6),
}
