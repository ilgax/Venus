package dev.ilgax.venus.platform

import net.minecraft.commands.CommandResultCallback
import net.minecraft.commands.CommandSource
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer

internal object FabricCommandExecutor {
    fun execute(
        server: MinecraftServer,
        command: String,
        output: (String) -> Unit,
    ): Boolean {
        val dispatchState = DispatchState(output)
        val source =
            server
                .createCommandSourceStack()
                .withSource(
                    CapturingCommandSource { line ->
                        dispatchState.onOutput(line)
                    },
                ).withCallback(
                    CommandResultCallback { _, _ ->
                        dispatchState.onCallback()
                    },
                )
        server.commands.performPrefixedCommand(source, command)
        return dispatchState.wasDispatched()
    }

    internal class DispatchState(
        private val output: (String) -> Unit,
    ) {
        private var callbackSeen = false
        private var emittedOutput = false

        fun onOutput(line: String) {
            emittedOutput = true
            output(line)
        }

        fun onCallback() {
            callbackSeen = true
        }

        fun wasDispatched(): Boolean = callbackSeen || emittedOutput
    }

    private class CapturingCommandSource(
        private val output: (String) -> Unit,
    ) : CommandSource {
        override fun sendSystemMessage(component: Component) {
            output(component.string)
        }

        override fun acceptsSuccess(): Boolean = true

        override fun acceptsFailure(): Boolean = true

        override fun shouldInformAdmins(): Boolean = false
    }
}
