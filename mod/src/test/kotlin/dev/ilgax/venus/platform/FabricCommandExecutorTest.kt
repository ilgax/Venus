package dev.ilgax.venus.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FabricCommandExecutorTest {
    @Test
    fun `dispatch state reports output as dispatched`() {
        val output = mutableListOf<String>()
        val state = FabricCommandExecutor.DispatchState(output::add)

        state.onOutput("hi")

        assertTrue(state.wasDispatched())
        assertEquals(listOf("hi"), output)
    }

    @Test
    fun `dispatch state reports callback as dispatched`() {
        val state = FabricCommandExecutor.DispatchState {}

        state.onCallback()

        assertTrue(state.wasDispatched())
    }

    @Test
    fun `dispatch state stays false without callback or output`() {
        val state = FabricCommandExecutor.DispatchState {}

        assertFalse(state.wasDispatched())
    }
}
