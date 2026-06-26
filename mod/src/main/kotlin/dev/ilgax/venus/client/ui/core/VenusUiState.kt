package dev.ilgax.venus.client.ui.core

/**
 * Top-level UI state container held by [dev.ilgax.venus.client.ui.VenusScreen].
 *
 * This is NOT a second global mutable store — it only holds ephemeral UI
 * session state (active page, modal stack, toasts, animation flags). All
 * server-provided data continues to flow through the existing
 * [dev.ilgax.venus.state.SessionState] object.
 *
 * The screen owns one [VenusUiState] instance and mutates it directly on the
 * render/input thread. Pages receive callbacks, never this object.
 */
class VenusUiState {
    var activePage: VenusPage = VenusPage.DASHBOARD
        private set
    var previousPage: VenusPage? = null
        private set

    val modalStack = ArrayDeque<VenusModalRequest>()
    val toasts = ArrayDeque<VenusToastRequest>()

    var animationsEnabled: Boolean = true
    var compactMode: Boolean = false
    var backgroundOpacity: Float = 0.75f
    var showPlayerHeads: Boolean = true
    var confirmDangerousActions: Boolean = true
    var consoleHistoryLimit: Int = 500

    fun navigateTo(page: VenusPage) {
        if (page == activePage) return
        previousPage = activePage
        activePage = page
    }

    fun pushModal(request: VenusModalRequest) {
        modalStack.addLast(request)
    }

    fun popModal(): VenusModalRequest? = modalStack.removeLastOrNull()

    fun clearModals() {
        modalStack.clear()
    }

    val currentModal: VenusModalRequest?
        get() = modalStack.lastOrNull()

    fun addToast(request: VenusToastRequest) {
        if (toasts.size >= MAX_TOASTS) toasts.removeFirst()
        toasts.addLast(request)
    }

    fun removeToast(id: Long) {
        toasts.removeAll { it.id == id }
    }

    fun tickToasts(nowMs: Long) {
        toasts.removeAll { nowMs >= it.expireAtMs }
    }

    private companion object {
        const val MAX_TOASTS = 4
    }
}

enum class VenusPage(
    val id: String,
) {
    DASHBOARD("dashboard"),
    PLAYERS("players"),
    CONSOLE("console"),
    AUTH("auth"),
    SETTINGS("settings"),
}

enum class ModalKind {
    INFO,
    WARN,
    DANGER,
    CONFIRM,
}

data class VenusModalRequest(
    val kind: ModalKind,
    val title: String,
    val message: String,
    val confirmLabel: String = "Confirm",
    val cancelLabel: String = "Cancel",
    val onConfirm: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val dismissOnOutsideClick: Boolean = true,
    val dismissOnEscape: Boolean = true,
)

enum class ToastKind {
    INFO,
    SUCCESS,
    WARN,
    DANGER,
}

data class VenusToastRequest(
    val id: Long,
    val kind: ToastKind,
    val title: String,
    val message: String,
    val createdAtMs: Long,
    val expireAtMs: Long,
)
