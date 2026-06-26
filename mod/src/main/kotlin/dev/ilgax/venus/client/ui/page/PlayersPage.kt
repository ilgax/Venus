package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.client.ui.component.VenusEmptyState
import dev.ilgax.venus.client.ui.component.VenusPlayerHead
import dev.ilgax.venus.client.ui.component.VenusPlayerRow
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusSpacing
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import dev.ilgax.venus.client.ui.widget.SearchFilter
import dev.ilgax.venus.client.ui.widget.VenusList
import dev.ilgax.venus.client.ui.widget.VenusSearchField
import dev.ilgax.venus.client.ui.widget.scrollbarForList
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Players page. Search field, online/offline filter, scrollable player list,
 * selected-player details panel. Player actions are callbacks — the page does
 * not invent network packets. Unsupported actions are omitted.
 *
 * Existing Venus player data: [SessionState.latestPlayerList] (summary) and
 * [SessionState.latestPlayerDetail] (detail with health/gamemode/world/pos).
 * Actions: heal, feed, kill, set_game_mode, set_whitelisted, set_blocked,
 * set_operator, teleport_admin_to_player, teleport_player_to_admin — all
 * routed through [sendPlayerAction].
 */
class PlayersPage(
    private val requestPlayerList: () -> Unit,
    private val requestPlayerDetail: (String) -> Unit,
    private val sendPlayerAction: (String, String, Any?) -> String,
    private val showPlayerHeads: () -> Boolean = { true },
) : VenusPageContract {
    private var contentBounds: Bounds = Bounds(0, 0, 0, 0)
    private var font: Font? = null
    private var searchField: VenusSearchField? = null
    private var list: VenusList? = null
    private var query: String = ""
    private var filter: PlayerFilter = PlayerFilter.ALL
    private var selectedUuid: String? = null
    private var pendingAction: String? = null
    private var pendingRequestId: String? = null
    private var requestedList = false
    private var actionButtonBounds: List<Pair<Bounds, PlayerAction>> = emptyList()

    override fun layout(contentBounds: Bounds) {
        this.contentBounds = contentBounds
        val pad = VenusDimensions.CONTENT_PADDING
        val inner = contentBounds.inset(pad)

        val searchH = VenusDimensions.INPUT_HEIGHT
        searchField?.layout(Bounds(inner.x, inner.y, inner.width / 2, searchH))

        val filterY = inner.y
        val filterX = inner.x + inner.width / 2 + VenusSpacing.SM
        val savedScroll = list?.scrollOffset ?: 0
        list =
            VenusList(
                Bounds(
                    inner.x,
                    inner.y + searchH + VenusSpacing.SM + 2,
                    inner.width,
                    inner.height - searchH - VenusSpacing.SM - 2,
                ),
            )
        list?.scrollOffset = savedScroll
    }

    enum class PlayerFilter(
        val label: String,
    ) {
        ALL("All"),
        ONLINE("Online"),
        OFFLINE("Offline"),
        BANNED("Banned"),
    }

    fun searchField(): VenusSearchField? {
        if (searchField == null) {
            searchField =
                VenusSearchField(
                    font ?: net.minecraft.client.Minecraft
                        .getInstance()
                        .font,
                    0,
                    0,
                    100,
                    placeholder = "Search players...",
                ) { q ->
                    query = q
                }
        }
        return searchField
    }

    override fun onEnter() {
        searchField?.setVisible(true)
        if (SessionState.sessionActive && !requestedList) {
            requestPlayerList()
            requestedList = true
        }
    }

    fun selectAndNavigate(uuid: String) {
        selectedUuid = uuid
        requestPlayerDetail(uuid)
    }

    override fun onLeave() {
        searchField?.setVisible(false)
    }

    override fun render(
        g: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        this.font = font
        val pad = VenusDimensions.CONTENT_PADDING
        val inner = contentBounds.inset(pad)

        if (selectedUuid != null) {
            searchField?.setVisible(false)
            renderDetail(g, font, inner, mouseX, mouseY)
            return
        }
        searchField?.setVisible(true)
        searchField?.render(g, mouseX, mouseY)

        renderFilters(g, font, inner)

        val list = this.list ?: return
        val players = filteredPlayers()

        if (!SessionState.sessionActive) {
            VenusEmptyState(Bounds(inner.x, inner.y + 40, inner.width, inner.height - 40)).run {
                message = "Authenticate to load players"
                render(g, font)
            }
            return
        }

        val scrollbar = scrollbarForList(list) { players.size }
        list.render(g, mouseX, mouseY, players.size, scrollbar, if (players.isEmpty()) "No players" else null) { index, rb, hovered ->
            val p = players[index]
            VenusPlayerRow(rb, p.displayName, p.uuid, p.online, p.operator)
                .render(g, font, hovered, false, showPlayerHeads())
        }
    }

    private fun renderFilters(
        g: GuiGraphics,
        font: Font,
        inner: Bounds,
    ) {
        val filterX = inner.x + inner.width / 2 + VenusSpacing.SM
        val filterY = inner.y
        var cx = filterX
        for (f in PlayerFilter.entries) {
            val w = font.width(f.label) + 16
            val active = f == filter
            VenusDraw.rect(g, cx, filterY, w, VenusDimensions.INPUT_HEIGHT, if (active) VenusTheme.ACTIVE else VenusTheme.RAISED)
            VenusDraw.border(g, cx, filterY, w, VenusDimensions.INPUT_HEIGHT, if (active) VenusTheme.ACCENT else VenusTheme.BORDER)
            VenusDraw.textCentered(
                g,
                font,
                f.label,
                Bounds(cx, filterY, w, VenusDimensions.INPUT_HEIGHT),
                if (active) VenusTheme.TEXT else VenusTheme.TEXT_MUTED,
                false,
            )
            cx += w + VenusSpacing.SM
        }
    }

    private fun filteredPlayers(): List<PlayerSummaryPacket> {
        val list = SessionState.latestPlayerList ?: return emptyList()
        val onlineUuids = list.onlinePlayers.mapTo(HashSet()) { it.uuid }
        val whitelistUuids = list.whitelistedPlayers.mapTo(HashSet()) { it.uuid }
        val all =
            list.onlinePlayers + list.whitelistedPlayers.filter { it.uuid !in onlineUuids } +
                list.blockedPlayers.filter { it.uuid !in onlineUuids && it.uuid !in whitelistUuids }
        val byFilter =
            when (filter) {
                PlayerFilter.ALL -> all
                PlayerFilter.ONLINE -> all.filter { it.online }
                PlayerFilter.OFFLINE -> all.filter { !it.online }
                PlayerFilter.BANNED -> list.blockedPlayers
            }
        return SearchFilter.apply(byFilter, query) { it.displayName }
    }

    private fun renderDetail(
        g: GuiGraphics,
        font: Font,
        inner: Bounds,
        mouseX: Int,
        mouseY: Int,
    ) {
        val uuid = selectedUuid ?: return
        val list = SessionState.latestPlayerList
        val summary =
            list?.onlinePlayers?.find { it.uuid == uuid }
                ?: list?.whitelistedPlayers?.find { it.uuid == uuid }
                ?: list?.blockedPlayers?.find { it.uuid == uuid }
        val detail = SessionState.latestPlayerDetail

        VenusDraw.rect(g, inner, VenusTheme.SURFACE)
        VenusDraw.border(g, inner, VenusTheme.BORDER)

        val headX = inner.x + 12
        val headY = inner.y + 12
        VenusPlayerHead(headX, headY, uuid, 32).render(g, showPlayerHeads())
        VenusDraw.text(g, font, summary?.displayName ?: detail?.displayName ?: "Unknown", headX + 40, headY + 4, VenusTheme.TEXT, false)
        VenusDraw.text(g, font, uuid, headX + 40, headY + 4 + font.lineHeight + 2, VenusTheme.TEXT_MUTED, false)
        if (summary?.operator == true || detail?.operator == true) {
            VenusDraw.rect(g, headX + 40 + font.width(summary?.displayName ?: "") + 8, headY + 6, 8, 8, VenusTheme.WARNING)
        }

        // Resolve pending action
        if (SessionState.latestPlayerActionResult?.requestId == pendingRequestId) {
            pendingAction = null
            pendingRequestId = null
        }

        if (detail == null || detail.uuid != uuid) {
            VenusEmptyState(Bounds(inner.x, inner.y + 60, inner.width, inner.height - 60)).run {
                message = "Loading details..."
                render(g, font)
            }
            return
        }

        renderDetailFields(g, font, inner, detail, mouseX, mouseY)
    }

    private fun renderDetailFields(
        g: GuiGraphics,
        font: Font,
        inner: Bounds,
        detail: dev.ilgax.venus.protocol.PlayerDetail,
        mouseX: Int,
        mouseY: Int,
    ) {
        val startY = inner.y + 60
        val lineH = font.lineHeight + 4
        var y = startY

        val fields =
            buildList {
                add("Status" to (if (detail.online) "Online" else "Offline"))
                add("Game Mode" to (detail.gameMode ?: "Unknown"))
                if (detail.online) {
                    add("Health" to "${String.format("%.1f", detail.health ?: 0.0)} / ${String.format("%.1f", detail.maxHealth ?: 20.0)}")
                    add("Food" to "${detail.foodLevel ?: 20}")
                    add("Level" to "${detail.level ?: 0}")
                    add("World" to (detail.world ?: "Unknown"))
                    if (detail.x != null && detail.y != null && detail.z != null) {
                        add("Position" to "${detail.x!!.toInt()}, ${detail.y!!.toInt()}, ${detail.z!!.toInt()}")
                    }
                }
                add("Whitelisted" to if (detail.whitelisted) "Yes" else "No")
                add("Banned" to if (detail.blocked) "Yes" else "No")
                add("Operator" to if (detail.operator) "Yes" else "No")
            }

        fields.forEach { (label, value) ->
            VenusDraw.text(g, font, label, inner.x + 12, y, VenusTheme.TEXT_MUTED, false)
            VenusDraw.textRight(g, font, value, inner.right - 12, y, VenusTheme.TEXT, false)
            y += lineH
        }

        renderActionButtons(g, font, inner, detail, y + 8, mouseX, mouseY)
    }

    private fun renderActionButtons(
        g: GuiGraphics,
        font: Font,
        inner: Bounds,
        detail: dev.ilgax.venus.protocol.PlayerDetail,
        startY: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val actions = availableActions(detail)
        val btnW = (inner.width - 24 - (actions.size - 1) * VenusSpacing.SM) / actions.size
        actionButtonBounds = emptyList()
        val boundsList = mutableListOf<Pair<Bounds, PlayerAction>>()
        actions.forEachIndexed { i, action ->
            val x = inner.x + 12 + i * (btnW + VenusSpacing.SM)
            val bounds = Bounds(x, startY, btnW, VenusDimensions.BUTTON_HEIGHT)
            boundsList.add(bounds to action)
            val isPending = action.id == pendingAction
            val hovered = bounds.contains(mouseX, mouseY) && !isPending
            val variant = if (action.danger) VenusTheme.DANGER_DIM else VenusTheme.RAISED
            val border = if (action.danger) VenusTheme.DANGER else VenusTheme.BORDER_BRIGHT
            VenusDraw.rect(g, bounds, if (hovered) VenusTheme.HOVER else variant)
            VenusDraw.border(g, bounds, if (hovered && action.danger) VenusTheme.DANGER else border)
            VenusDraw.textCentered(
                g,
                font,
                if (isPending) "..." else action.label,
                bounds,
                if (isPending) VenusTheme.TEXT_MUTED else VenusTheme.TEXT,
                false,
            )
        }
        actionButtonBounds = boundsList
    }

    private data class PlayerAction(
        val id: String,
        val label: String,
        val danger: Boolean = false,
    )

    private fun availableActions(detail: dev.ilgax.venus.protocol.PlayerDetail): List<PlayerAction> =
        buildList {
            if (detail.online) {
                add(PlayerAction("heal", "Heal"))
                add(PlayerAction("feed", "Feed"))
                add(PlayerAction("teleport_admin_to_player", "TP To"))
            }
            add(PlayerAction("set_whitelisted", if (detail.whitelisted) "Unwhitelist" else "Whitelist"))
            add(PlayerAction("set_blocked", if (detail.blocked) "Unban" else "Ban", danger = true))
        }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean {
        if (selectedUuid != null) return false
        val list = this.list ?: return false
        val players = filteredPlayers()
        if (list.bounds.contains(mouseX.toInt(), mouseY.toInt())) {
            list.scroll(-scrollY.toInt() * VenusDimensions.SCROLL_LINES, players.size)
            return true
        }
        return false
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        if (button != 0) return false
        val pad = VenusDimensions.CONTENT_PADDING
        val inner = contentBounds.inset(pad)

        // Filter clicks
        val filterX = inner.x + inner.width / 2 + VenusSpacing.SM
        var cx = filterX
        for (f in PlayerFilter.entries) {
            val w =
                (
                    this.font ?: net.minecraft.client.Minecraft
                        .getInstance()
                        .font
                ).width(f.label) + 16
            if (mouseX.toInt() in cx..(cx + w) && mouseY.toInt() in inner.y..(inner.y + VenusDimensions.INPUT_HEIGHT)) {
                filter = f
                return true
            }
            cx += w + VenusSpacing.SM
        }

        if (selectedUuid != null) {
            val uuid = selectedUuid!!
            val detail = SessionState.latestPlayerDetail
            if (detail != null && detail.uuid == uuid && pendingRequestId == null) {
                for ((bounds, action) in actionButtonBounds) {
                    if (bounds.contains(mouseX, mouseY)) {
                        val value =
                            when (action.id) {
                                "set_whitelisted" -> !detail.whitelisted
                                "set_blocked" -> !detail.blocked
                                "set_operator" -> !detail.operator
                                else -> null
                            }
                        pendingRequestId = sendPlayerAction(uuid, action.id, value)
                        pendingAction = action.id
                        return true
                    }
                }
            }
            return false
        }

        val list = this.list ?: return false
        val players = filteredPlayers()
        val idx = list.hitTest(mouseX.toInt(), mouseY.toInt(), players.size)
        if (idx >= 0) {
            selectedUuid = players[idx].uuid
            requestPlayerDetail(players[idx].uuid)
            return true
        }
        return false
    }
}
