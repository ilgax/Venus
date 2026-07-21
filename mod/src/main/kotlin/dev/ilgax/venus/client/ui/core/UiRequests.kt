package dev.ilgax.venus.client.ui.core

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
