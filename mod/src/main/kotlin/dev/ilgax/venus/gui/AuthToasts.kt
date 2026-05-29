package dev.ilgax.venus.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component

object AuthToasts {
    private val AUTH_SUCCESS = SystemToast.SystemToastId(5_000L)
    private val AUTH_FAILURE = SystemToast.SystemToastId(7_000L)

    fun success() {
        show(AUTH_SUCCESS, "Venus auth succeeded", "Session ready.")
    }

    fun failure(reason: String) {
        show(AUTH_FAILURE, "Venus auth failed", reason)
    }

    private fun show(
        id: SystemToast.SystemToastId,
        title: String,
        message: String,
    ) {
        val minecraft = Minecraft.getInstance()
        minecraft.execute {
            SystemToast.addOrUpdate(
                minecraft.toastManager,
                id,
                Component.literal(title),
                Component.literal(message),
            )
        }
    }
}
