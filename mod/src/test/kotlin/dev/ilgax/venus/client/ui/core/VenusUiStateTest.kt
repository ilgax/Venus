package dev.ilgax.venus.client.ui.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VenusUiStateTest {
    @Test
    fun `starts on dashboard page`() {
        val state = VenusUiState()
        assertEquals(VenusPage.DASHBOARD, state.activePage)
    }

    @Test
    fun `navigateTo changes active page and records previous`() {
        val state = VenusUiState()
        state.navigateTo(VenusPage.CONSOLE)
        assertEquals(VenusPage.CONSOLE, state.activePage)
        assertEquals(VenusPage.DASHBOARD, state.previousPage)
    }

    @Test
    fun `navigateTo to same page is no-op`() {
        val state = VenusUiState()
        state.navigateTo(VenusPage.DASHBOARD)
        assertEquals(VenusPage.DASHBOARD, state.activePage)
        assertNull(state.previousPage)
    }

    @Test
    fun `modal stack pushes and pops`() {
        val state = VenusUiState()
        val modal = VenusModalRequest(ModalKind.INFO, "Test", "Message")
        state.pushModal(modal)
        assertEquals(modal, state.currentModal)
        assertEquals(modal, state.popModal())
        assertNull(state.currentModal)
    }

    @Test
    fun `toast add and expiry`() {
        val state = VenusUiState()
        val now = System.currentTimeMillis()
        state.addToast(VenusToastRequest(1, ToastKind.INFO, "T", "M", now, now + 1000))
        assertEquals(1, state.toasts.size)
        state.tickToasts(now + 2000)
        assertTrue(state.toasts.isEmpty())
    }

    @Test
    fun `toast stack bounded to max`() {
        val state = VenusUiState()
        val now = System.currentTimeMillis()
        repeat(10) { i ->
            state.addToast(VenusToastRequest(i.toLong(), ToastKind.INFO, "T$i", "M", now, now + 100000))
        }
        assertTrue(state.toasts.size <= 4)
    }
}
