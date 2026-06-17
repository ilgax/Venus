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

data class PlayerActionClick(
    val action: String,
    val value: Any? = null,
)

data class PlayersTabHitResult(
    val categoryClicked: PlayerCategory? = null,
    val refreshClicked: Boolean = false,
    val backClicked: Boolean = false,
    val playerUuidClicked: String? = null,
    val listBounds: Rect? = null,
    val playerActionClicked: PlayerActionClick? = null,
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
        pendingPlayerAction: String? = null,
    ): PlayersTabHitResult {
        var hitResult = PlayersTabHitResult()

        val gap = 8
        val headerHeight = 28
        val isCompact = width < 500
        val detailWidth = if (isCompact) width else (width * 0.4).toInt()
        val listWidth = if (isCompact) width else width - detailWidth - gap

        val headerRect = Rect(x, y, width, headerHeight)

        if (selectedUuid != null) {
            val bodyY = y + headerHeight + gap
            val bodyHeight = height - headerHeight - gap
            val detailRect = Rect(x, bodyY, width, bodyHeight)
            val detailContentRect = detailContentRect(detailRect)
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

            val detail = SessionState.latestPlayerDetail
            if (detail != null && detail.uuid == selectedUuid) {
                val actionClick = hitTestDetailPane(mouseX, mouseY, detailContentRect, detail, pendingPlayerAction)
                if (actionClick != null) {
                    hitResult = hitResult.copy(playerActionClicked = actionClick)
                }
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
        pendingPlayerAction: String? = null,
    ) {
        val gap = 8
        val headerHeight = 28

        val headerRect = Rect(x, y, width, headerHeight)

        if (selectedUuid != null) {
            // Render Detail View Fullscreen
            val bodyY = y + headerHeight + gap
            val bodyHeight = height - headerHeight - gap
            val detailRect = Rect(x, bodyY, width, bodyHeight)
            val detailContentRect = detailContentRect(detailRect)

            guiGraphics.fill(
                headerRect.x,
                headerRect.y,
                headerRect.x + headerRect.width,
                headerRect.y + headerRect.height,
                COLOR_CARD,
            )
            guiGraphics.renderOutline(
                headerRect.x,
                headerRect.y,
                headerRect.width,
                headerRect.height,
                COLOR_BORDER,
            )

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
                currentX += 20
            }

            guiGraphics.drawString(font, selectedUuid, currentX, headerRect.y + 10, COLOR_DARK_MUTED, false)

            guiGraphics.fill(
                detailRect.x,
                detailRect.y,
                detailRect.x + detailRect.width,
                detailRect.y + detailRect.height,
                COLOR_CARD,
            )
            guiGraphics.renderOutline(detailRect.x, detailRect.y, detailRect.width, detailRect.height, COLOR_BORDER)

            if (detail == null || detail.uuid != selectedUuid) {
                drawCenteredString(guiGraphics, font, "Loading player details...", detailRect)
            } else {
                renderDetailPane(guiGraphics, font, detailContentRect, detail, mouseX, mouseY, pendingPlayerAction)
            }
            return
        }

        // Header Render
        guiGraphics.fill(headerRect.x, headerRect.y, headerRect.x + headerRect.width, headerRect.y + headerRect.height, COLOR_CARD)
        guiGraphics.renderOutline(headerRect.x, headerRect.y, headerRect.width, headerRect.height, COLOR_BORDER)

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

                    val bgColor =
                        if (hovered) {
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

    private fun detailContentRect(rect: Rect): Rect =
        Rect(
            rect.x + DETAIL_CONTENT_PADDING,
            rect.y + DETAIL_CONTENT_PADDING,
            maxOf(0, rect.width - DETAIL_CONTENT_PADDING * 2),
            maxOf(0, rect.height - DETAIL_CONTENT_PADDING * 2),
        )

    private fun detailLayout(rect: Rect): DetailLayout {
        val contentX = rect.x
        val contentY = rect.y
        val contentWidth = rect.width
        val contentHeight = rect.height

        val rightColWidth = maxOf(DETAIL_RIGHT_COLUMN_MIN_WIDTH, (contentWidth * DETAIL_RIGHT_COLUMN_PERCENT) / 100)
        val leftAreaWidth = maxOf(0, contentWidth - DETAIL_SECTION_GAP - rightColWidth)
        val topRowHeight = minOf(DETAIL_TOP_ROW_MAX_HEIGHT, maxOf(DETAIL_TOP_ROW_MIN_HEIGHT, contentHeight / 4))
        val lowerRowY = contentY + topRowHeight + DETAIL_SECTION_GAP
        val lowerRowHeight = maxOf(MIN_PLACEHOLDER_SECTION_HEIGHT, contentHeight - topRowHeight - DETAIL_SECTION_GAP)
        val leftTopWidth = maxOf(0, leftAreaWidth - DETAIL_SECTION_GAP)
        val healthWidth = (leftTopWidth * DETAIL_HEALTH_PERCENT_OF_LEFT_TOP) / 100
        val gameModeWidth = leftTopWidth - healthWidth
        val rightColX = contentX + leftAreaWidth + DETAIL_SECTION_GAP
        val controlHeight =
            minOf(
                maxOf(CONTROL_SECTION_HEIGHT, topRowHeight + lowerRowHeight / 3),
                maxOf(CONTROL_SECTION_HEIGHT, contentHeight - DETAIL_SECTION_GAP - INFORMATION_SECTION_MIN_HEIGHT),
            )
        val infoY = contentY + controlHeight + DETAIL_SECTION_GAP
        val infoHeight = maxOf(INFORMATION_SECTION_MIN_HEIGHT, contentY + contentHeight - infoY)

        return DetailLayout(
            healthRect = Rect(contentX, contentY, healthWidth, topRowHeight),
            gameModeRect = Rect(contentX + healthWidth + DETAIL_SECTION_GAP, contentY, gameModeWidth, topRowHeight),
            controlRect = Rect(rightColX, contentY, rightColWidth, controlHeight),
            inventoryRect = Rect(contentX, lowerRowY, leftAreaWidth, lowerRowHeight),
            infoRect = Rect(rightColX, infoY, rightColWidth, infoHeight),
        )
    }

    private data class DetailLayout(
        val healthRect: Rect,
        val gameModeRect: Rect,
        val controlRect: Rect,
        val inventoryRect: Rect,
        val infoRect: Rect,
    )

    private fun healthActionRects(
        x: Int,
        y: Int,
        width: Int,
    ): Map<String, Rect> {
        val btnWidth = (width - 16) / 3
        return mapOf(
            "heal" to Rect(x, y, btnWidth, 20),
            "feed" to Rect(x + btnWidth + 8, y, btnWidth, 20),
            "kill" to Rect(x + 2 * btnWidth + 16, y, btnWidth, 20),
        )
    }

    private fun controlActionRects(
        x: Int,
        y: Int,
        width: Int,
    ): Map<String, Rect> =
        mapOf(
            "set_whitelisted" to Rect(x, y, width, 20),
            "set_blocked" to Rect(x, y + 24, width, 20),
            "set_operator" to Rect(x, y + 48, width, 20),
        )

    private fun gameModeRects(
        x: Int,
        y: Int,
        width: Int,
    ): Map<String, Rect> {
        val btnWidth = (width - 24) / 4
        return mapOf(
            "survival" to Rect(x, y, btnWidth, 20),
            "creative" to Rect(x + btnWidth + 8, y, btnWidth, 20),
            "adventure" to Rect(x + 2 * btnWidth + 16, y, btnWidth, 20),
            "spectator" to Rect(x + 3 * btnWidth + 24, y, btnWidth, 20),
        )
    }

    private fun teleportRects(
        x: Int,
        y: Int,
        width: Int,
    ): Map<String, Rect> {
        val btnWidth = (width - 8) / 2
        return mapOf(
            "teleport_admin_to_player" to Rect(x, y, btnWidth, 20),
            "teleport_player_to_admin" to Rect(x + btnWidth + 8, y, btnWidth, 20),
        )
    }

    private fun hitTestDetailPane(
        mouseX: Int,
        mouseY: Int,
        rect: Rect,
        detail: PlayerDetail,
        pendingPlayerAction: String?,
    ): PlayerActionClick? {
        if (pendingPlayerAction != null) return null
        val layout = detailLayout(rect)

        if (detail.online) {
            val healthContentX = layout.healthRect.x + SECTION_PADDING
            var healthContentY = layout.healthRect.y + SECTION_PADDING + 16
            healthContentY += 28
            val actions = healthActionRects(healthContentX, healthContentY, layout.healthRect.width - SECTION_PADDING * 2)
            for ((action, btnRect) in actions) {
                if (inside(mouseX, mouseY, btnRect)) return PlayerActionClick(action)
            }

            val gameModeContentX = layout.gameModeRect.x + SECTION_PADDING
            val gameModeContentY = layout.gameModeRect.y + SECTION_PADDING + 32
            val gmActions = gameModeRects(gameModeContentX, gameModeContentY, layout.gameModeRect.width - SECTION_PADDING * 2)
            for ((mode, btnRect) in gmActions) {
                if (inside(mouseX, mouseY, btnRect)) return PlayerActionClick("set_game_mode", mode)
            }
        }

        val controlContentX = layout.controlRect.x + SECTION_PADDING
        val controlContentY = layout.controlRect.y + SECTION_PADDING + 16
        val controls = controlActionRects(controlContentX, controlContentY, layout.controlRect.width - SECTION_PADDING * 2)
        for ((action, btnRect) in controls) {
            if (inside(mouseX, mouseY, btnRect)) {
                val value =
                    when (action) {
                        "set_whitelisted" -> !detail.whitelisted
                        "set_blocked" -> !detail.blocked
                        "set_operator" -> !detail.operator
                        else -> false
                    }
                return PlayerActionClick(action, value)
            }
        }

        if (detail.online) {
            val infoContentX = layout.infoRect.x + SECTION_PADDING
            val infoContentY = layout.infoRect.y + SECTION_PADDING + 44
            val tpActions = teleportRects(infoContentX, infoContentY, layout.infoRect.width - SECTION_PADDING * 2)
            for ((action, btnRect) in tpActions) {
                if (inside(mouseX, mouseY, btnRect)) return PlayerActionClick(action)
            }
        }

        return null
    }

    private fun renderDetailPane(
        guiGraphics: GuiGraphics,
        font: Font,
        rect: Rect,
        detail: PlayerDetail,
        mouseX: Int,
        mouseY: Int,
        pendingPlayerAction: String?,
    ) {
        val layout = detailLayout(rect)

        renderSectionBackground(guiGraphics, layout.healthRect)
        renderSectionBackground(guiGraphics, layout.gameModeRect)
        renderSectionBackground(guiGraphics, layout.controlRect)
        renderSectionBackground(guiGraphics, layout.infoRect)

        val healthContentX = layout.healthRect.x + SECTION_PADDING
        var healthContentY = layout.healthRect.y + SECTION_PADDING
        guiGraphics.drawString(font, "HP and XP", healthContentX, healthContentY, COLOR_TEXT, false)
        healthContentY += 16

        val gameModeContentX = layout.gameModeRect.x + SECTION_PADDING
        var gameModeContentY = layout.gameModeRect.y + SECTION_PADDING
        guiGraphics.drawString(font, "Game Mode", gameModeContentX, gameModeContentY, COLOR_TEXT, false)
        gameModeContentY += 16

        if (detail.online) {
            val health = detail.health ?: 0.0
            val maxHealth = detail.maxHealth ?: 20.0
            val food = detail.foodLevel ?: 20
            val level = detail.level ?: 0
            val exp = (detail.experienceProgress ?: 0f) * 100
            val healthText = "HP: ${String.format("%.1f", health)} / ${String.format("%.1f", maxHealth)}"

            guiGraphics.drawString(
                font,
                healthText,
                healthContentX,
                healthContentY,
                COLOR_MUTED,
                false,
            )
            val hungerX = healthContentX + font.width(healthText) + 6
            guiGraphics.drawString(font, "Hunger: $food", hungerX, healthContentY, COLOR_MUTED, false)
            healthContentY += 12
            guiGraphics.drawString(font, "Lvl: $level (${String.format("%.1f", exp)}%)", healthContentX, healthContentY, COLOR_MUTED, false)
            healthContentY += 16

            val actions = healthActionRects(healthContentX, healthContentY, layout.healthRect.width - SECTION_PADDING * 2)
            for ((action, btnRect) in actions) {
                val isPending = action == pendingPlayerAction
                val hovered = inside(mouseX, mouseY, btnRect) && !isPending
                val bg =
                    if (isPending) {
                        COLOR_DISABLED
                    } else if (hovered) {
                        COLOR_HOVER
                    } else {
                        COLOR_BUTTON
                    }
                val label = if (isPending) "Working..." else action.replaceFirstChar { it.uppercase() }

                guiGraphics.fill(btnRect.x, btnRect.y, btnRect.x + btnRect.width, btnRect.y + btnRect.height, bg)
                guiGraphics.renderOutline(btnRect.x, btnRect.y, btnRect.width, btnRect.height, COLOR_BORDER)
                drawCenteredString(guiGraphics, font, label, btnRect, if (isPending) COLOR_MUTED else COLOR_TEXT)
            }

            guiGraphics.drawString(font, detail.gameMode ?: "Unknown", gameModeContentX, gameModeContentY, COLOR_MUTED, false)
            gameModeContentY += 16
            val gmActions = gameModeRects(gameModeContentX, gameModeContentY, layout.gameModeRect.width - SECTION_PADDING * 2)
            for ((mode, btnRect) in gmActions) {
                val isPending = "set_game_mode" == pendingPlayerAction
                val hovered = inside(mouseX, mouseY, btnRect) && !isPending
                val active = detail.gameMode == mode
                val bg =
                    if (isPending) {
                        COLOR_DISABLED
                    } else if (active) {
                        COLOR_ACTIVE_TAB
                    } else if (hovered) {
                        COLOR_HOVER
                    } else {
                        COLOR_BUTTON
                    }

                guiGraphics.fill(btnRect.x, btnRect.y, btnRect.x + btnRect.width, btnRect.y + btnRect.height, bg)
                guiGraphics.renderOutline(btnRect.x, btnRect.y, btnRect.width, btnRect.height, COLOR_BORDER)
                val shortMode =
                    when (mode) {
                        "survival" -> "S"
                        "creative" -> "C"
                        "adventure" -> "A"
                        "spectator" -> "SP"
                        else -> "?"
                    }
                drawCenteredString(guiGraphics, font, shortMode, btnRect, if (isPending) COLOR_MUTED else COLOR_TEXT)
            }
        } else {
            guiGraphics.drawString(font, "Player is offline", healthContentX, healthContentY, COLOR_MUTED, false)
        }

        val controlContentX = layout.controlRect.x + SECTION_PADDING
        var controlContentY = layout.controlRect.y + SECTION_PADDING
        guiGraphics.drawString(font, "Control", controlContentX, controlContentY, COLOR_TEXT, false)
        controlContentY += 16

        val controls = controlActionRects(controlContentX, controlContentY, layout.controlRect.width - SECTION_PADDING * 2)
        for ((action, btnRect) in controls) {
            val isPending = action == pendingPlayerAction
            val hovered = inside(mouseX, mouseY, btnRect) && !isPending
            val bg = if (hovered) COLOR_HOVER else COLOR_CARD
            val label =
                when (action) {
                    "set_whitelisted" -> "Whitelisted"
                    "set_blocked" -> "Banned"
                    "set_operator" -> "Operator"
                    else -> action
                }
            val isChecked =
                when (action) {
                    "set_whitelisted" -> detail.whitelisted
                    "set_blocked" -> detail.blocked
                    "set_operator" -> detail.operator
                    else -> false
                }

            if (hovered) {
                guiGraphics.fill(btnRect.x, btnRect.y, btnRect.x + btnRect.width, btnRect.y + btnRect.height, bg)
            }

            val boxRect = Rect(btnRect.x, btnRect.y + 2, 16, 16)
            guiGraphics.fill(boxRect.x, boxRect.y, boxRect.x + boxRect.width, boxRect.y + boxRect.height, COLOR_BUTTON)
            guiGraphics.renderOutline(boxRect.x, boxRect.y, boxRect.width, boxRect.height, COLOR_BORDER)
            if (isPending) {
                drawCenteredString(guiGraphics, font, "-", boxRect, COLOR_MUTED)
            } else if (isChecked) {
                drawCenteredString(guiGraphics, font, "Y", boxRect, COLOR_OK)
            } else {
                drawCenteredString(guiGraphics, font, "N", boxRect, COLOR_BAD)
            }

            guiGraphics.drawString(
                font,
                label,
                boxRect.x + 24,
                btnRect.y + 6,
                if (isPending) COLOR_MUTED else COLOR_TEXT,
                false,
            )
        }

        val infoContentX = layout.infoRect.x + SECTION_PADDING
        var currInfoY = layout.infoRect.y + SECTION_PADDING
        guiGraphics.drawString(font, "Information", infoContentX, currInfoY, COLOR_TEXT, false)
        currInfoY += 16

        if (detail.online) {
            val world = detail.world ?: "Unknown"
            val dx = detail.x
            val dy = detail.y
            val dz = detail.z
            val pos =
                if (dx != null && dy != null && dz != null) {
                    "${dx.toInt()}, ${dy.toInt()}, ${dz.toInt()}"
                } else {
                    "Unknown"
                }
            guiGraphics.drawString(font, "World: $world", infoContentX, currInfoY, COLOR_MUTED, false)
            currInfoY += 12
            guiGraphics.drawString(font, "Pos: $pos", infoContentX, currInfoY, COLOR_MUTED, false)
            currInfoY += 16

            val tpActions = teleportRects(infoContentX, currInfoY, layout.infoRect.width - SECTION_PADDING * 2)
            for ((action, btnRect) in tpActions) {
                val isPending = action == pendingPlayerAction
                val hovered = inside(mouseX, mouseY, btnRect) && !isPending
                val bg =
                    if (isPending) {
                        COLOR_DISABLED
                    } else if (hovered) {
                        COLOR_HOVER
                    } else {
                        COLOR_BUTTON
                    }
                val label = if (action == "teleport_admin_to_player") "TP To" else "Bring"

                guiGraphics.fill(btnRect.x, btnRect.y, btnRect.x + btnRect.width, btnRect.y + btnRect.height, bg)
                guiGraphics.renderOutline(btnRect.x, btnRect.y, btnRect.width, btnRect.height, COLOR_BORDER)
                drawCenteredString(guiGraphics, font, if (isPending) "..." else label, btnRect, if (isPending) COLOR_MUTED else COLOR_TEXT)
            }
        } else {
            guiGraphics.drawString(font, "Position unavailable", infoContentX, currInfoY, COLOR_MUTED, false)
        }
    }

    private fun renderSectionBackground(
        guiGraphics: GuiGraphics,
        rect: Rect,
    ) {
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, COLOR_CARD)
        guiGraphics.renderOutline(rect.x, rect.y, rect.width, rect.height, COLOR_BORDER)
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
        color: Int = COLOR_MUTED,
    ) {
        val width = font.width(text)
        guiGraphics.drawString(
            font,
            text,
            rect.x + (rect.width - width) / 2,
            rect.y + (rect.height - font.lineHeight) / 2,
            color,
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
    private const val COLOR_DARK_MUTED = 0xFF4A5562.toInt()
    private const val COLOR_ACTIVE_TAB = 0xFF2F6F85.toInt()
    private const val COLOR_BUTTON = 0xFF222B35.toInt()
    private const val COLOR_HOVER = 0xFF334150.toInt()
    private const val COLOR_DISABLED = 0xFF151A20.toInt()
    private const val COLOR_ONLINE = 0xFF10D39E.toInt()
    private const val COLOR_OK = 0xFF10D39E.toInt()
    private const val COLOR_BAD = 0xFFFF4D64.toInt()

    private const val DETAIL_CONTENT_PADDING = 12
    private const val DETAIL_SECTION_GAP = 12
    private const val DETAIL_RIGHT_COLUMN_MIN_WIDTH = 160
    private const val DETAIL_RIGHT_COLUMN_PERCENT = 28
    private const val DETAIL_HEALTH_PERCENT_OF_LEFT_TOP = 58
    private const val DETAIL_TOP_ROW_MIN_HEIGHT = 80
    private const val DETAIL_TOP_ROW_MAX_HEIGHT = 96
    private const val SECTION_PADDING = 8
    private const val MIN_PLACEHOLDER_SECTION_HEIGHT = 32
    private const val CONTROL_SECTION_HEIGHT = 92
    private const val INFORMATION_SECTION_MIN_HEIGHT = 88
    private const val CROWN_RENDER_SIZE = 16
    private const val CROWN_TEXTURE_SIZE = 64
    private val CROWN_TEXTURE = Identifier.fromNamespaceAndPath("venus", "textures/gui/crown.png")
}
