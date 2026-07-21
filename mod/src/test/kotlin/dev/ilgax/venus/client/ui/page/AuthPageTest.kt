package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.client.ui.UiTestFixture
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.state.SessionState
import kotlin.test.Test
import kotlin.test.assertFalse

class AuthPageTest : UiTestFixture() {
    @Test
    fun `auth page presents every handshake state without invented requests`() {
        val page = AuthPage({}, {}, {})
        page.layout(Bounds(0, 0, 500, 300))

        page.render(graphics, font, 0, 0, 0f)
        SessionState.markExpectingReady()
        page.render(graphics, font, 0, 0, 0f)
        SessionState.markActive()
        page.render(graphics, font, 0, 0, 0f)

        assertFalse(page.mouseClicked(20.0, 50.0, 1))
        assertFalse(page.mouseClicked(20.0, 50.0, 0))
    }
}
