package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.ScissorStack
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Dropdown selector. Keyboard navigable (up/down, enter, escape), closes on
 * outside click, scrolls when options overflow. Pure selection logic in
 * [DropdownState] for unit testing.
 */
class VenusDropdown(
    private val font: Font,
    x: Int = 0,
    y: Int = 0,
    width: Int,
    height: Int = VenusDimensions.DROPDOWN_HEIGHT,
    options: List<String>,
    initial: String? = null,
    private val onSelect: (String) -> Unit,
) : VenusWidget(x, y, width, height, Component.empty()) {
    private val state = DropdownState(options, initial)

    private var open = false
    private var scrollOffset = 0
    private var hoverIndex = -1

    val selected: String? get() = state.selected

    override fun drawVenus(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        VenusDraw.rect(g, bounds, VenusTheme.SURFACE)
        VenusDraw.border(g, bounds, if (isFocused || open) VenusTheme.ACCENT else VenusTheme.BORDER_BRIGHT)

        val label = selected ?: "Select..."
        val textColor = if (selected != null) VenusTheme.TEXT else VenusTheme.TEXT_MUTED
        VenusDraw.text(g, font, label, bounds.x + 6, bounds.y + (bounds.height - font.lineHeight) / 2, textColor, false)

        // Caret
        val cx = bounds.right - 8
        val cy = bounds.centerY
        g.fill(cx - 3, cy - 1, cx + 3, cy, VenusTheme.TEXT_MUTED)
        g.fill(cx - 2, cy, cx + 2, cy + 1, VenusTheme.TEXT_MUTED)
        g.fill(cx - 1, cy + 1, cx + 1, cy + 2, VenusTheme.TEXT_MUTED)

        if (isFocused && !open) VenusDraw.focusOutline(g, bounds, VenusTheme.ACCENT)

        if (open) {
            val itemH = VenusDimensions.ROW_HEIGHT_COMPACT
            val maxVisible = minOf(state.options.size, MAX_VISIBLE_ITEMS)
            val popupH = maxVisible * itemH + 2
            val popup = Bounds(bounds.x, bounds.bottom, bounds.width, popupH)
            ScissorStack.with(g, popup) {
                VenusDraw.rect(g, popup, VenusTheme.RAISED)
                VenusDraw.border(g, popup, VenusTheme.BORDER_BRIGHT)
                val visible = state.options.size
                for (i in 0 until visible) {
                    val itemY = popup.y + 1 + i * itemH - scrollOffset
                    if (itemY + itemH < popup.y || itemY > popup.bottom) continue
                    val itemBounds = Bounds(popup.x + 1, itemY, popup.width - 2, itemH)
                    val isSel = state.options[i] == selected
                    val isHover = i == hoverIndex
                    val bg =
                        when {
                            isSel -> VenusTheme.ACTIVE
                            isHover -> VenusTheme.HOVER
                            else -> VenusTheme.RAISED
                        }
                    VenusDraw.rect(g, itemBounds, bg)
                    VenusDraw.text(
                        g,
                        font,
                        state.options[i],
                        itemBounds.x + 6,
                        itemBounds.y + (itemH - font.lineHeight) / 2,
                        VenusTheme.TEXT,
                        false,
                    )
                }
            }
        }
    }

    override fun mouseClicked(
        mouseButtonEvent: MouseButtonEvent,
        doubleClick: Boolean,
    ): Boolean {
        if (!visible || !active) return false
        if (mouseButtonEvent.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        val mouseX = mouseButtonEvent.x().toInt()
        val mouseY = mouseButtonEvent.y().toInt()
        if (bounds.contains(mouseX, mouseY)) {
            open = !open
            return true
        }
        if (open) {
            val itemH = VenusDimensions.ROW_HEIGHT_COMPACT
            val maxVisible = minOf(state.options.size, MAX_VISIBLE_ITEMS)
            val popupH = maxVisible * itemH + 2
            val popup = Bounds(bounds.x, bounds.bottom, bounds.width, popupH)
            if (popup.contains(mouseX, mouseY)) {
                val idx = ((mouseY - popup.y - 1 + scrollOffset) / itemH).coerceIn(0, state.options.lastIndex)
                state.select(idx)
                onSelect(state.selected!!)
                open = false
                return true
            }
            open = false
            return false
        }
        return false
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean {
        if (!open) return false
        val maxVisible = minOf(state.options.size, MAX_VISIBLE_ITEMS)
        val maxScroll = (state.options.size - maxVisible).coerceAtLeast(0)
        scrollOffset = (scrollOffset - scrollY.toInt() * VenusDimensions.SCROLL_LINES).coerceIn(0, maxScroll)
        return true
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (!isFocused && !open) return false
        when (keyEvent.key()) {
            GLFW.GLFW_KEY_DOWN -> {
                if (!open) open = true
                hoverIndex = (hoverIndex + 1).coerceAtMost(state.options.lastIndex)
                return true
            }
            GLFW.GLFW_KEY_UP -> {
                hoverIndex = (hoverIndex - 1).coerceAtLeast(0)
                return true
            }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (open && hoverIndex >= 0) {
                    state.select(hoverIndex)
                    onSelect(state.selected!!)
                    open = false
                    return true
                }
            }
            GLFW.GLFW_KEY_ESCAPE -> {
                if (open) {
                    open = false
                    return true
                }
            }
        }
        return false
    }

    private companion object {
        const val MAX_VISIBLE_ITEMS = 8
    }
}

/**
 * Pure dropdown selection logic — unit-testable.
 */
class DropdownState(
    val options: List<String>,
    initial: String? = null,
) {
    var selected: String? = initial?.takeIf { it in options }
        private set
    private var selectedIndex: Int = options.indexOf(initial).takeIf { it >= 0 } ?: -1

    fun select(index: Int) {
        if (index !in options.indices) return
        selectedIndex = index
        selected = options[index]
    }

    fun selected(index: Int): Int = selectedIndex
}
