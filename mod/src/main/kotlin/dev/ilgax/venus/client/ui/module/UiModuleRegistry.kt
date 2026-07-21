package dev.ilgax.venus.client.ui.module

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.profile.UiLayoutMode
import dev.ilgax.venus.client.ui.profile.UiModuleInstance
import dev.ilgax.venus.client.ui.profile.UiModuleType
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget

interface UiModule {
    val instance: UiModuleInstance

    fun layout(bounds: Bounds)

    fun render(
        graphics: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    )

    fun onActivate() {}

    fun onDeactivate() {}

    fun children(): List<AbstractWidget> = emptyList()

    fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean = false

    fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean = false
}

data class UiModuleDescriptor(
    val type: UiModuleType,
    val displayName: String,
    val minimumNormal: GridSize,
    val minimumCompact: GridSize,
    val requirements: Set<UiDataRequirement> = emptySet(),
) {
    fun minimum(mode: UiLayoutMode): GridSize = if (mode == UiLayoutMode.NORMAL) minimumNormal else minimumCompact
}

data class GridSize(
    val width: Int,
    val height: Int,
)

enum class UiDataRequirement {
    STATS,
    PLAYERS,
    LOGS,
    FILES,
}

class UiModuleRegistry private constructor(
    private val descriptors: Map<UiModuleType, UiModuleDescriptor>,
) {
    fun descriptor(type: UiModuleType): UiModuleDescriptor = descriptors.getValue(type)

    fun all(): List<UiModuleDescriptor> = UiModuleType.entries.map(::descriptor)

    companion object {
        val builtIn: UiModuleRegistry =
            UiModuleRegistry(
                listOf(
                    descriptor(UiModuleType.SERVER_STATUS, "Server Status", 4, 1, 3, 1),
                    descriptor(UiModuleType.METRIC_CARD, "Metric Card", 2, 2, 3, 2, UiDataRequirement.STATS),
                    descriptor(UiModuleType.STAT_GRAPH, "Stat Graph", 4, 3, 6, 3, UiDataRequirement.STATS),
                    descriptor(UiModuleType.ONLINE_PLAYERS, "Online Players", 3, 3, 6, 3, UiDataRequirement.PLAYERS),
                    descriptor(UiModuleType.ACTIVE_TRANSFERS, "Active Transfers", 3, 2, 6, 2),
                    descriptor(UiModuleType.PLAYERS_WORKFLOW, "Players", 6, 6, 6, 8),
                    descriptor(UiModuleType.FILES_WORKFLOW, "Files", 8, 8, 6, 10),
                    descriptor(UiModuleType.CONSOLE_WORKFLOW, "Console", 8, 8, 6, 10),
                    descriptor(UiModuleType.AUTH_WORKFLOW, "Access", 6, 6, 6, 8),
                    descriptor(UiModuleType.SETTINGS_WORKFLOW, "Settings", 6, 6, 6, 8),
                ).associateBy(UiModuleDescriptor::type),
            )

        private fun descriptor(
            type: UiModuleType,
            name: String,
            normalWidth: Int,
            normalHeight: Int,
            compactWidth: Int,
            compactHeight: Int,
            vararg requirements: UiDataRequirement,
        ) = UiModuleDescriptor(
            type,
            name,
            GridSize(normalWidth, normalHeight),
            GridSize(compactWidth, compactHeight),
            requirements.toSet(),
        )
    }
}

class UiModuleRuntime(
    private val actions: UiDataActions,
    private val registry: UiModuleRegistry = UiModuleRegistry.builtIn,
) {
    private var activeRequirements: Set<UiDataRequirement> = emptySet()

    fun activate(instances: Collection<UiModuleInstance>) {
        val next = instances.flatMapTo(mutableSetOf()) { registry.descriptor(it.type).requirements }
        (next - activeRequirements).forEach {
            when (it) {
                UiDataRequirement.STATS -> actions.subscribeStats()
                UiDataRequirement.PLAYERS -> actions.requestPlayers()
                UiDataRequirement.LOGS -> actions.subscribeLogs()
                UiDataRequirement.FILES -> actions.requestFiles()
            }
        }
        activeRequirements = next
    }

    fun clear() {
        activeRequirements = emptySet()
    }
}

data class UiDataActions(
    val subscribeStats: () -> Unit = {},
    val requestPlayers: () -> Unit = {},
    val subscribeLogs: () -> Unit = {},
    val requestFiles: () -> Unit = {},
)
