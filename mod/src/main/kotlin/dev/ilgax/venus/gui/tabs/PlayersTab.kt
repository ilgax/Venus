package dev.ilgax.venus.gui.tabs

import dev.ilgax.venus.protocol.PlayerDetail
import dev.ilgax.venus.protocol.PlayerListPacket
import dev.ilgax.venus.protocol.PlayerSummaryPacket
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

enum class PlayerCategory(
    val displayName: String,
) {
    ONLINE("Online"),
    WHITELIST("Whitelist"),
    BLOCKED("Banned"),
}

data class PlayersTabHitResult(
    val categoryClicked: PlayerCategory? = null,
    val refreshClicked: Boolean = false,
    val backClicked: Boolean = false,
    val playerUuidClicked: String? = null,
    val listBounds: dev.ilgax.venus.gui.tabs.Rect? = null,
)

data class Rect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

object PlayersTab {
    fun playersForCategory(
        list: PlayerListPacket?,
        category: PlayerCategory,
    ): List<PlayerSummaryPacket> {
        if (list == null) return emptyList()
        return when (category) {
            PlayerCategory.ONLINE -> list.onlinePlayers
            PlayerCategory.WHITELIST -> list.whitelistedPlayers
            PlayerCategory.BLOCKED -> list.blockedPlayers
        }
    }

    fun hitTest(
        font: Font,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        list: PlayerListPacket?,
        activeCategory: PlayerCategory,
        selectedUuid: String?,
        scrollOffset: Int,
    ): PlayersTabHitResult {
        var hitResult = PlayersTabHitResult()

        val gap = 8
        val headerHeight = 28
        val isCompact = width < 500
        val detailWidth = if (isCompact) width else (width * 0.4).toInt()
        val listWidth = if (isCompact) width else width - detailWidth - gap

        val headerRect = Rect(x, y, width, headerHeight)

        if (selectedUuid != null) {
            val backWidth = font.width("Back") + 16
            val backRect = Rect(headerRect.x + 4, headerRect.y + 4, backWidth, 20)
            if (inside(mouseX, mouseY, backRect)) {
                hitResult = hitResult.copy(backClicked = true)
            }

            val refreshWidth = font.width("Refresh") + 16
            val refreshRect = Rect(headerRect.x + headerRect.width - refreshWidth - 4, headerRect.y + 4, refreshWidth, 20)
            if (inside(mouseX, mouseY, refreshRect) && SessionState.sessionActive) {
                hitResult = hitResult.copy(refreshClicked = true)
            }
            return hitResult
        }

        val pillText = if (list != null) "Players - ${list.onlineCount} / ${list.maxPlayers}" else "Players - ? / ?"
        val pillWidth = font.width(pillText) + 20
        var currentTabX = headerRect.x + pillWidth

        val refreshWidth = font.width("Refresh") + 16
        val availableTabSpace = headerRect.width - pillWidth - refreshWidth - 12
        var spaceUsed = 0

        for (category in PlayerCategory.entries) {
            val tabWidth = font.width(category.displayName) + 16
            // Always show ONLINE, otherwise check if there's enough space
            if (category != PlayerCategory.ONLINE && spaceUsed + tabWidth > availableTabSpace) {
                continue
            }

            val tabRect = Rect(currentTabX, headerRect.y + 4, tabWidth, 20)
            if (inside(mouseX, mouseY, tabRect)) {
                hitResult = hitResult.copy(categoryClicked = category)
            }
            currentTabX += tabWidth + 4
            spaceUsed += tabWidth + 4
        }

        val refreshRect = Rect(headerRect.x + headerRect.width - refreshWidth - 4, headerRect.y + 4, refreshWidth, 20)
        if (inside(mouseX, mouseY, refreshRect) && SessionState.sessionActive) {
            hitResult = hitResult.copy(refreshClicked = true)
        }

        val bodyY = y + headerHeight + gap
        val bodyHeight = height - headerHeight - gap
        val listRect = Rect(x, bodyY, listWidth, if (isCompact) bodyHeight / 2 else bodyHeight)
        hitResult = hitResult.copy(listBounds = listRect)

        if (SessionState.sessionActive && list != null && inside(mouseX, mouseY, listRect)) {
            val players = playersForCategory(list, activeCategory)
            val rowHeight = 24
            val visibleRows = (listRect.height - 4) / rowHeight
            val startIndex = scrollOffset.coerceIn(0, maxOf(0, players.size - visibleRows))
            val endIndex = minOf(players.size, startIndex + visibleRows + 1)

            for (i in startIndex until endIndex) {
                val rowY = listRect.y + 2 + (i - startIndex) * rowHeight
                val rowRect = Rect(listRect.x + 2, rowY, listRect.width - 4, rowHeight)
                if (inside(mouseX, mouseY, rowRect)) {
                    hitResult = hitResult.copy(playerUuidClicked = players[i].uuid)
                    break
                }
            }
        }

        return hitResult
    }

    fun render(
        guiGraphics: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        activeCategory: PlayerCategory,
        selectedUuid: String?,
        scrollOffset: Int,
    ) {
        val gap = 8
        val headerHeight = 28
        val isCompact = width < 500
        val detailWidth = if (isCompact) width else (width * 0.4).toInt()
        val listWidth = if (isCompact) width else width - detailWidth - gap

        val headerRect = Rect(x, y, width, headerHeight)

        // Header Render
        guiGraphics.fill(headerRect.x, headerRect.y, headerRect.x + headerRect.width, headerRect.y + headerRect.height, COLOR_CARD)
        guiGraphics.renderOutline(headerRect.x, headerRect.y, headerRect.width, headerRect.height, COLOR_BORDER)

        if (selectedUuid != null) {
            // Render Detail View Fullscreen
            val backWidth = font.width("Back") + 16
            val backRect = Rect(headerRect.x + 4, headerRect.y + 4, backWidth, 20)
            val backHovered = inside(mouseX, mouseY, backRect)
            val backBg = if (backHovered) COLOR_HOVER else COLOR_BUTTON
            guiGraphics.fill(backRect.x, backRect.y, backRect.x + backRect.width, backRect.y + backRect.height, backBg)
            guiGraphics.renderOutline(backRect.x, backRect.y, backRect.width, backRect.height, COLOR_BORDER)
            guiGraphics.drawString(font, "Back", backRect.x + 8, backRect.y + 6, COLOR_TEXT, false)

            val refreshWidth = font.width("Refresh") + 16
            val refreshRect = Rect(headerRect.x + headerRect.width - refreshWidth - 4, headerRect.y + 4, refreshWidth, 20)
            val refreshHovered = inside(mouseX, mouseY, refreshRect)
            val refreshBg =
                if (!SessionState.sessionActive) {
                    COLOR_DISABLED
                } else if (refreshHovered) {
                    COLOR_HOVER
                } else {
                    COLOR_BUTTON
                }
            guiGraphics.fill(refreshRect.x, refreshRect.y, refreshRect.x + refreshRect.width, refreshRect.y + refreshRect.height, refreshBg)
            guiGraphics.renderOutline(refreshRect.x, refreshRect.y, refreshRect.width, refreshRect.height, COLOR_BORDER)
            guiGraphics.drawString(
                font,
                "Refresh",
                refreshRect.x + 8,
                refreshRect.y + 6,
                if (SessionState.sessionActive) COLOR_TEXT else COLOR_MUTED,
                false,
            )

            // Draw player name and icon in header
            val list = SessionState.latestPlayerList
            val summary =
                list?.onlinePlayers?.find { it.uuid == selectedUuid }
                    ?: list?.whitelistedPlayers?.find { it.uuid == selectedUuid }
                    ?: list?.blockedPlayers?.find { it.uuid == selectedUuid }

            val detail = SessionState.latestPlayerDetail
            val displayName = summary?.displayName ?: detail?.displayName ?: "Unknown"
            val isOp = summary?.operator == true || detail?.operator == true

            var currentX = backRect.x + backRect.width + 12
            // Face
            guiGraphics.fill(currentX, headerRect.y + 6, currentX + 16, headerRect.y + 22, getFaceColor(selectedUuid))
            currentX += 24

            // Name
            guiGraphics.drawString(font, displayName, currentX, headerRect.y + 10, COLOR_TEXT, false)
            currentX += font.width(displayName) + 6

            // Crown
            if (isOp) {
                drawCrown(guiGraphics, currentX, headerRect.y + 6)
            }

            val bodyY = y + headerHeight + gap
            val bodyHeight = height - headerHeight - gap
            val detailRect = Rect(x, bodyY, width, bodyHeight)

            guiGraphics.fill(detailRect.x, detailRect.y, detailRect.x + detailRect.width, detailRect.y + detailRect.height, COLOR_CARD)
            guiGraphics.renderOutline(detailRect.x, detailRect.y, detailRect.width, detailRect.height, COLOR_BORDER)

            if (detail == null || detail.uuid != selectedUuid) {
                drawCenteredString(guiGraphics, font, "Loading player details...", detailRect)
            } else {
                renderDetailPane(guiGraphics, font, detailRect, detail)
            }
            return
        }

        // Pill
        val list = SessionState.latestPlayerList
        val pillText = if (list != null) "Players - ${list.onlineCount} / ${list.maxPlayers}" else "Players - ? / ?"
        guiGraphics.drawString(font, pillText, headerRect.x + 10, headerRect.y + 10, COLOR_TEXT, false)

        // Segmented Tabs
        val pillWidth = font.width(pillText) + 20
        var currentTabX = headerRect.x + pillWidth

        val refreshWidth = font.width("Refresh") + 16
        val availableTabSpace = headerRect.width - pillWidth - refreshWidth - 12
        var spaceUsed = 0

        for (category in PlayerCategory.entries) {
            val tabWidth = font.width(category.displayName) + 16
            if (category != PlayerCategory.ONLINE && spaceUsed + tabWidth > availableTabSpace) {
                continue
            }

            val tabRect = Rect(currentTabX, headerRect.y + 4, tabWidth, 20)

            val hovered = inside(mouseX, mouseY, tabRect)
            val selected = category == activeCategory

            val bgColor =
                if (selected) {
                    COLOR_ACTIVE_TAB
                } else if (hovered) {
                    COLOR_HOVER
                } else {
                    COLOR_CARD
                }
            guiGraphics.fill(tabRect.x, tabRect.y, tabRect.x + tabRect.width, tabRect.y + tabRect.height, bgColor)
            guiGraphics.renderOutline(tabRect.x, tabRect.y, tabRect.width, tabRect.height, COLOR_BORDER)

            guiGraphics.drawString(
                font,
                category.displayName,
                tabRect.x + 8,
                tabRect.y + 6,
                if (selected) COLOR_TEXT else COLOR_MUTED,
                false,
            )
            currentTabX += tabWidth + 4
            spaceUsed += tabWidth + 4
        }

        // Refresh Button
        val refreshRect = Rect(headerRect.x + headerRect.width - refreshWidth - 4, headerRect.y + 4, refreshWidth, 20)
        val refreshHovered = inside(mouseX, mouseY, refreshRect)
        val refreshBgColor =
            if (!SessionState.sessionActive) {
                COLOR_DISABLED
            } else if (refreshHovered) {
                COLOR_HOVER
            } else {
                COLOR_BUTTON
            }

        guiGraphics.fill(
            refreshRect.x,
            refreshRect.y,
            refreshRect.x + refreshRect.width,
            refreshRect.y + refreshRect.height,
            refreshBgColor,
        )
        guiGraphics.renderOutline(refreshRect.x, refreshRect.y, refreshRect.width, refreshRect.height, COLOR_BORDER)
        guiGraphics.drawString(
            font,
            "Refresh",
            refreshRect.x + 8,
            refreshRect.y + 6,
            if (SessionState.sessionActive) COLOR_TEXT else COLOR_MUTED,
            false,
        )

        // Body
        val bodyY = y + headerHeight + gap
        val bodyHeight = height - headerHeight - gap

        val listRect = Rect(x, bodyY, width, bodyHeight)

        // Render List
        guiGraphics.fill(listRect.x, listRect.y, listRect.x + listRect.width, listRect.y + listRect.height, COLOR_CARD)
        guiGraphics.renderOutline(listRect.x, listRect.y, listRect.width, listRect.height, COLOR_BORDER)

        if (!SessionState.sessionActive) {
            drawCenteredString(guiGraphics, font, "Authenticate to load players", listRect)
        } else if (list == null) {
            drawCenteredString(guiGraphics, font, "Refresh to load players", listRect)
        } else {
            val players = playersForCategory(list, activeCategory)
            if (players.isEmpty()) {
                drawCenteredString(guiGraphics, font, "No players", listRect)
            } else {
                val rowHeight = 24
                val visibleRows = (listRect.height - 4) / rowHeight
                val startIndex = scrollOffset.coerceIn(0, maxOf(0, players.size - visibleRows))
                val endIndex = minOf(players.size, startIndex + visibleRows + 1)

                guiGraphics.enableScissor(listRect.x, listRect.y, listRect.x + listRect.width, listRect.y + listRect.height)

                for (i in startIndex until endIndex) {
                    val player = players[i]
                    val rowY = listRect.y + 2 + (i - startIndex) * rowHeight
                    val rowRect = Rect(listRect.x + 2, rowY, listRect.width - 4, rowHeight)

                    val hovered = inside(mouseX, mouseY, rowRect) && inside(mouseX, mouseY, listRect)
                    val selected = player.uuid == selectedUuid

                    val bgColor =
                        if (selected) {
                            COLOR_ACTIVE_ROW
                        } else if (hovered) {
                            COLOR_HOVER
                        } else {
                            COLOR_CARD
                        }
                    guiGraphics.fill(rowRect.x, rowRect.y, rowRect.x + rowRect.width, rowRect.y + rowRect.height, bgColor)

                    guiGraphics.fill(rowRect.x + 4, rowRect.y + 4, rowRect.x + 20, rowRect.y + 20, getFaceColor(player.uuid))
                    val nameX = rowRect.x + 28
                    guiGraphics.drawString(font, player.displayName, nameX, rowRect.y + 8, COLOR_TEXT, false)
                    if (player.operator) {
                        drawCrown(guiGraphics, nameX + font.width(player.displayName) + 6, rowRect.y + 4)
                    }

                    if (player.online) {
                        val markerX = rowRect.x + rowRect.width - 16
                        guiGraphics.fill(markerX, rowRect.y + 8, markerX + 8, rowRect.y + 16, COLOR_ONLINE)
                    }
                }
                guiGraphics.disableScissor()
            }
        }
    }

    private fun renderDetailPane(
        guiGraphics: GuiGraphics,
        font: Font,
        rect: Rect,
        detail: PlayerDetail,
    ) {
        val pad = 12
        var currentY = rect.y + pad
        val maxY = rect.y + rect.height - pad

        guiGraphics.drawString(font, detail.uuid, rect.x + pad, currentY, COLOR_MUTED, false)
        currentY += 20

        val statusLines = mutableListOf<String>()
        if (detail.online) statusLines.add("Online") else statusLines.add("Offline")
        if (detail.operator) statusLines.add("Operator")
        if (detail.whitelisted) statusLines.add("Whitelisted")
        if (detail.blocked) statusLines.add("Blocked")

        if (currentY + 8 > maxY) return
        guiGraphics.drawString(font, "Status: ${statusLines.joinToString(", ")}", rect.x + pad, currentY, COLOR_TEXT, false)
        currentY += 20

        if (detail.online) {
            val gameMode = detail.gameMode
            if (gameMode != null) {
                if (currentY + 8 > maxY) return
                guiGraphics.drawString(font, "Game Mode: $gameMode", rect.x + pad, currentY, COLOR_TEXT, false)
                currentY += 16
            }
            val health = detail.health
            val maxHealth = detail.maxHealth
            if (health != null && maxHealth != null) {
                if (currentY + 8 > maxY) return
                guiGraphics.drawString(
                    font,
                    "Health: ${String.format("%.1f", health)} / ${String.format("%.1f", maxHealth)}",
                    rect.x + pad,
                    currentY,
                    COLOR_TEXT,
                    false,
                )
                currentY += 16
            }
            val foodLevel = detail.foodLevel
            if (foodLevel != null) {
                if (currentY + 8 > maxY) return
                guiGraphics.drawString(font, "Food: $foodLevel", rect.x + pad, currentY, COLOR_TEXT, false)
                currentY += 16
            }
            val level = detail.level
            val experienceProgress = detail.experienceProgress
            if (level != null && experienceProgress != null) {
                if (currentY + 8 > maxY) return
                guiGraphics.drawString(
                    font,
                    "Level: $level (${String.format("%.1f", experienceProgress * 100)}%)",
                    rect.x + pad,
                    currentY,
                    COLOR_TEXT,
                    false,
                )
                currentY += 16
            }
            val world = detail.world
            if (world != null) {
                if (currentY + 8 > maxY) return
                guiGraphics.drawString(font, "World: $world", rect.x + pad, currentY, COLOR_TEXT, false)
                currentY += 16
            }
            val x = detail.x
            val y = detail.y
            val z = detail.z
            if (x != null && y != null && z != null) {
                if (currentY + 8 > maxY) return
                guiGraphics.drawString(
                    font,
                    "Position: ${x.toInt()}, ${y.toInt()}, ${z.toInt()}",
                    rect.x + pad,
                    currentY,
                    COLOR_TEXT,
                    false,
                )
                currentY += 16
            }
        }
    }

    fun maxScrollOffset(
        listRectHeight: Int,
        itemCount: Int,
    ): Int {
        val rowHeight = 24
        val visibleRows = (listRectHeight - 4) / rowHeight
        return maxOf(0, itemCount - visibleRows)
    }

    private fun drawCenteredString(
        guiGraphics: GuiGraphics,
        font: Font,
        text: String,
        rect: Rect,
    ) {
        val width = font.width(text)
        guiGraphics.drawString(
            font,
            text,
            rect.x + (rect.width - width) / 2,
            rect.y + (rect.height - font.lineHeight) / 2,
            COLOR_MUTED,
            false,
        )
    }

    private fun inside(
        mouseX: Int,
        mouseY: Int,
        rect: Rect,
    ): Boolean = mouseX >= rect.x && mouseX < rect.x + rect.width && mouseY >= rect.y && mouseY < rect.y + rect.height

    private fun getFaceColor(uuid: String): Int {
        val hash = uuid.hashCode()
        val r = (hash and 0xFF)
        val g = ((hash shr 8) and 0xFF)
        val b = ((hash shr 16) and 0xFF)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun drawCrown(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
    ) {
        guiGraphics.blit(
            RenderPipelines.GUI_TEXTURED,
            CROWN_TEXTURE,
            x,
            y,
            0F,
            0F,
            CROWN_RENDER_SIZE,
            CROWN_RENDER_SIZE,
            CROWN_TEXTURE_SIZE,
            CROWN_TEXTURE_SIZE,
            CROWN_TEXTURE_SIZE,
            CROWN_TEXTURE_SIZE,
        )
    }

    private const val COLOR_CARD = 0xFF101216.toInt()
    private const val COLOR_BORDER = 0xFF2A2E36.toInt()
    private const val COLOR_TEXT = 0xFFF4F7FA.toInt()
    private const val COLOR_MUTED = 0xFFA1A7B3.toInt()
    private const val COLOR_ACTIVE_TAB = 0xFF2F6F85.toInt()
    private const val COLOR_BUTTON = 0xFF222B35.toInt()
    private const val COLOR_HOVER = 0xFF334150.toInt()
    private const val COLOR_DISABLED = 0xFF151A20.toInt()
    private const val COLOR_ACTIVE_ROW = 0xFF1E2A38.toInt()
    private const val COLOR_ONLINE = 0xFF10D39E.toInt()
    private const val COLOR_OP = 0xFFFFC107.toInt()
    private const val COLOR_OK = 0xFF10D39E.toInt()
    private const val COLOR_WHITELIST = 0xFFFFFFFF.toInt()
    private const val COLOR_BAD = 0xFFFF4D64.toInt()

    private const val CROWN_RENDER_SIZE = 16
    private const val CROWN_TEXTURE_SIZE = 64
    private val CROWN_TEXTURE = Identifier.fromNamespaceAndPath("venus", "textures/gui/crown.png")
}
