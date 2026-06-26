package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.client.ui.component.VenusEmptyState
import dev.ilgax.venus.client.ui.component.VenusPlayerHead
import dev.ilgax.venus.client.ui.component.VenusStatusIndicator
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusSpacing
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import dev.ilgax.venus.client.ui.widget.VenusButton
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Authentication page. Pending requests list, selected request details,
 * approve/deny/trust, and recent auth history.
 *
 * Venus does not currently expose a pending-auth-request packet type. This page
 * is wired to the existing session/handshake state so it reflects real auth
 * status. When a pending-request protocol is added, the [pendingRequests] list
 * can be populated from a new SessionState field without changing the page
 * structure. No geographic IP lookup or fingerprint data is invented.
 */
class AuthPage(
    private val onApprove: (String) -> Unit,
    private val onDeny: (String) -> Unit,
    private val onTrust: (String) -> Unit,
) : VenusPageContract {
    private var contentBounds: Bounds = Bounds(0, 0, 0, 0)
    private var selectedRequestId: String? = null

    data class AuthRequest(
        val id: String,
        val playerName: String,
        val playerUuid: String,
        val time: String,
        val reason: String,
    )

    // Placeholder list — populated when Venus adds a pending-request packet.
    private val pendingRequests: List<AuthRequest> = emptyList()

    // Recent auth history derived from handshake state.
    private val recentHistory: List<String>
        get() =
            when (SessionState.handshakeState) {
                SessionState.HandshakeState.ACTIVE -> listOf("Session authenticated and active")
                SessionState.HandshakeState.EXPECTING_READY -> listOf("Awaiting server ready signal")
                SessionState.HandshakeState.IDLE -> listOf("No active session")
            }

    override fun layout(contentBounds: Bounds) {
        this.contentBounds = contentBounds
    }

    override fun render(
        g: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val pad = VenusDimensions.CONTENT_PADDING
        val inner = contentBounds.inset(pad)

        val leftW = (inner.width - VenusSpacing.LG) * 3 / 5
        val rightX = inner.x + leftW + VenusSpacing.LG
        val rightW = inner.width - leftW - VenusSpacing.LG

        renderPendingList(g, font, Bounds(inner.x, inner.y, leftW, inner.height), mouseX, mouseY)
        renderDetails(g, font, Bounds(rightX, inner.y, rightW, inner.height), mouseX, mouseY)
    }

    private fun renderPendingList(
        g: GuiGraphics,
        font: Font,
        bounds: Bounds,
        mouseX: Int,
        mouseY: Int,
    ) {
        VenusDraw.rect(g, bounds, VenusTheme.SURFACE)
        VenusDraw.border(g, bounds, VenusTheme.BORDER)
        VenusDraw.text(g, font, "Pending Requests", bounds.x + 8, bounds.y + 6, VenusTheme.TEXT_MUTED, false)
        VenusDraw.hSeparator(g, bounds.x + 8, bounds.y + 6 + font.lineHeight + 2, bounds.width - 16, VenusTheme.BORDER)

        if (pendingRequests.isEmpty()) {
            VenusEmptyState(Bounds(bounds.x, bounds.y + 30, bounds.width, bounds.height - 30)).run {
                message = "No pending requests"
                render(g, font)
            }
            return
        }
        val rowH = VenusDimensions.ROW_HEIGHT
        pendingRequests.forEachIndexed { i, req ->
            val rb = Bounds(bounds.x + 4, bounds.y + 30 + i * rowH, bounds.width - 8, rowH)
            val hovered = rb.contains(mouseX, mouseY)
            val selected = req.id == selectedRequestId
            VenusDraw.rect(
                g,
                rb,
                if (selected) {
                    VenusTheme.ACTIVE
                } else if (hovered) {
                    VenusTheme.HOVER
                } else {
                    VenusTheme.SURFACE
                },
            )
            VenusPlayerHead(rb.x + 4, rb.y + (rb.height - 12) / 2, req.playerUuid, 12).render(g)
            VenusDraw.textTruncated(
                g,
                font,
                req.playerName,
                rb.x + 20,
                rb.y + (rb.height - font.lineHeight) / 2,
                rb.width - 40,
                VenusTheme.TEXT,
                false,
            )
            VenusStatusIndicator(rb.right - 60, rb.y + (rb.height - font.lineHeight) / 2, "Pending", VenusTheme.WARNING).render(g, font)
        }
    }

    private fun renderDetails(
        g: GuiGraphics,
        font: Font,
        bounds: Bounds,
        mouseX: Int,
        mouseY: Int,
    ) {
        VenusDraw.rect(g, bounds, VenusTheme.SURFACE)
        VenusDraw.border(g, bounds, VenusTheme.BORDER)
        VenusDraw.text(g, font, "Details", bounds.x + 8, bounds.y + 6, VenusTheme.TEXT_MUTED, false)
        VenusDraw.hSeparator(g, bounds.x + 8, bounds.y + 6 + font.lineHeight + 2, bounds.width - 16, VenusTheme.BORDER)

        val req = pendingRequests.find { it.id == selectedRequestId }
        if (req == null) {
            renderSessionStatus(g, font, bounds)
            return
        }

        var y = bounds.y + 30
        val lineH = font.lineHeight + 4
        VenusDraw.text(g, font, "Player: ${req.playerName}", bounds.x + 12, y, VenusTheme.TEXT, false)
        y += lineH
        VenusDraw.text(g, font, "UUID: ${req.playerUuid}", bounds.x + 12, y, VenusTheme.TEXT_MUTED, false)
        y += lineH
        VenusDraw.text(g, font, "Time: ${req.time}", bounds.x + 12, y, VenusTheme.TEXT_MUTED, false)
        y += lineH
        VenusDraw.text(g, font, "Reason: ${req.reason}", bounds.x + 12, y, VenusTheme.TEXT_MUTED, false)
        y += lineH + 8

        val btnW = (bounds.width - 24 - VenusSpacing.SM * 2) / 3
        val btnY = y
        VenusButton(0, 0, btnW, text = "Approve", onPressed = {
            onApprove(req.id)
        }).apply { layout(bounds.x + 12, btnY, btnW, VenusDimensions.BUTTON_HEIGHT) }.renderVenus(g, mouseX, mouseY, 0f)
        VenusButton(0, 0, btnW, text = "Deny", onPressed = {
            onDeny(req.id)
        })
            .apply {
                layout(
                    bounds.x + 12 + btnW + VenusSpacing.SM,
                    btnY,
                    btnW,
                    VenusDimensions.BUTTON_HEIGHT,
                )
            }.renderVenus(g, mouseX, mouseY, 0f)
        VenusButton(0, 0, btnW, text = "Trust", onPressed = {
            onTrust(req.id)
        })
            .apply {
                layout(bounds.x + 12 + (btnW + VenusSpacing.SM) * 2, btnY, btnW, VenusDimensions.BUTTON_HEIGHT)
            }.renderVenus(g, mouseX, mouseY, 0f)
    }

    private fun renderSessionStatus(
        g: GuiGraphics,
        font: Font,
        bounds: Bounds,
    ) {
        var y = bounds.y + 30
        val lineH = font.lineHeight + 4
        VenusDraw.text(g, font, "Current Session", bounds.x + 12, y, VenusTheme.TEXT, false)
        y += lineH

        val state = SessionState.handshakeState
        val (label, color) =
            when (state) {
                SessionState.HandshakeState.ACTIVE -> "Active" to VenusTheme.SUCCESS
                SessionState.HandshakeState.EXPECTING_READY -> "Authenticating" to VenusTheme.WARNING
                SessionState.HandshakeState.IDLE -> "Idle" to VenusTheme.TEXT_MUTED
            }
        VenusStatusIndicator(bounds.x + 12, y, label, color).render(g, font)
        y += lineH + 4

        VenusDraw.text(g, font, "Recent History", bounds.x + 12, y, VenusTheme.TEXT_MUTED, false)
        y += lineH
        recentHistory.forEach { line ->
            VenusDraw.textTruncated(g, font, line, bounds.x + 12, y, bounds.width - 24, VenusTheme.TEXT_MUTED, false)
            y += lineH
        }
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        if (button != 0) return false
        val pad = VenusDimensions.CONTENT_PADDING
        val inner = contentBounds.inset(pad)
        val leftW = (inner.width - VenusSpacing.LG) * 3 / 5

        val rowH = VenusDimensions.ROW_HEIGHT
        pendingRequests.forEachIndexed { i, req ->
            val rb = Bounds(inner.x + 4, inner.y + 30 + i * rowH, leftW - 8, rowH)
            if (rb.contains(mouseX, mouseY)) {
                selectedRequestId = req.id
                return true
            }
        }
        return false
    }
}
