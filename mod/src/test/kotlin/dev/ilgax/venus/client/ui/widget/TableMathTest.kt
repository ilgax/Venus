package dev.ilgax.venus.client.ui.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableMathTest {
    private val columns =
        listOf(
            VenusColumn("name", "Name", minWidth = 40, preferredWidth = 100, priority = 2),
            VenusColumn(
                "status",
                "Status",
                minWidth = 30,
                preferredWidth = 80,
                priority = 1,
                hideable = true,
            ),
            VenusColumn(
                "uuid",
                "UUID",
                minWidth = 60,
                preferredWidth = 200,
                priority = 0,
                hideable = true,
            ),
        )

    @Test
    fun `distribute returns preferred widths when space is available`() {
        val widths = TableMath.distribute(columns, 500)
        assertEquals(3, widths.size)
        assertEquals(100 + 120, widths[0]) // extra goes to highest priority
    }

    @Test
    fun `distribute returns min widths when space is tight`() {
        val widths = TableMath.distribute(columns, 50)
        assertEquals(40, widths[0])
        assertEquals(30, widths[1])
        assertEquals(60, widths[2])
    }

    @Test
    fun `visibleColumns hides low-priority hideable columns when overflowing`() {
        val preferred = listOf(100, 80, 200)
        val visible = TableMath.visibleColumns(columns, 150, preferred)
        assertTrue(visible.size <= 3)
        assertTrue(visible.any { it.first.id == "name" })
    }

    @Test
    fun `visibleColumns keeps all when space is available`() {
        val preferred = listOf(100, 80, 200)
        val visible = TableMath.visibleColumns(columns, 500, preferred)
        assertEquals(3, visible.size)
    }
}

class SearchFilterTest {
    @Test
    fun `empty query returns all items`() {
        val items = listOf("Alice", "Bob", "Charlie")
        assertEquals(items, SearchFilter.apply(items, "", { it }))
    }

    @Test
    fun `case-insensitive substring match`() {
        val items = listOf("Alice", "Bob", "Charlie")
        assertEquals(listOf("Alice"), SearchFilter.apply(items, "ali", { it }))
        assertEquals(listOf("Bob"), SearchFilter.apply(items, "OB", { it }))
    }

    @Test
    fun `matches handles whitespace`() {
        assertTrue(SearchFilter.matches("Alice", "  ali  "))
        assertTrue(SearchFilter.matches("Alice", ""))
    }

    @Test
    fun `apply on empty list returns empty`() {
        assertEquals(emptyList<String>(), SearchFilter.apply(emptyList(), "test", { it }))
    }
}

class DropdownStateTest {
    @Test
    fun `initial selection is null when not in options`() {
        val state = DropdownState(listOf("A", "B"), "C")
        assertEquals(null, state.selected)
    }

    @Test
    fun `initial selection is set when in options`() {
        val state = DropdownState(listOf("A", "B"), "A")
        assertEquals("A", state.selected)
    }

    @Test
    fun `select sets the selected option`() {
        val state = DropdownState(listOf("A", "B", "C"))
        state.select(1)
        assertEquals("B", state.selected)
    }

    @Test
    fun `select ignores out-of-range index`() {
        val state = DropdownState(listOf("A", "B"))
        state.select(5)
        assertEquals(null, state.selected)
    }
}
