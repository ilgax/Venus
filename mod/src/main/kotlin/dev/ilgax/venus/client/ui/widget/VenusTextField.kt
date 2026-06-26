package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * Venus-styled text field wrapping Minecraft's native [EditBox]. Preserves all
 * native behavior: clipboard shortcuts, selection, cursor movement, Unicode,
 * and focus handling. We do NOT rebuild text editing from scratch.
 *
 * The [EditBox] is added to the screen as a renderable widget; this class
 * styles its background/border to match the Venus theme and delegates all
 * input to the native implementation.
 */
class VenusTextField(
    private val font: Font,
    x: Int = 0,
    y: Int = 0,
    width: Int,
    height: Int = VenusDimensions.INPUT_HEIGHT,
    placeholder: String = "",
) {
    val editBox: EditBox =
        EditBox(font, x, y, width, height, Component.literal(placeholder)).apply {
            setMaxLength(256)
            setBordered(false)
            setSuggestion("")
            setTextColor(VenusTheme.TEXT)
            setTextColorUneditable(VenusTheme.TEXT_MUTED)
        }

    var bounds: Bounds = Bounds(x, y, width, height)
        private set

    var placeholder: String = placeholder
        set(value) {
            field = value
            updateSuggestion()
        }

    val value: String get() = editBox.value

    val isFocused: Boolean get() = editBox.isFocused

    fun layout(b: Bounds) {
        bounds = b
        editBox.setX(b.x + INSET)
        editBox.setY(b.y + (b.height - font.lineHeight) / 2 - 1)
        editBox.setWidth(b.width - INSET * 2)
        editBox.setHeight(font.lineHeight + 2)
    }

    fun editBox(): EditBox = editBox

    fun setVisible(v: Boolean) {
        editBox.setVisible(v)
    }

    fun setFocused(v: Boolean) {
        editBox.setFocused(v)
    }

    fun setValue(v: String) {
        editBox.setValue(v)
    }

    fun moveCursorToEnd(select: Boolean = false) {
        editBox.moveCursorToEnd(select)
    }

    fun clear() {
        editBox.setValue("")
    }

    /**
     * Draw the Venus background/border behind the native EditBox. The EditBox
     * itself is rendered by the screen's renderable list; this draws the frame.
     */
    fun renderBackground(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = bounds.contains(mouseX, mouseY)
        VenusDraw.rect(g, bounds, VenusTheme.SURFACE)
        VenusDraw.border(
            g,
            bounds,
            if (isFocused) {
                VenusTheme.ACCENT
            } else if (hovered) {
                VenusTheme.BORDER_BRIGHT
            } else {
                VenusTheme.BORDER
            },
        )
        if (value.isEmpty() && !isFocused && placeholder.isNotEmpty()) {
            VenusDraw.text(
                g,
                font,
                placeholder,
                bounds.x + INSET,
                bounds.y + (bounds.height - font.lineHeight) / 2,
                VenusTheme.TEXT_DISABLED,
                false,
            )
        }
    }

    private fun updateSuggestion() {
        editBox.setSuggestion(if (value.isEmpty()) placeholder else null)
    }

    private companion object {
        const val INSET = 6
    }
}

/**
 * Search field — a [VenusTextField] with a leading search glyph and live
 * query callback. Pure filter logic lives in [SearchFilter] for unit testing.
 */
class VenusSearchField(
    font: Font,
    x: Int = 0,
    y: Int = 0,
    width: Int,
    height: Int = VenusDimensions.INPUT_HEIGHT,
    placeholder: String = "Search...",
    private val onQueryChange: (String) -> Unit = {},
) {
    private val textField = VenusTextField(font, x, y, width, height, placeholder)

    val bounds: Bounds get() = textField.bounds
    val query: String get() = textField.value
    val isFocused: Boolean get() = textField.isFocused

    init {
        textField.editBox.setResponder { onQueryChange(it) }
    }

    fun layout(b: Bounds) {
        textField.layout(b)
    }

    fun editBox(): EditBox = textField.editBox

    fun setVisible(v: Boolean) {
        textField.setVisible(v)
    }

    fun setFocused(v: Boolean) {
        textField.setFocused(v)
    }

    fun render(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        textField.renderBackground(g, mouseX, mouseY)
        // Leading search glyph
        val gx = textField.bounds.x + 4
        val gy = textField.bounds.centerY
        g.fill(gx, gy - 3, gx + 5, gy - 2, VenusTheme.TEXT_MUTED)
        g.fill(gx, gy - 3, gx + 1, gy + 2, VenusTheme.TEXT_MUTED)
        g.fill(gx + 4, gy - 3, gx + 5, gy + 2, VenusTheme.TEXT_MUTED)
        g.fill(gx, gy + 1, gx + 5, gy + 2, VenusTheme.TEXT_MUTED)
        g.fill(gx + 5, gy + 2, gx + 7, gy + 3, VenusTheme.TEXT_MUTED)
        g.fill(gx + 6, gy + 3, gx + 7, gy + 5, VenusTheme.TEXT_MUTED)
    }
}

/**
 * Pure search/filter logic — unit-testable without Minecraft.
 */
object SearchFilter {
    fun <T> apply(
        items: List<T>,
        query: String,
        extractor: (T) -> String,
    ): List<T> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter { extractor(it).lowercase().contains(q) }
    }

    fun matches(
        text: String,
        query: String,
    ): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return text.lowercase().contains(q)
    }
}
