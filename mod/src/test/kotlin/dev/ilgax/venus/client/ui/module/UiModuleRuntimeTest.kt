package dev.ilgax.venus.client.ui.module

import dev.ilgax.venus.client.ui.profile.UiModuleInstance
import dev.ilgax.venus.client.ui.profile.UiModuleType
import org.junit.Test
import kotlin.test.assertEquals

class UiModuleRuntimeTest {
    @Test
    fun `duplicate display modules acquire each data requirement once`() {
        var stats = 0
        var players = 0
        val runtime =
            UiModuleRuntime(
                UiDataActions(
                    subscribeStats = { stats++ },
                    requestPlayers = { players++ },
                ),
            )
        val modules =
            listOf(
                UiModuleInstance("one", UiModuleType.METRIC_CARD),
                UiModuleInstance("two", UiModuleType.METRIC_CARD),
                UiModuleInstance("players", UiModuleType.ONLINE_PLAYERS),
            )

        runtime.activate(modules)
        runtime.activate(modules)

        assertEquals(1, stats)
        assertEquals(1, players)
    }

    @Test
    fun `requirements reacquire after runtime clear`() {
        var stats = 0
        val runtime = UiModuleRuntime(UiDataActions(subscribeStats = { stats++ }))
        val graph = listOf(UiModuleInstance("graph", UiModuleType.STAT_GRAPH))

        runtime.activate(graph)
        runtime.clear()
        runtime.activate(graph)

        assertEquals(2, stats)
    }
}
