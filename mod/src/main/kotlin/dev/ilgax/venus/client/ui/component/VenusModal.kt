package dev.ilgax.venus.client.ui.component

import dev.ilgax.venus.client.ui.core.Animation
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.ModalKind
import dev.ilgax.venus.client.ui.core.ToastKind
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusModalRequest
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.core.VenusToastRequest
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Modal dialog. Only one modal receives input at a time — the screen routes
 * input to the top of its modal stack. Escape closes when allowed. Outside
 * click behavior is controlled by [VenusModalRequest.dismissOnOutsideClick].
 *
 * Dangerous actions (kind == DANGER) render a red-accented border so the
 * semantic color reinforces the risk.
 */
class VenusModal(
    val bounds: Bounds,
    val request: VenusModalRequest,
) {
    private val anim = Animation(VenusDimensions.ANIM_MODAL_MS)

    init {
        anim.target = 1f
    }

    val accentColor: Int
        get() =
            when (request.kind) {
                ModalKind.INFO -> VenusTheme.ACCENT
                ModalKind.WARN -> VenusTheme.WARNING
                ModalKind.DANGER -> VenusTheme.DANGER
                ModalKind.CONFIRM -> VenusTheme.ACCENT
            }

    fun tick(deltaMs: Float) {
        anim.tick(deltaMs)
    }

    fun render(
        g: GuiGraphics,
        font: Font,
    ) {
        VenusDraw.rect(g, bounds, VenusTheme.WINDOW)
        VenusDraw.border(g, bounds, accentColor)
        VenusDraw.rect(g, bounds.x, bounds.y, bounds.width, 2, accentColor)

        val pad = VenusDimensions.MODAL_PADDING
        val titleColor =
            when (request.kind) {
                ModalKind.DANGER -> VenusTheme.DANGER
                ModalKind.WARN -> VenusTheme.WARNING
                else -> VenusTheme.TEXT
            }
        VenusDraw.text(g, font, request.title, bounds.x + pad, bounds.y + pad, titleColor, false)
        VenusDraw.hSeparator(g, bounds.x + pad, bounds.y + pad + font.lineHeight + 2, bounds.width - pad * 2, VenusTheme.BORDER)

        val msgY = bounds.y + pad + font.lineHeight + 10
        val msgBounds = Bounds(bounds.x + pad, msgY, bounds.width - pad * 2, bounds.height - msgY - pad - VenusDimensions.BUTTON_HEIGHT - 8)
        renderWrapped(g, font, request.message, msgBounds)

        val btnY = bounds.bottom - pad - VenusDimensions.BUTTON_HEIGHT
        val btnH = VenusDimensions.BUTTON_HEIGHT
        val cancelW = font.width(request.cancelLabel) + 16
        val confirmW = font.width(request.confirmLabel) + 16
        val gap = 8
        val totalW = cancelW + confirmW + gap
        var bx = bounds.centerX + (totalW / 2 - confirmW)

        val confirmBounds = Bounds(bx, btnY, confirmW, btnH)
        val confirmColor = if (request.kind == ModalKind.DANGER) VenusTheme.DANGER else VenusTheme.ACCENT
        VenusDraw.rect(g, confirmBounds, VenusDraw.blendColor(VenusTheme.RAISED, confirmColor, 0.3f))
        VenusDraw.border(g, confirmBounds, confirmColor)
        VenusDraw.textCentered(g, font, request.confirmLabel, confirmBounds, VenusTheme.TEXT, false)

        bx -= cancelW + gap
        val cancelBounds = Bounds(bx, btnY, cancelW, btnH)
        VenusDraw.rect(g, cancelBounds, VenusTheme.RAISED)
        VenusDraw.border(g, cancelBounds, VenusTheme.BORDER_BRIGHT)
        VenusDraw.textCentered(g, font, request.cancelLabel, cancelBounds, VenusTheme.TEXT_MUTED, false)
    }

    fun confirmBounds(font: Font): Bounds {
        val pad = VenusDimensions.MODAL_PADDING
        val btnY = bounds.bottom - pad - VenusDimensions.BUTTON_HEIGHT
        val cancelW = font.width(request.cancelLabel) + 16
        val confirmW = font.width(request.confirmLabel) + 16
        val gap = 8
        val totalW = cancelW + confirmW + gap
        val bx = bounds.centerX + (totalW / 2 - confirmW)
        return Bounds(bx, btnY, confirmW, VenusDimensions.BUTTON_HEIGHT)
    }

    fun cancelBounds(font: Font): Bounds {
        val pad = VenusDimensions.MODAL_PADDING
        val btnY = bounds.bottom - pad - VenusDimensions.BUTTON_HEIGHT
        val cancelW = font.width(request.cancelLabel) + 16
        val confirmW = font.width(request.confirmLabel) + 16
        val gap = 8
        val totalW = cancelW + confirmW + gap
        val bx = bounds.centerX + (totalW / 2 - confirmW) - cancelW - gap
        return Bounds(bx, btnY, cancelW, VenusDimensions.BUTTON_HEIGHT)
    }

    private fun renderWrapped(
        g: GuiGraphics,
        font: Font,
        text: String,
        bounds: Bounds,
    ) {
        val lines =
            dev.ilgax.venus.client.ui.render.TextRenderUtil
                .split(font, text, bounds.width)
        lines.forEachIndexed { i, line ->
            if (i * font.lineHeight >= bounds.height) return
            VenusDraw.text(g, font, line, bounds.x, bounds.y + i * font.lineHeight, VenusTheme.TEXT_MUTED, false)
        }
    }
}

/**
 * Toast notification. Stacked bottom-right, bounded lifetime, short entrance
 * animation. Pure expiry logic in [ToastLifecycle] for unit testing.
 */
class VenusToast(
    val bounds: Bounds,
    val request: VenusToastRequest,
) {
    private val anim = Animation(VenusDimensions.ANIM_TOAST_MS)

    init {
        anim.target = 1f
    }

    val accentColor: Int
        get() =
            when (request.kind) {
                ToastKind.INFO -> VenusTheme.ACCENT
                ToastKind.SUCCESS -> VenusTheme.SUCCESS
                ToastKind.WARN -> VenusTheme.WARNING
                ToastKind.DANGER -> VenusTheme.DANGER
            }

    fun tick(deltaMs: Float) {
        anim.tick(deltaMs)
    }

    fun render(
        g: GuiGraphics,
        font: Font,
    ) {
        VenusDraw.rect(g, bounds, VenusTheme.WINDOW)
        VenusDraw.border(g, bounds, accentColor)
        VenusDraw.rect(g, bounds.x, bounds.y, 2, bounds.height, accentColor)
        VenusDraw.statusDot(g, bounds.x + 6, bounds.y + 6, accentColor, size = 4)
        VenusDraw.text(g, font, request.title, bounds.x + 14, bounds.y + 4, VenusTheme.TEXT, false)
        val msgBounds = Bounds(bounds.x + 14, bounds.y + 4 + font.lineHeight + 2, bounds.width - 18, bounds.height - font.lineHeight - 10)
        val lines =
            dev.ilgax.venus.client.ui.render.TextRenderUtil
                .split(font, request.message, msgBounds.width)
        lines.take(2).forEachIndexed { i, line ->
            VenusDraw.text(g, font, line, msgBounds.x, msgBounds.y + i * font.lineHeight, VenusTheme.TEXT_MUTED, false)
        }
    }
}

/**
 * Pure toast lifecycle logic — unit-testable.
 */
object ToastLifecycle {
    fun isExpired(
        toast: VenusToastRequest,
        nowMs: Long,
    ): Boolean = nowMs >= toast.expireAtMs

    fun expiryMs(
        createdAtMs: Long,
        durationMs: Long,
    ): Long = createdAtMs + durationMs

    fun sortStack(toasts: List<VenusToastRequest>): List<VenusToastRequest> = toasts.sortedByDescending { it.createdAtMs }
}
