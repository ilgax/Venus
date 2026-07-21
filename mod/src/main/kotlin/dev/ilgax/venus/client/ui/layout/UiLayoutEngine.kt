package dev.ilgax.venus.client.ui.layout

import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.profile.NavigationPlacement
import dev.ilgax.venus.client.ui.profile.UiLayout
import dev.ilgax.venus.client.ui.profile.UiLayoutMode
import dev.ilgax.venus.client.ui.profile.UiPlacement
import dev.ilgax.venus.client.ui.profile.UiShell
import dev.ilgax.venus.client.ui.profile.UiTheme
import dev.ilgax.venus.client.ui.profile.WindowAnchor

object UiLayoutEngine {
    fun shell(
        screenWidth: Int,
        screenHeight: Int,
        shell: UiShell,
        theme: UiTheme,
        compact: Boolean,
    ): UiShellGeometry {
        val margin = if (compact) minOf(shell.margin, 8) else shell.margin
        val availableWidth = (screenWidth - margin * 2).coerceAtLeast(MIN_WINDOW_WIDTH)
        val availableHeight = (screenHeight - margin * 2).coerceAtLeast(MIN_WINDOW_HEIGHT)
        val width = (availableWidth * shell.widthPercent).toInt().coerceIn(MIN_WINDOW_WIDTH, availableWidth)
        val height = (availableHeight * shell.heightPercent).toInt().coerceIn(MIN_WINDOW_HEIGHT, availableHeight)
        val window = anchoredBounds(screenWidth, screenHeight, width, height, margin, shell.anchor)
        val topHeight = if (shell.showTopBar) minOf(theme.topBarHeight, window.height / 4) else 0
        val body = Bounds(window.x, window.y + topHeight, window.width, window.height - topHeight)
        val navigationSize = minOf(theme.navigationSize, if (shell.navigationPlacement.vertical) body.width / 3 else body.height / 3)
        val navigation = navigationBounds(body, shell.navigationPlacement, navigationSize)
        val content = contentBounds(body, shell.navigationPlacement, navigationSize)
        val topBar = Bounds(window.x, window.y, window.width, topHeight)
        return UiShellGeometry(window, topBar, navigation, content)
    }

    fun modules(
        content: Bounds,
        layout: UiLayout,
        pageId: String,
        mode: UiLayoutMode,
        gap: Int,
        rowHeight: Int,
        scrollOffset: Int = 0,
    ): UiPageGeometry {
        val placements = layout.placements.filter { it.pageId == pageId && it.visible }
        val columnWidth = (content.width - gap * (mode.columns - 1)).coerceAtLeast(mode.columns) / mode.columns
        val bounds =
            placements.associate { placement ->
                placement.moduleId to placement.toBounds(content, columnWidth, rowHeight, gap, scrollOffset)
            }
        val rows = placements.maxOfOrNull { it.row + it.height } ?: 0
        val totalHeight = if (rows == 0) 0 else rows * rowHeight + (rows - 1) * gap
        return UiPageGeometry(bounds, totalHeight)
    }

    private fun UiPlacement.toBounds(
        content: Bounds,
        columnWidth: Int,
        rowHeight: Int,
        gap: Int,
        scrollOffset: Int,
    ): Bounds =
        Bounds(
            x = content.x + column * (columnWidth + gap),
            y = content.y + row * (rowHeight + gap) - scrollOffset,
            width = width * columnWidth + (width - 1) * gap,
            height = height * rowHeight + (height - 1) * gap,
        )

    private fun anchoredBounds(
        screenWidth: Int,
        screenHeight: Int,
        width: Int,
        height: Int,
        margin: Int,
        anchor: WindowAnchor,
    ): Bounds {
        val x =
            when (anchor) {
                WindowAnchor.TOP_LEFT, WindowAnchor.LEFT, WindowAnchor.BOTTOM_LEFT -> margin
                WindowAnchor.TOP, WindowAnchor.CENTER, WindowAnchor.BOTTOM -> (screenWidth - width) / 2
                WindowAnchor.TOP_RIGHT, WindowAnchor.RIGHT, WindowAnchor.BOTTOM_RIGHT -> screenWidth - width - margin
            }
        val y =
            when (anchor) {
                WindowAnchor.TOP_LEFT, WindowAnchor.TOP, WindowAnchor.TOP_RIGHT -> margin
                WindowAnchor.LEFT, WindowAnchor.CENTER, WindowAnchor.RIGHT -> (screenHeight - height) / 2
                WindowAnchor.BOTTOM_LEFT, WindowAnchor.BOTTOM, WindowAnchor.BOTTOM_RIGHT -> screenHeight - height - margin
            }
        return Bounds(x, y, width, height)
    }

    private fun navigationBounds(
        body: Bounds,
        placement: NavigationPlacement,
        size: Int,
    ): Bounds =
        when (placement) {
            NavigationPlacement.LEFT -> Bounds(body.x, body.y, size, body.height)
            NavigationPlacement.RIGHT -> Bounds(body.right - size, body.y, size, body.height)
            NavigationPlacement.TOP -> Bounds(body.x, body.y, body.width, size)
            NavigationPlacement.BOTTOM -> Bounds(body.x, body.bottom - size, body.width, size)
        }

    private fun contentBounds(
        body: Bounds,
        placement: NavigationPlacement,
        size: Int,
    ): Bounds =
        when (placement) {
            NavigationPlacement.LEFT -> Bounds(body.x + size, body.y, body.width - size, body.height)
            NavigationPlacement.RIGHT -> Bounds(body.x, body.y, body.width - size, body.height)
            NavigationPlacement.TOP -> Bounds(body.x, body.y + size, body.width, body.height - size)
            NavigationPlacement.BOTTOM -> Bounds(body.x, body.y, body.width, body.height - size)
        }

    private val NavigationPlacement.vertical: Boolean
        get() = this == NavigationPlacement.LEFT || this == NavigationPlacement.RIGHT

    private const val MIN_WINDOW_WIDTH = 320
    private const val MIN_WINDOW_HEIGHT = 220
}

data class UiShellGeometry(
    val window: Bounds,
    val topBar: Bounds,
    val navigation: Bounds,
    val content: Bounds,
)

data class UiPageGeometry(
    val modules: Map<String, Bounds>,
    val totalHeight: Int,
)
