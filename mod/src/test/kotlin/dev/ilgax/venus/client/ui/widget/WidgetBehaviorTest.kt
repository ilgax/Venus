package dev.ilgax.venus.client.ui.widget

import dev.ilgax.venus.client.ui.UiTestFixture
import dev.ilgax.venus.client.ui.UiTestSupport
import dev.ilgax.venus.client.ui.core.Bounds
import org.lwjgl.glfw.GLFW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetBehaviorTest : UiTestFixture() {
    @Test
    fun `button keyboard activation respects focus and enabled state`() {
        var presses = 0
        val button = VenusButton(width = 80, text = "Run") { presses++ }

        assertFalse(button.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_ENTER)))
        button.isFocused = true
        assertTrue(button.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_ENTER)))
        assertEquals(1, presses)

        button.enabled = false
        assertFalse(button.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_ENTER)))
        assertEquals(1, presses)
    }

    @Test
    fun `button mouse press requires visibility left button and bounds`() {
        val button = VenusButton(10, 20, 80, text = "Run") {}

        assertFalse(button.mouseClicked(UiTestSupport.mouse(20.0, 25.0, GLFW.GLFW_MOUSE_BUTTON_RIGHT), false))
        assertFalse(button.mouseClicked(UiTestSupport.mouse(200.0, 25.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false))
        assertTrue(button.mouseClicked(UiTestSupport.mouse(20.0, 25.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false))
        assertFalse(button.mouseReleased(UiTestSupport.mouse(200.0, 25.0, GLFW.GLFW_MOUSE_BUTTON_LEFT)))

        button.visible = false
        assertFalse(button.mouseClicked(UiTestSupport.mouse(20.0, 25.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false))
    }

    @Test
    fun `icon button supports mouse release and keyboard activation`() {
        var presses = 0
        val button = VenusIconButton(10, 10, 20, VenusIconButton.IconGlyph.CHECK, "Confirm") { presses++ }

        assertTrue(button.mouseClicked(UiTestSupport.mouse(15.0, 15.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false))
        assertTrue(button.mouseReleased(UiTestSupport.mouse(15.0, 15.0, GLFW.GLFW_MOUSE_BUTTON_LEFT)))
        button.isFocused = true
        assertTrue(button.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_SPACE)))
        assertEquals(2, presses)
    }

    @Test
    fun `toggle changes once and honors focus and hit bounds`() {
        val changes = mutableListOf<Boolean>()
        val toggle = VenusToggle(10, 10, initial = false, onChange = changes::add)

        toggle.set(false)
        assertTrue(changes.isEmpty())
        assertFalse(toggle.mouseClicked(UiTestSupport.mouse(1.0, 1.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false))
        assertTrue(toggle.mouseClicked(UiTestSupport.mouse(15.0, 15.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false))
        assertEquals(listOf(true), changes)

        toggle.isFocused = true
        assertTrue(toggle.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_ENTER)))
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun `slider clamps mouse and keyboard changes`() {
        val changes = mutableListOf<Double>()
        val slider = VenusSlider(10, 10, 100, min = 0.0, max = 10.0, step = 1.0, initial = 5.0, onChange = changes::add)

        slider.set(99.0)
        assertEquals(10.0, slider.value)
        slider.isFocused = true
        assertTrue(slider.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_LEFT)))
        assertEquals(9.0, slider.value)
        assertTrue(slider.mouseClicked(UiTestSupport.mouse(12.0, 15.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false))
        assertTrue(slider.mouseDragged(UiTestSupport.mouse(108.0, 15.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), 0.0, 0.0))
        assertTrue(slider.mouseReleased(UiTestSupport.mouse(108.0, 15.0, GLFW.GLFW_MOUSE_BUTTON_LEFT)))
        assertTrue(changes.isNotEmpty())
    }

    @Test
    fun `dropdown supports keyboard selection escape and outside close`() {
        val selected = mutableListOf<String>()
        val dropdown = VenusDropdown(UiTestSupport.font(), 10, 10, 100, options = listOf("A", "B", "C"), onSelect = selected::add)
        dropdown.isFocused = true

        assertTrue(dropdown.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_DOWN)))
        assertTrue(dropdown.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_DOWN)))
        assertTrue(dropdown.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_ENTER)))
        assertEquals("B", dropdown.selected)
        assertEquals(listOf("B"), selected)

        assertTrue(dropdown.mouseClicked(UiTestSupport.mouse(20.0, 15.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false))
        assertTrue(dropdown.keyPressed(UiTestSupport.key(GLFW.GLFW_KEY_ESCAPE)))
        assertFalse(dropdown.mouseClicked(UiTestSupport.mouse(200.0, 200.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false))
    }

    @Test
    fun `list and table clamp scroll and reject scrollbar hit area`() {
        val list = VenusList(Bounds(10, 10, 100, 60), rowHeight = 20)
        list.scroll(99, 10)
        assertEquals(7, list.scrollOffset)
        assertEquals(7, list.hitTest(20, 15, 10))
        assertEquals(-1, list.hitTest(109, 15, 10))
        list.clampScroll(1)
        assertEquals(0, list.scrollOffset)

        val table = VenusTable(Bounds(0, 0, 120, 100), listOf(VenusColumn("name", "Name")), rowHeight = 20)
        table.scroll(99, 10)
        assertEquals(table.maxScroll(10), table.scrollOffset)
        table.select(4)
        assertEquals(4, table.selectedRowIndex)
        assertEquals(-1, table.hitTest(119, 30, 10))
    }

    @Test
    fun `scrollbar drag maps thumb position to caller scroll`() {
        var scroll = 0
        val scrollbar =
            VenusScrollbar(
                x = 100,
                y = 10,
                height = 100,
                totalItems = { 20 },
                visibleItems = { 5 },
                maxScroll = { 15 },
                getScroll = { scroll },
                setScroll = { scroll = it },
            )
        val x = scrollbar.bounds.x.toDouble()

        assertTrue(scrollbar.mouseClicked(UiTestSupport.mouse(x, 50.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false))
        assertTrue(scrollbar.mouseDragged(UiTestSupport.mouse(x, 100.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), 0.0, 50.0))
        assertTrue(scroll > 0)
        assertTrue(scrollbar.mouseReleased(UiTestSupport.mouse(x, 100.0, GLFW.GLFW_MOUSE_BUTTON_LEFT)))
        assertFalse(scrollbar.mouseDragged(UiTestSupport.mouse(x, 50.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), 0.0, 0.0))
    }

    @Test
    fun `interactive widgets render stable visual state branches`() {
        VenusButton(10, 10, 100, text = "Run") {}
            .apply {
                isFocused = true
                leadingIcon = "run"
            }.renderVenus(graphics, 20, 15, 0f)
        VenusButton.Variant.entries.forEach { variant ->
            VenusButton(10, 10, 100, text = variant.name) {}
                .apply {
                    this.variant = variant
                    enabled = variant != VenusButton.Variant.DANGER
                }.renderVenus(graphics, 0, 0, 0f)
        }

        VenusIconButton.IconGlyph.entries.forEach { glyph ->
            VenusIconButton(10, 10, icon = glyph, tooltipText = glyph.name) {}
                .apply { isFocused = true }
                .renderVenus(graphics, 15, 15, 0f)
        }

        VenusToggle(10, 10, initial = false) {}
            .apply {
                isFocused = true
                renderVenus(graphics, 15, 15, 0f)
                set(true)
                renderVenus(graphics, 0, 0, 0f)
            }
        VenusSlider(10, 10, 100, min = 0.0, max = 10.0, step = 1.0, initial = 5.0, onChange = {})
            .apply { isFocused = true }
            .renderVenus(graphics, 0, 0, 0f)
    }

    @Test
    fun `dropdown table and tooltip render empty selected hovered and overflow branches`() {
        val options = (1..12).map { "Option $it" }
        val dropdown = VenusDropdown(font, 10, 10, 100, options = options, onSelect = {})
        dropdown.renderVenus(graphics, 0, 0, 0f)
        dropdown.mouseClicked(UiTestSupport.mouse(20.0, 15.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), false)
        dropdown.mouseScrolled(20.0, 40.0, 0.0, -1.0)
        dropdown.renderVenus(graphics, 20, 50, 0f)

        val columns =
            listOf(
                VenusColumn("name", "Name", preferredWidth = 80, hideable = false),
                VenusColumn("state", "State", preferredWidth = 60),
            )
        val table = VenusTable(Bounds(0, 0, 150, 80), columns)
        table.render(graphics, font, 0, 0, 0, listOf(80, 60)) { _, _ -> "" }
        table.select(0)
        table.render(graphics, font, 10, 30, 3, listOf(80, 60)) { row, column -> "$row:$column" }

        VenusTooltip.render(graphics, font, "Hint", 10, 10)
        VenusTooltip.render(graphics, font, listOf("One", "Two"), 10, 10)
    }
}
