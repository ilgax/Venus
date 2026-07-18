package dev.ilgax.venus.client.ui.component

import dev.ilgax.venus.client.ui.UiTestFixture
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.ModalKind
import dev.ilgax.venus.client.ui.core.ToastKind
import dev.ilgax.venus.client.ui.core.VenusModalRequest
import dev.ilgax.venus.client.ui.core.VenusToastRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentRenderSmokeTest : UiTestFixture() {
    @Test
    fun `card and section report deterministic content bounds with and without title`() {
        val reported = mutableListOf<Bounds>()
        val card = VenusCard(Bounds(10, 20, 120, 80))

        card.render(graphics, font, reported::add)
        card.title = "Status"
        card.render(graphics, font, reported::add)
        VenusSection(Bounds(0, 0, 100, 50), "Details").render(graphics, font, reported::add)

        assertEquals(Bounds(20, 30, 100, 60), reported[0])
        assertEquals(Bounds(20, 45, 100, 45), reported[1])
        assertEquals(Bounds(0, 17, 100, 33), reported[2])
    }

    @Test
    fun `empty loading and error states render optional branches`() {
        VenusEmptyState(Bounds(0, 0, 100, 40))
            .apply {
                message = "Empty"
                subtext = "Try again"
            }.render(graphics, font)
        VenusLoadingState(Bounds(0, 0, 100, 40)).render(graphics, font, 0.5f)
        VenusErrorState(Bounds(0, 0, 100, 40)).render(graphics, font)
    }

    @Test
    fun `player event and console rows render semantic variants`() {
        val bounds = Bounds(0, 0, 180, 24)
        VenusPlayerHead(0, 0, "uuid").render(graphics, true)
        VenusPlayerHead(0, 0, "uuid").render(graphics, false)
        VenusPlayerRow(bounds, "Player", "uuid", online = true, operator = true).render(graphics, font, true, false)
        VenusPlayerRow(bounds, "Offline", "uuid", online = false, operator = false).render(graphics, font, false, true, false)
        VenusEventRow(bounds, "12:00", "Server started").render(graphics, font)
        listOf("INFO", "WARN", "ERROR", "DEBUG", "CUSTOM").forEach { level ->
            VenusConsoleLine(bounds, "12:00", level, "[$level] line").render(graphics, font, selected = true)
        }
        VenusConsoleLine(bounds, "", "INFO", "line", "a.b.Logger", "message", simpleLogger = true).render(graphics, font)
    }

    @Test
    fun `modal exposes semantic accents and click regions`() {
        ModalKind.entries.forEach { kind ->
            var confirmed = 0
            var cancelled = 0
            val modal =
                VenusModal(
                    Bounds(10, 20, 220, 120),
                    VenusModalRequest(
                        kind = kind,
                        title = "Title",
                        message = "A message that wraps across the modal",
                        onConfirm = { confirmed++ },
                        onCancel = { cancelled++ },
                    ),
                )
            modal.tick(16f)
            modal.render(graphics, font)
            val confirm = modal.confirmBounds(font)
            val cancel = modal.cancelBounds(font)
            assertEquals(true, confirm.contains(confirm.centerX, confirm.centerY))
            assertEquals(true, cancel.contains(cancel.centerX, cancel.centerY))
        }
    }

    @Test
    fun `toast variants render within calculated bounds`() {
        ToastKind.entries.forEachIndexed { index, kind ->
            VenusToast(
                Bounds(10, 10 + index * 40, 220, 36),
                VenusToastRequest(index.toLong(), kind, "Title", "Message", 0, 1000),
            ).render(graphics, font)
        }
    }
}
