package dev.ilgax.venus.backend

import dev.ilgax.venus.protocol.PlayerActionPacket
import dev.ilgax.venus.protocol.PlayerActionResultPacket
import dev.ilgax.venus.protocol.PlayerDetailPacket
import dev.ilgax.venus.protocol.PlayerListPacket
import java.util.UUID

data class BackendPlayer(
    val uuid: UUID,
    val name: String,
)

data class BackendCommandResult(
    val dispatched: Boolean,
    val lines: List<String>,
)

fun interface BackendTask {
    fun cancel()
}

interface BackendScheduler {
    fun runLater(
        delayTicks: Long,
        task: () -> Unit,
    ): BackendTask

    fun runRepeating(
        delayTicks: Long,
        periodTicks: Long,
        task: () -> Unit,
    ): BackendTask
}

interface BackendLogger {
    fun info(message: String)

    fun warning(message: String)
}

interface BackendPlayers {
    fun list(viewer: BackendPlayer): PlayerListPacket

    fun detail(
        viewer: BackendPlayer,
        uuid: UUID,
    ): PlayerDetailPacket?

    fun applyAction(
        viewer: BackendPlayer,
        packet: PlayerActionPacket,
    ): PlayerActionResultPacket
}

interface BackendPlatform {
    val logger: BackendLogger
    val scheduler: BackendScheduler
    val config: BackendConfig

    fun player(uuid: UUID): BackendPlayer?

    fun sendKey(
        player: BackendPlayer,
        data: String,
    )

    fun sendAuth(
        player: BackendPlayer,
        data: String,
    )

    fun sendReady(
        player: BackendPlayer,
        data: String,
    )

    fun sendError(
        player: BackendPlayer,
        data: String,
    )

    fun sendData(
        player: BackendPlayer,
        data: String,
    )

    fun executeCommand(
        player: BackendPlayer,
        command: String,
        output: (String) -> Unit,
    ): Boolean

    fun buildStatsJson(requestedStats: List<String>): String

    fun players(): BackendPlayers
}
