package dev.ilgax.venus.client.ui.profile

object FactoryUiProfile {
    val profile: UiProfile by lazy(::create)

    private fun create(): UiProfile {
        val pages =
            listOf(
                page("dashboard", "Dashboard", "dashboard", 0),
                page("players", "Players", "players", 1),
                page("files", "Files", "files", 2),
                page("console", "Console", "console", 3),
                page("access", "Access", "access", 4),
                page("settings", "Settings", "settings", 5),
            )
        val modules =
            listOf(
                module("status", UiModuleType.SERVER_STATUS, "Server"),
                metric("tps", "TPS / MSPT", "tps", "mspt"),
                metric("memory", "Memory", "ram_used", "ram_max"),
                metric("players_metric", "Players", "online_players", "max_players"),
                metric("uptime", "Uptime", "uptime"),
                graph("performance", "Performance", "tps", "mspt"),
                graph("memory_graph", "Memory History", "ram_used", "ram_max"),
                module("online", UiModuleType.ONLINE_PLAYERS, "Online Players"),
                module("transfers", UiModuleType.ACTIVE_TRANSFERS, "Active Transfers"),
                module("players_workflow", UiModuleType.PLAYERS_WORKFLOW, "Players"),
                module("files_workflow", UiModuleType.FILES_WORKFLOW, "Files"),
                module("console_workflow", UiModuleType.CONSOLE_WORKFLOW, "Console"),
                module("auth_workflow", UiModuleType.AUTH_WORKFLOW, "Access"),
                module("settings_workflow", UiModuleType.SETTINGS_WORKFLOW, "Settings"),
            )
        return UiProfile(
            id = UiProfilesFile.FACTORY_PROFILE_ID,
            name = "Factory Default",
            pages = pages,
            modules = modules,
            normalLayout = UiLayout(normalPlacements()),
            compactLayout = UiLayout(compactPlacements()),
        )
    }

    private fun normalPlacements(): List<UiPlacement> =
        listOf(
            place("status", "dashboard", 0, 0, 12, 1),
            place("tps", "dashboard", 0, 1, 3, 2),
            place("memory", "dashboard", 3, 1, 3, 2),
            place("players_metric", "dashboard", 6, 1, 3, 2),
            place("uptime", "dashboard", 9, 1, 3, 2),
            place("performance", "dashboard", 0, 3, 8, 5),
            place("online", "dashboard", 8, 3, 4, 5),
            place("memory_graph", "dashboard", 0, 8, 8, 4),
            place("transfers", "dashboard", 8, 8, 4, 4),
            place("players_workflow", "players", 0, 0, 12, 12),
            place("files_workflow", "files", 0, 0, 12, 12),
            place("console_workflow", "console", 0, 0, 12, 12),
            place("auth_workflow", "access", 0, 0, 12, 12),
            place("settings_workflow", "settings", 0, 0, 12, 12),
        )

    private fun compactPlacements(): List<UiPlacement> =
        listOf(
            place("status", "dashboard", 0, 0, 6, 1),
            place("tps", "dashboard", 0, 1, 3, 2),
            place("memory", "dashboard", 3, 1, 3, 2),
            place("players_metric", "dashboard", 0, 3, 3, 2),
            place("uptime", "dashboard", 3, 3, 3, 2),
            place("performance", "dashboard", 0, 5, 6, 5),
            place("memory_graph", "dashboard", 0, 10, 6, 4),
            place("online", "dashboard", 0, 14, 6, 5),
            place("transfers", "dashboard", 0, 19, 6, 4),
            place("players_workflow", "players", 0, 0, 6, 16),
            place("files_workflow", "files", 0, 0, 6, 16),
            place("console_workflow", "console", 0, 0, 6, 16),
            place("auth_workflow", "access", 0, 0, 6, 16),
            place("settings_workflow", "settings", 0, 0, 6, 16),
        )

    private fun page(
        id: String,
        title: String,
        icon: String,
        order: Int,
    ) = UiPageDefinition(id, title, icon, order)

    private fun module(
        id: String,
        type: UiModuleType,
        title: String,
    ) = UiModuleInstance(id, type, title)

    private fun metric(
        id: String,
        title: String,
        primary: String,
        secondary: String? = null,
    ) = UiModuleInstance(id, UiModuleType.METRIC_CARD, title, UiModuleSettings(primary, secondary))

    private fun graph(
        id: String,
        title: String,
        primary: String,
        secondary: String? = null,
    ) = UiModuleInstance(id, UiModuleType.STAT_GRAPH, title, UiModuleSettings(primary, secondary))

    private fun place(
        module: String,
        page: String,
        column: Int,
        row: Int,
        width: Int,
        height: Int,
    ) = UiPlacement(module, page, column, row, width, height)
}
