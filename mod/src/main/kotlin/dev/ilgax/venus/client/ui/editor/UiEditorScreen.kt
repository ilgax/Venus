package dev.ilgax.venus.client.ui.editor

import dev.ilgax.venus.client.ui.ModularVenusScreen
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.UiThemeRuntime
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.module.UiModuleRegistry
import dev.ilgax.venus.client.ui.module.UiScreenServices
import dev.ilgax.venus.client.ui.profile.FactoryUiProfile
import dev.ilgax.venus.client.ui.profile.NavigationPlacement
import dev.ilgax.venus.client.ui.profile.UiLayoutMode
import dev.ilgax.venus.client.ui.profile.UiModuleType
import dev.ilgax.venus.client.ui.profile.UiProfile
import dev.ilgax.venus.client.ui.profile.UiTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import dev.ilgax.venus.client.ui.widget.VenusTextField
import dev.ilgax.venus.keybind.PanelKeybind
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class UiEditorScreen(
    private val services: UiScreenServices,
    source: UiProfile,
    private val serverAddress: String?,
) : Screen(Component.literal("Venus UI Editor")) {
    private var session = UiEditorSession(source, services.profiles)
    private var mode = UiLayoutMode.NORMAL
    private var selectedPageId =
        session.draft.pages
            .minByOrNull { it.order }!!
            .id
    private var selectedModuleId: String? = null
    private var paletteType = UiModuleType.METRIC_CARD
    private var themeToken = ThemeToken.ACCENT
    private var numericToken = NumericToken.SPACING
    private var status = "Edit draft • Apply to save"
    private var dragging = false
    private var resizing = false
    private lateinit var profileName: VenusTextField
    private lateinit var titleField: VenusTextField
    private lateinit var valueField: VenusTextField

    override fun init() {
        UiThemeRuntime.activate(session.draft.theme)
        profileName = VenusTextField(font, width = 170, placeholder = "Profile name")
        titleField = VenusTextField(font, width = 170, placeholder = "Page/module title")
        valueField = VenusTextField(font, width = 170, placeholder = "AARRGGBB")
        profileName.layout(Bounds(width - RIGHT_WIDTH + 10, 50, RIGHT_WIDTH - 20, 20))
        titleField.layout(Bounds(width - RIGHT_WIDTH + 10, 92, RIGHT_WIDTH - 20, 20))
        valueField.layout(Bounds(width - RIGHT_WIDTH + 10, 216, RIGHT_WIDTH - 20, 20))
        profileName.setValue(session.draft.name)
        syncSelectionFields()
        addRenderableWidget(profileName.editBox())
        addRenderableWidget(titleField.editBox())
        addRenderableWidget(valueField.editBox())
    }

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        graphics.fill(0, 0, width, height, VenusTheme.BACKGROUND)
        renderToolbar(graphics, mouseX, mouseY)
        renderPages(graphics, mouseX, mouseY)
        renderCanvas(graphics, mouseX, mouseY)
        renderInspector(graphics, mouseX, mouseY)
        profileName.renderBackground(graphics, mouseX, mouseY)
        titleField.renderBackground(graphics, mouseX, mouseY)
        valueField.renderBackground(graphics, mouseX, mouseY)
        super.render(graphics, mouseX, mouseY, partialTick)
        VenusDraw.text(graphics, font, status, 10, height - 14, VenusTheme.TEXT_MUTED, false)
    }

    private fun renderToolbar(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        VenusDraw.rect(graphics, Bounds(0, 0, width, TOOLBAR_HEIGHT), VenusTheme.TOP_BAR)
        toolbarButtons().forEach { (label, bounds) -> drawButton(graphics, label, bounds, mouseX, mouseY) }
    }

    private fun renderPages(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        val panel = Bounds(0, TOOLBAR_HEIGHT, LEFT_WIDTH, height - TOOLBAR_HEIGHT - 20)
        VenusDraw.rect(graphics, panel, VenusTheme.SIDEBAR)
        VenusDraw.text(graphics, font, "PAGES", 10, TOOLBAR_HEIGHT + 8, VenusTheme.TEXT_MUTED, false)
        session.draft.pages.sortedBy { it.order }.forEachIndexed { index, page ->
            val bounds = pageBounds(index)
            if (page.id == selectedPageId || bounds.contains(mouseX, mouseY)) {
                VenusDraw.rect(graphics, bounds, if (page.id == selectedPageId) VenusTheme.ACTIVE else VenusTheme.HOVER)
            }
            VenusDraw.textTruncated(graphics, font, page.title, bounds.x + 6, bounds.y + 6, bounds.width - 12, VenusTheme.TEXT, false)
        }
        drawButton(graphics, "+ Page", addPageBounds(), mouseX, mouseY)
        drawButton(graphics, "Delete Page", deletePageBounds(), mouseX, mouseY, danger = true)
        VenusDraw.text(graphics, font, "MODULE PALETTE", 10, paletteTypeBounds().y - 14, VenusTheme.TEXT_MUTED, false)
        drawButton(graphics, paletteType.name.replace('_', ' '), paletteTypeBounds(), mouseX, mouseY)
        drawButton(graphics, "+ Module", addModuleBounds(), mouseX, mouseY)
    }

    private fun renderCanvas(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        val canvas = canvasBounds()
        VenusDraw.rect(graphics, canvas, VenusTheme.WINDOW)
        VenusDraw.border(graphics, canvas, VenusTheme.BORDER)
        val columns = mode.columns
        val columnWidth = canvas.width.toDouble() / columns
        for (column in 1 until columns) {
            val x = canvas.x + (column * columnWidth).toInt()
            VenusDraw.vSeparator(graphics, x, canvas.y, canvas.height, VenusTheme.BORDER)
        }
        var y = canvas.y + GRID_ROW_HEIGHT
        while (y < canvas.bottom) {
            VenusDraw.hSeparator(graphics, canvas.x, y, canvas.width, VenusTheme.BORDER)
            y += GRID_ROW_HEIGHT
        }
        currentPlacements().filter { it.pageId == selectedPageId && it.visible }.forEach { placement ->
            val bounds = placementBounds(placement.moduleId) ?: return@forEach
            val selected = placement.moduleId == selectedModuleId
            VenusDraw.rect(graphics, bounds, if (selected) VenusTheme.ACTIVE else VenusTheme.SURFACE)
            VenusDraw.border(graphics, bounds, if (selected) VenusTheme.ACCENT else VenusTheme.BORDER)
            val module = session.draft.modules.first { it.id == placement.moduleId }
            VenusDraw.textTruncated(
                graphics,
                font,
                module.title ?: UiModuleRegistry.builtIn.descriptor(module.type).displayName,
                bounds.x + 5,
                bounds.y + 5,
                bounds.width - 10,
                VenusTheme.TEXT,
                false,
            )
            if (selected) VenusDraw.rect(graphics, bounds.right - 7, bounds.bottom - 7, 6, 6, VenusTheme.ACCENT)
        }
    }

    private fun renderInspector(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        val panel = Bounds(width - RIGHT_WIDTH, TOOLBAR_HEIGHT, RIGHT_WIDTH, height - TOOLBAR_HEIGHT)
        VenusDraw.rect(graphics, panel, VenusTheme.SIDEBAR)
        VenusDraw.text(graphics, font, "PROFILE", panel.x + 10, panel.y + 8, VenusTheme.TEXT_MUTED, false)
        drawButton(graphics, "Rename Selected", renameBounds(), mouseX, mouseY)
        drawButton(graphics, "Delete Module", deleteModuleBounds(), mouseX, mouseY, danger = true)
        VenusDraw.text(graphics, font, "THEME", panel.x + 10, 176, VenusTheme.TEXT_MUTED, false)
        drawButton(graphics, themeToken.name.replace('_', ' '), themeTokenBounds(), mouseX, mouseY)
        drawButton(graphics, "Set Color", setColorBounds(), mouseX, mouseY)
        drawButton(graphics, numericToken.label(session.draft.theme), numericTokenBounds(), mouseX, mouseY)
        drawButton(graphics, "-", numericMinusBounds(), mouseX, mouseY)
        drawButton(graphics, "+", numericPlusBounds(), mouseX, mouseY)
        VenusDraw.text(graphics, font, "SHELL", panel.x + 10, 300, VenusTheme.TEXT_MUTED, false)
        drawButton(graphics, "Nav: ${session.draft.shell.navigationPlacement}", navigationBounds(), mouseX, mouseY)
        drawButton(graphics, "Top bar: ${if (session.draft.shell.showTopBar) "On" else "Off"}", topBarBounds(), mouseX, mouseY)
        drawButton(graphics, "Opacity -", opacityMinusBounds(), mouseX, mouseY)
        drawButton(graphics, "Opacity +", opacityPlusBounds(), mouseX, mouseY)
        drawButton(graphics, "Metric / option", moduleOptionBounds(), mouseX, mouseY)
        drawButton(graphics, "Duplicate Profile", duplicateProfileBounds(), mouseX, mouseY)
        drawButton(graphics, "Delete Profile", deleteProfileBounds(), mouseX, mouseY, danger = true)
        drawButton(graphics, "Assign Server", assignServerBounds(), mouseX, mouseY)
    }

    override fun mouseClicked(
        event: MouseButtonEvent,
        doubleClick: Boolean,
    ): Boolean {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
        val x = event.x().toInt()
        val y = event.y().toInt()
        toolbarButtons().entries.firstOrNull { it.value.contains(x, y) }?.let {
            handleToolbar(it.key)
            return true
        }
        session.draft.pages.sortedBy { it.order }.forEachIndexed { index, page ->
            if (pageBounds(index).contains(x, y)) {
                selectedPageId = page.id
                selectedModuleId = null
                syncSelectionFields()
                return true
            }
        }
        when {
            addPageBounds().contains(x, y) -> {
                session.addPage()?.let { selectedPageId = it.id }
                syncSelectionFields()
                return true
            }
            deletePageBounds().contains(x, y) -> {
                if (session.deletePage(selectedPageId)) {
                    selectedPageId =
                        session.draft.pages
                            .minBy { it.order }
                            .id
                    selectedModuleId = null
                    status = "Page deleted"
                }
                syncSelectionFields()
                return true
            }
            paletteTypeBounds().contains(x, y) -> {
                paletteType = UiModuleType.entries[(paletteType.ordinal + 1) % UiModuleType.entries.size]
                return true
            }
            addModuleBounds().contains(x, y) -> {
                val added = session.addModule(paletteType, selectedPageId)
                selectedModuleId = added?.id
                status = if (added != null) "Module added" else "Module unavailable or no grid space"
                syncSelectionFields()
                return true
            }
        }
        currentPlacements().filter { it.pageId == selectedPageId }.asReversed().forEach { placement ->
            val bounds = placementBounds(placement.moduleId) ?: return@forEach
            if (bounds.contains(x, y)) {
                selectedModuleId = placement.moduleId
                resizing = x >= bounds.right - 10 && y >= bounds.bottom - 10
                dragging = !resizing
                syncSelectionFields()
                return true
            }
        }
        if (handleInspectorClick(x, y)) return true
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(
        event: MouseButtonEvent,
        dragX: Double,
        dragY: Double,
    ): Boolean {
        val moduleId = selectedModuleId ?: return super.mouseDragged(event, dragX, dragY)
        if (!dragging && !resizing) return super.mouseDragged(event, dragX, dragY)
        val canvas = canvasBounds()
        val columnWidth = canvas.width.toDouble() / mode.columns
        val column = ((event.x() - canvas.x) / columnWidth).toInt().coerceIn(0, mode.columns - 1)
        val row = ((event.y() - canvas.y) / GRID_ROW_HEIGHT).toInt().coerceAtLeast(0)
        val placement = currentPlacements().first { it.moduleId == moduleId }
        if (resizing) {
            session.resize(moduleId, mode, (column - placement.column + 1).coerceAtLeast(1), (row - placement.row + 1).coerceAtLeast(1))
        } else {
            session.move(moduleId, mode, column.coerceAtMost(mode.columns - placement.width), row)
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (dragging || resizing) {
            dragging = false
            resizing = false
            return true
        }
        return super.mouseReleased(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (PanelKeybind.matchesRecovery(event)) {
            minecraft.setScreen(ModularVenusScreen(services, FactoryUiProfile.profile, safeMode = true))
            return true
        }
        if (focused is EditBox) return super.keyPressed(event)
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            cancel()
            return true
        }
        if (event.modifiers() and GLFW.GLFW_MOD_CONTROL != 0) {
            if (event.key() == GLFW.GLFW_KEY_Z) return session.undo()
            if (event.key() == GLFW.GLFW_KEY_Y) return session.redo()
        }
        val moduleId = selectedModuleId
        if (moduleId != null) {
            if (event.key() == GLFW.GLFW_KEY_DELETE) return session.deleteModule(moduleId).also { selectedModuleId = null }
            val placement = currentPlacements().first { it.moduleId == moduleId }
            val delta = if (event.modifiers() and GLFW.GLFW_MOD_SHIFT != 0) 1 else 0
            return when (event.key()) {
                GLFW.GLFW_KEY_LEFT -> editByKey(moduleId, placement, -1, 0, delta)
                GLFW.GLFW_KEY_RIGHT -> editByKey(moduleId, placement, 1, 0, delta)
                GLFW.GLFW_KEY_UP -> editByKey(moduleId, placement, 0, -1, delta)
                GLFW.GLFW_KEY_DOWN -> editByKey(moduleId, placement, 0, 1, delta)
                else -> super.keyPressed(event)
            }
        }
        return super.keyPressed(event)
    }

    private fun editByKey(
        moduleId: String,
        placement: dev.ilgax.venus.client.ui.profile.UiPlacement,
        dx: Int,
        dy: Int,
        resize: Int,
    ): Boolean =
        if (resize == 1) {
            session.resize(moduleId, mode, placement.width + dx, placement.height + dy)
        } else {
            session.move(moduleId, mode, placement.column + dx, placement.row + dy)
        }

    private fun handleToolbar(label: String) {
        when (label) {
            "Apply" -> apply()
            "Cancel" -> cancel()
            "Undo" -> session.undo()
            "Redo" -> session.redo()
            "Layout" -> mode = if (mode == UiLayoutMode.NORMAL) UiLayoutMode.COMPACT else UiLayoutMode.NORMAL
            "Export" -> {
                minecraft.keyboardHandler.setClipboard(services.profiles.export(session.draft))
                status = "Profile code copied"
            }
            "Import" ->
                runCatching { services.profiles.importAsCopy(minecraft.keyboardHandler.clipboard) }
                    .onSuccess { minecraft.setScreen(UiEditorScreen(services, it, serverAddress)) }
                    .onFailure { status = it.message ?: "Import failed" }
            "Previous" -> cycleProfile(-1)
            "Next" -> cycleProfile(1)
        }
    }

    private fun handleInspectorClick(
        x: Int,
        y: Int,
    ): Boolean {
        when {
            renameBounds().contains(x, y) -> {
                val title = titleField.value
                val changed =
                    selectedModuleId?.let { session.renameModule(it, title) }
                        ?: session.renamePage(selectedPageId, title)
                status = if (changed) "Title updated" else "Invalid title"
            }
            deleteModuleBounds().contains(x, y) -> selectedModuleId?.let { session.deleteModule(it) }.also { selectedModuleId = null }
            themeTokenBounds().contains(x, y) -> {
                themeToken = ThemeToken.entries[(themeToken.ordinal + 1) % ThemeToken.entries.size]
                valueField.setValue(themeToken.hex(session.draft.theme))
            }
            setColorBounds().contains(x, y) -> setThemeColor()
            numericTokenBounds().contains(x, y) ->
                numericToken =
                    NumericToken.entries[(numericToken.ordinal + 1) % NumericToken.entries.size]
            numericMinusBounds().contains(x, y) -> changeNumeric(-1)
            numericPlusBounds().contains(x, y) -> changeNumeric(1)
            navigationBounds().contains(x, y) ->
                changeShell {
                    it.copy(
                        navigationPlacement =
                            NavigationPlacement.entries[(it.navigationPlacement.ordinal + 1) % NavigationPlacement.entries.size],
                    )
                }
            topBarBounds().contains(x, y) -> changeShell { it.copy(showTopBar = !it.showTopBar) }
            opacityMinusBounds().contains(
                x,
                y,
            ) -> changeShell { it.copy(backgroundOpacity = (it.backgroundOpacity - 0.05f).coerceAtLeast(0f)) }
            opacityPlusBounds().contains(
                x,
                y,
            ) -> changeShell { it.copy(backgroundOpacity = (it.backgroundOpacity + 0.05f).coerceAtMost(1f)) }
            moduleOptionBounds().contains(x, y) -> cycleModuleOption()
            duplicateProfileBounds().contains(x, y) -> {
                val copy = services.profiles.editableCopy(session.draft)
                services.profiles.saveProfile(copy)
                minecraft.setScreen(UiEditorScreen(services, copy, serverAddress))
            }
            deleteProfileBounds().contains(x, y) -> {
                if (session.draft.id != "factory" &&
                    services.profiles.file.profiles
                        .any { it.id == session.draft.id }
                ) {
                    services.profiles.delete(session.draft.id)
                    minecraft.setScreen(UiEditorScreen(services, services.profiles.resolve(serverAddress), serverAddress))
                } else {
                    status = "Factory Default cannot be deleted"
                }
            }
            assignServerBounds().contains(x, y) -> {
                if (serverAddress == null) {
                    status = "Join a server to assign this profile"
                } else {
                    services.profiles.saveProfile(session.draft)
                    services.profiles.assignCurrentServer(serverAddress, session.draft.id)
                    status = "Assigned to $serverAddress"
                }
            }
            else -> return false
        }
        UiThemeRuntime.activate(session.draft.theme)
        syncSelectionFields()
        return true
    }

    private fun apply() {
        session.renameProfile(profileName.value)
        val applied = session.apply()
        minecraft.setScreen(ModularVenusScreen(services, applied))
    }

    private fun cancel() {
        minecraft.setScreen(ModularVenusScreen(services, session.original))
    }

    private fun cycleProfile(delta: Int) {
        val profiles = services.profiles.allProfiles()
        val current = profiles.indexOfFirst { it.id == session.original.id }.coerceAtLeast(0)
        val next = profiles[(current + delta + profiles.size) % profiles.size]
        minecraft.setScreen(UiEditorScreen(services, next, serverAddress))
    }

    private fun setThemeColor() {
        val raw = valueField.value.removePrefix("#")
        val color = raw.toLongOrNull(16)?.toInt()
        if (color == null || raw.length !in setOf(6, 8)) {
            status = "Use RRGGBB or AARRGGBB"
            return
        }
        val argb = if (raw.length == 6) color or 0xFF000000.toInt() else color
        if (session.change { it.copy(theme = themeToken.set(it.theme, argb)) }) {
            status = UiThemeRuntime.contrastWarnings(session.draft.theme).firstOrNull() ?: "Theme color updated"
        }
    }

    private fun changeNumeric(delta: Int) {
        session.change { it.copy(theme = numericToken.change(it.theme, delta)) }
    }

    private fun changeShell(transform: (dev.ilgax.venus.client.ui.profile.UiShell) -> dev.ilgax.venus.client.ui.profile.UiShell) {
        session.change { it.copy(shell = transform(it.shell)) }
    }

    private fun cycleModuleOption() {
        val id = selectedModuleId ?: return
        val metrics = listOf("tps", "mspt", "ram_used", "ram_max", "online_players", "uptime")
        session.change { profile ->
            profile.copy(
                modules =
                    profile.modules.map { module ->
                        if (module.id != id) {
                            module
                        } else if (module.type == UiModuleType.METRIC_CARD || module.type == UiModuleType.STAT_GRAPH) {
                            val current = metrics.indexOf(module.settings.primaryMetric).coerceAtLeast(0)
                            module.copy(settings = module.settings.copy(primaryMetric = metrics[(current + 1) % metrics.size]))
                        } else {
                            module.copy(settings = module.settings.copy(showPlayerHeads = !module.settings.showPlayerHeads))
                        }
                    },
            )
        }
    }

    private fun syncSelectionFields() {
        val module = selectedModuleId?.let { id -> session.draft.modules.firstOrNull { it.id == id } }
        val page = session.draft.pages.firstOrNull { it.id == selectedPageId }
        if (::titleField.isInitialized) titleField.setValue(module?.title ?: page?.title.orEmpty())
        if (::valueField.isInitialized) valueField.setValue(themeToken.hex(session.draft.theme))
        if (::profileName.isInitialized && !profileName.isFocused) profileName.setValue(session.draft.name)
    }

    private fun currentPlacements() =
        if (mode == UiLayoutMode.NORMAL) session.draft.normalLayout.placements else session.draft.compactLayout.placements

    private fun placementBounds(moduleId: String): Bounds? {
        val placement = currentPlacements().firstOrNull { it.moduleId == moduleId } ?: return null
        val canvas = canvasBounds()
        val columnWidth = canvas.width.toDouble() / mode.columns
        return Bounds(
            canvas.x + (placement.column * columnWidth).toInt() + 2,
            canvas.y + placement.row * GRID_ROW_HEIGHT + 2,
            (placement.width * columnWidth).toInt() - 4,
            placement.height * GRID_ROW_HEIGHT - 4,
        )
    }

    private fun drawButton(
        graphics: GuiGraphics,
        label: String,
        bounds: Bounds,
        mouseX: Int,
        mouseY: Int,
        danger: Boolean = false,
    ) {
        VenusDraw.rect(graphics, bounds, if (bounds.contains(mouseX, mouseY)) VenusTheme.HOVER else VenusTheme.RAISED)
        VenusDraw.border(graphics, bounds, if (danger) VenusTheme.DANGER else VenusTheme.BORDER)
        VenusDraw.textCentered(graphics, font, label, bounds, if (danger) VenusTheme.DANGER else VenusTheme.TEXT, false)
    }

    private fun toolbarButtons(): Map<String, Bounds> =
        linkedMapOf(
            "Apply" to Bounds(8, 6, 52, 22),
            "Cancel" to Bounds(64, 6, 52, 22),
            "Undo" to Bounds(124, 6, 44, 22),
            "Redo" to Bounds(172, 6, 44, 22),
            "Layout" to Bounds(224, 6, 110, 22),
            "Previous" to Bounds(342, 6, 62, 22),
            "Next" to Bounds(408, 6, 48, 22),
            "Export" to Bounds(464, 6, 54, 22),
            "Import" to Bounds(522, 6, 54, 22),
        )

    private fun pageBounds(index: Int) = Bounds(8, TOOLBAR_HEIGHT + 24 + index * 26, LEFT_WIDTH - 16, 22)

    private fun addPageBounds() = Bounds(8, TOOLBAR_HEIGHT + 190, 68, 22)

    private fun deletePageBounds() = Bounds(80, TOOLBAR_HEIGHT + 190, LEFT_WIDTH - 88, 22)

    private fun paletteTypeBounds() = Bounds(8, TOOLBAR_HEIGHT + 246, LEFT_WIDTH - 16, 22)

    private fun addModuleBounds() = Bounds(8, TOOLBAR_HEIGHT + 272, LEFT_WIDTH - 16, 22)

    private fun canvasBounds() =
        Bounds(
            LEFT_WIDTH + 8,
            TOOLBAR_HEIGHT + 8,
            width - LEFT_WIDTH - RIGHT_WIDTH - 16,
            height - TOOLBAR_HEIGHT - 28,
        )

    private fun renameBounds() = Bounds(width - RIGHT_WIDTH + 10, 116, RIGHT_WIDTH - 20, 22)

    private fun deleteModuleBounds() = Bounds(width - RIGHT_WIDTH + 10, 142, RIGHT_WIDTH - 20, 22)

    private fun themeTokenBounds() = Bounds(width - RIGHT_WIDTH + 10, 190, RIGHT_WIDTH - 20, 22)

    private fun setColorBounds() = Bounds(width - RIGHT_WIDTH + 10, 240, RIGHT_WIDTH - 20, 22)

    private fun numericTokenBounds() = Bounds(width - RIGHT_WIDTH + 10, 266, RIGHT_WIDTH - 74, 22)

    private fun numericMinusBounds() = Bounds(width - 60, 266, 22, 22)

    private fun numericPlusBounds() = Bounds(width - 34, 266, 22, 22)

    private fun navigationBounds() = Bounds(width - RIGHT_WIDTH + 10, 312, RIGHT_WIDTH - 20, 22)

    private fun topBarBounds() = Bounds(width - RIGHT_WIDTH + 10, 338, RIGHT_WIDTH - 20, 22)

    private fun opacityMinusBounds() = Bounds(width - RIGHT_WIDTH + 10, 364, (RIGHT_WIDTH - 24) / 2, 22)

    private fun opacityPlusBounds() = Bounds(width - RIGHT_WIDTH / 2 + 2, 364, (RIGHT_WIDTH - 24) / 2, 22)

    private fun moduleOptionBounds() = Bounds(width - RIGHT_WIDTH + 10, 390, RIGHT_WIDTH - 20, 22)

    private fun duplicateProfileBounds() = Bounds(width - RIGHT_WIDTH + 10, 416, RIGHT_WIDTH - 20, 22)

    private fun deleteProfileBounds() = Bounds(width - RIGHT_WIDTH + 10, 442, RIGHT_WIDTH - 20, 22)

    private fun assignServerBounds() = Bounds(width - RIGHT_WIDTH + 10, 468, RIGHT_WIDTH - 20, 22)

    private companion object {
        const val TOOLBAR_HEIGHT = 34
        const val LEFT_WIDTH = 150
        const val RIGHT_WIDTH = 190
        const val GRID_ROW_HEIGHT = 24
    }
}

private enum class ThemeToken {
    BACKGROUND,
    WINDOW,
    TOP_BAR,
    NAVIGATION,
    SURFACE,
    RAISED,
    HOVER,
    ACTIVE,
    BORDER,
    ACCENT,
    TEXT,
    TEXT_MUTED,
    SUCCESS,
    WARNING,
    DANGER,
    ;

    fun value(theme: UiTheme): Int =
        when (this) {
            BACKGROUND -> theme.background
            WINDOW -> theme.window
            TOP_BAR -> theme.topBar
            NAVIGATION -> theme.navigation
            SURFACE -> theme.surface
            RAISED -> theme.raised
            HOVER -> theme.hover
            ACTIVE -> theme.active
            BORDER -> theme.border
            ACCENT -> theme.accent
            TEXT -> theme.text
            TEXT_MUTED -> theme.textMuted
            SUCCESS -> theme.success
            WARNING -> theme.warning
            DANGER -> theme.danger
        }

    fun set(
        theme: UiTheme,
        value: Int,
    ): UiTheme =
        when (this) {
            BACKGROUND -> theme.copy(background = value)
            WINDOW -> theme.copy(window = value)
            TOP_BAR -> theme.copy(topBar = value)
            NAVIGATION -> theme.copy(navigation = value)
            SURFACE -> theme.copy(surface = value)
            RAISED -> theme.copy(raised = value)
            HOVER -> theme.copy(hover = value)
            ACTIVE -> theme.copy(active = value)
            BORDER -> theme.copy(border = value)
            ACCENT -> theme.copy(accent = value)
            TEXT -> theme.copy(text = value)
            TEXT_MUTED -> theme.copy(textMuted = value)
            SUCCESS -> theme.copy(success = value)
            WARNING -> theme.copy(warning = value)
            DANGER -> theme.copy(danger = value)
        }

    fun hex(theme: UiTheme): String = "%08X".format(value(theme))
}

private enum class NumericToken {
    SPACING,
    CONTENT_PADDING,
    ROW_HEIGHT,
    CONTROL_HEIGHT,
    CARD_PADDING,
    NAVIGATION_SIZE,
    TOP_BAR_HEIGHT,
    BORDER_WIDTH,
    CORNER_RADIUS,
    ;

    fun label(theme: UiTheme): String = "$name: ${value(theme)}"

    fun change(
        theme: UiTheme,
        delta: Int,
    ): UiTheme =
        when (this) {
            SPACING -> theme.copy(spacing = (theme.spacing + delta).coerceIn(0, 32))
            CONTENT_PADDING -> theme.copy(contentPadding = (theme.contentPadding + delta).coerceIn(4, 32))
            ROW_HEIGHT -> theme.copy(rowHeight = (theme.rowHeight + delta).coerceIn(16, 48))
            CONTROL_HEIGHT -> theme.copy(controlHeight = (theme.controlHeight + delta).coerceIn(16, 40))
            CARD_PADDING -> theme.copy(cardPadding = (theme.cardPadding + delta).coerceIn(4, 24))
            NAVIGATION_SIZE -> theme.copy(navigationSize = (theme.navigationSize + delta * 4).coerceIn(72, 240))
            TOP_BAR_HEIGHT -> theme.copy(topBarHeight = (theme.topBarHeight + delta).coerceIn(24, 64))
            BORDER_WIDTH -> theme.copy(borderWidth = (theme.borderWidth + delta).coerceIn(0, 4))
            CORNER_RADIUS -> theme.copy(cornerRadius = (theme.cornerRadius + delta).coerceIn(0, 16))
        }

    private fun value(theme: UiTheme): Int =
        when (this) {
            SPACING -> theme.spacing
            CONTENT_PADDING -> theme.contentPadding
            ROW_HEIGHT -> theme.rowHeight
            CONTROL_HEIGHT -> theme.controlHeight
            CARD_PADDING -> theme.cardPadding
            NAVIGATION_SIZE -> theme.navigationSize
            TOP_BAR_HEIGHT -> theme.topBarHeight
            BORDER_WIDTH -> theme.borderWidth
            CORNER_RADIUS -> theme.cornerRadius
        }
}
