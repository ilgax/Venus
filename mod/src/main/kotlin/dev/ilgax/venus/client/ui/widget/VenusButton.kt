package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.core.Animation
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Standard Venus button. Sharp corners, thin border, no pill shape.
 *
 * States: normal, hovered, pressed, disabled, active, danger, success.
 * Keyboard: Enter/Space activate when focused. Focus outline drawn.
 * Narration: via [AbstractWidget.defaultButtonNarrationText].
 * Optional leading icon drawn as a small filled square placeholder until a
 * texture asset is wired (see [leadingIcon]).
 */
class VenusButton(
    x: Int = 0,
    y: Int = 0,
    width: Int,
    height: Int = VenusDimensions.BUTTON_HEIGHT,
    text: String,
    private val onPressed: () -> Unit,
) : VenusWidget(x, y, width, height, Component.literal(text)) {
    enum class Variant {
        NORMAL,
        ACTIVE,
        DANGER,
        SUCCESS,
    }

    var variant: Variant = Variant.NORMAL
    var enabled: Boolean = true
        set(value) {
            field = value
            active = value
        }
    var leadingIcon: String? = null
    var disabledReason: String? = null

    private val hover = Animation(VenusDimensions.ANIM_HOVER_MS)
    private var pressed = false

    fun text(value: String) {
        message = Component.literal(value)
    }

    override fun drawVenus(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        hover.target = if (isHovered && enabled) 1f else 0f
        hover.tickFrame()

        val base = baseColor()
        val hoverColor = VenusTheme.HOVER
        val bg =
            if (pressed && enabled) {
                VenusDraw.blendColor(base, hoverColor, 1f)
            } else {
                VenusDraw.blendColor(base, hoverColor, hover.value)
            }

        VenusDraw.rect(g, bounds, bg)
        VenusDraw.border(g, bounds, borderColor())

        if (isFocused && enabled) {
            VenusDraw.focusOutline(g, bounds, focusColor())
        }

        val label = message.string
        val font =
            net.minecraft.client.Minecraft
                .getInstance()
                .font
        val textColor = if (!enabled) VenusTheme.TEXT_DISABLED else textColor()

        val iconW = if (leadingIcon != null) VenusDimensions.ICON_SMALL + 4 else 0
        VenusDraw.textCentered(g, font, label, bounds, textColor, false)
        if (iconW > 0) {
            // Placeholder: draw a small accent square until icon textures are wired
            VenusDraw.rect(
                g,
                bounds.x + 6,
                bounds.y + (bounds.height - VenusDimensions.ICON_SMALL) / 2,
                VenusDimensions.ICON_SMALL,
                VenusDimensions.ICON_SMALL,
                textColor,
            )
        }
    }

    private fun baseColor(): Int =
        when (variant) {
            Variant.ACTIVE -> VenusTheme.ACTIVE
            Variant.DANGER -> VenusTheme.DANGER_DIM
            Variant.SUCCESS -> VenusTheme.SUCCESS
            Variant.NORMAL -> VenusTheme.RAISED
        }

    private fun borderColor(): Int =
        if (!enabled) {
            VenusTheme.BORDER
        } else {
            when (variant) {
                Variant.ACTIVE -> VenusTheme.ACCENT
                Variant.DANGER -> VenusTheme.DANGER
                Variant.SUCCESS -> VenusTheme.SUCCESS
                Variant.NORMAL -> VenusTheme.BORDER_BRIGHT
            }
        }

    private fun textColor(): Int =
        when (variant) {
            Variant.ACTIVE -> VenusTheme.TEXT
            Variant.DANGER -> VenusTheme.DANGER
            Variant.SUCCESS -> VenusTheme.TEXT
            Variant.NORMAL -> VenusTheme.TEXT
        }

    private fun focusColor(): Int =
        when (variant) {
            Variant.DANGER -> VenusTheme.DANGER
            Variant.SUCCESS -> VenusTheme.SUCCESS
            else -> VenusTheme.ACCENT
        }

    override fun mouseClicked(
        mouseButtonEvent: MouseButtonEvent,
        doubleClick: Boolean,
    ): Boolean {
        if (!enabled || !visible) return false
        val mouseX = mouseButtonEvent.x().toInt()
        val mouseY = mouseButtonEvent.y().toInt()
        if (!bounds.contains(mouseX, mouseY)) return false
        if (mouseButtonEvent.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressed = true
            return true
        }
        return false
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (!enabled || !visible) return false
        val mouseX = mouseButtonEvent.x().toInt()
        val mouseY = mouseButtonEvent.y().toInt()
        if (pressed && mouseButtonEvent.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressed = false
            if (bounds.contains(mouseX, mouseY)) {
                playDownSound(
                    net.minecraft.client.Minecraft
                        .getInstance()
                        .soundManager,
                )
                onPressed()
                return true
            }
        }
        return false
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (!enabled || !isFocused) return false
        if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_SPACE || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
            pressed = true
            onPressed()
            pressed = false
            return true
        }
        return false
    }

    fun tooltip(text: String) {
        tooltipText = text
    }

    fun disabledTooltip(reason: String) {
        disabledReason = reason
        if (!enabled) tooltipText = reason
    }
}
