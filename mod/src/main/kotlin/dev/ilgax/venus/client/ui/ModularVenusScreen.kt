package dev.ilgax.venus.client.ui

import dev.ilgax.venus.client.ui.component.VenusModal
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.ModalKind
import dev.ilgax.venus.client.ui.core.UiThemeRuntime
import dev.ilgax.venus.client.ui.core.VenusModalRequest
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.layout.UiLayoutEngine
import dev.ilgax.venus.client.ui.layout.UiShellGeometry
import dev.ilgax.venus.client.ui.module.ActiveTransfersModule
import dev.ilgax.venus.client.ui.module.MetricCardModule
import dev.ilgax.venus.client.ui.module.OnlinePlayersModule
import dev.ilgax.venus.client.ui.module.PageWorkflowModule
import dev.ilgax.venus.client.ui.module.ServerStatusModule
import dev.ilgax.venus.client.ui.module.StatGraphModule
import dev.ilgax.venus.client.ui.module.UiDataActions
import dev.ilgax.venus.client.ui.module.UiModule
import dev.ilgax.venus.client.ui.module.UiModuleRuntime
import dev.ilgax.venus.client.ui.module.UiScreenServices
import dev.ilgax.venus.client.ui.page.AuthPage
import dev.ilgax.venus.client.ui.page.ConsolePage
import dev.ilgax.venus.client.ui.page.FilesPage
import dev.ilgax.venus.client.ui.page.PlayersPage
import dev.ilgax.venus.client.ui.page.SettingsOverviewPage
import dev.ilgax.venus.client.ui.profile.FactoryUiProfile
import dev.ilgax.venus.client.ui.profile.NavigationPlacement
import dev.ilgax.venus.client.ui.profile.UiLayoutMode
import dev.ilgax.venus.client.ui.profile.UiModuleInstance
import dev.ilgax.venus.client.ui.profile.UiModuleType
import dev.ilgax.venus.client.ui.profile.UiPageDefinition
import dev.ilgax.venus.client.ui.profile.UiProfile
import dev.ilgax.venus.client.ui.profile.UiProfileRecovery
import dev.ilgax.venus.client.ui.profile.UiProfilesFile
import dev.ilgax.venus.client.ui.render.VenusDraw
import dev.ilgax.venus.keybind.PanelKeybind
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class ModularVenusScreen(
    private val services: UiScreenServices,
    val profile: UiProfile,
    private val safeMode: Boolean = false,
) : Screen(Component.translatable("screen.venus.panel")) {
    private val modules = linkedMapOf<String, UiModule>()
    private val pageScroll = mutableMapOf<String, Int>()
    private val dataRuntime =
        UiModuleRuntime(
            UiDataActions(
                subscribeStats = services.subscribeStats,
                requestPlayers = services.requestPlayerList,
                subscribeLogs = services.subscribeLogs,
                requestFiles = {
                    services.requestFileRoots()
                    Unit
                },
            ),
        )

    private lateinit var geometry: UiShellGeometry
    private var activePageId =
        profile.pages
            .filter { it.visible }
            .minByOrNull { it.order }
            ?.id ?: profile.pages.first().id
    private var compact = false
    private var pageContentHeight = 0
    private var modal: VenusModalRequest? = null
    private var toast: Pair<String, Long>? = null
    private var activated = false

    internal fun initializeForJvmTest(
        width: Int,
        height: Int,
    ) {
        this.width = width
        this.height = height
        init()
    }

    override fun init() {
        UiThemeRuntime.activate(profile.theme)
        compact = width < COMPACT_WIDTH || height < COMPACT_HEIGHT
        geometry = UiLayoutEngine.shell(width, height, profile.shell, profile.theme, compact)
        if (modules.isEmpty()) buildModules()
        modules.values.flatMap(UiModule::children).forEach(::addRenderableWidget)
        layoutActiveModules()
        updateChildVisibility()
        if (!activated) {
            activateCurrentPage()
            when (services.profiles.loadResult.recovery) {
                UiProfileRecovery.BACKUP -> showToast("Recovered the last-good UI profiles backup")
                UiProfileRecovery.FACTORY -> showToast("UI profiles were unreadable; Factory Default is active")
                UiProfileRecovery.NONE -> Unit
            }
            activated = true
        }
    }

    private fun buildModules() {
        profile.modules.forEach { instance ->
            modules[instance.id] = createModule(instance)
        }
    }

    private fun createModule(instance: UiModuleInstance): UiModule =
        when (instance.type) {
            UiModuleType.SERVER_STATUS -> ServerStatusModule(instance)
            UiModuleType.METRIC_CARD -> MetricCardModule(instance)
            UiModuleType.STAT_GRAPH -> StatGraphModule(instance)
            UiModuleType.ONLINE_PLAYERS -> OnlinePlayersModule(instance)
            UiModuleType.ACTIVE_TRANSFERS -> ActiveTransfersModule(instance)
            UiModuleType.PLAYERS_WORKFLOW -> playersModule(instance)
            UiModuleType.FILES_WORKFLOW -> filesModule(instance)
            UiModuleType.CONSOLE_WORKFLOW -> consoleModule(instance)
            UiModuleType.AUTH_WORKFLOW -> PageWorkflowModule(instance, AuthPage({}, {}, {}))
            UiModuleType.SETTINGS_WORKFLOW ->
                PageWorkflowModule(
                    instance,
                    SettingsOverviewPage({ profile.name }, ::openEditor),
                )
        }

    private fun playersModule(instance: UiModuleInstance): UiModule {
        val page =
            PlayersPage(
                services.requestPlayerList,
                services.requestPlayerDetail,
                services.sendPlayerAction,
            ) { instance.settings.showPlayerHeads }
        return PageWorkflowModule(instance, page, { listOfNotNull(page.searchField()?.editBox()) })
    }

    private fun filesModule(instance: UiModuleInstance): UiModule {
        val page =
            FilesPage(
                services.requestFileRoots,
                services.requestFileList,
                services.sendFileAction,
                services.uploadFile,
                services.downloadFile,
                services.openFileEditor,
                services.saveEditedFile,
                services.cancelFileTransfer,
                { title, message, action, kind -> showConfirm(title, message, action, kind) },
                { _, title, message -> showToast("$title: $message") },
            )
        return PageWorkflowModule(instance, page, page::widgets)
    }

    private fun consoleModule(instance: UiModuleInstance): UiModule {
        val page = ConsolePage(services.sendConsoleCommand, services.subscribeLogs) { instance.settings.consoleHistoryLimit }
        return PageWorkflowModule(
            instance,
            page,
            { listOfNotNull(page.inputField()?.editBox()) },
            page::keyPressed,
        )
    }

    private fun layoutActiveModules() {
        val content = geometry.content.inset(profile.theme.contentPadding)
        val layout = if (compact) profile.compactLayout else profile.normalLayout
        val mode = if (compact) UiLayoutMode.COMPACT else UiLayoutMode.NORMAL
        val pageGeometry =
            UiLayoutEngine.modules(
                content,
                layout,
                activePageId,
                mode,
                profile.theme.spacing,
                profile.theme.rowHeight,
                pageScroll[activePageId] ?: 0,
            )
        pageContentHeight = pageGeometry.totalHeight
        pageGeometry.modules.forEach { (id, bounds) -> modules[id]?.layout(bounds) }
    }

    private fun activateCurrentPage() {
        val active = activeModules()
        active.forEach(UiModule::onActivate)
        dataRuntime.activate(active.map(UiModule::instance))
        updateChildVisibility()
    }

    private fun deactivateCurrentPage() {
        activeModules().forEach(UiModule::onDeactivate)
    }

    private fun activeModules(): List<UiModule> {
        val layout = if (compact) profile.compactLayout else profile.normalLayout
        return layout.placements
            .asSequence()
            .filter { it.pageId == activePageId && it.visible }
            .mapNotNull { modules[it.moduleId] }
            .toList()
    }

    private fun updateChildVisibility() {
        val activeIds = activeModules().mapTo(mutableSetOf()) { it.instance.id }
        modules.values.forEach { module ->
            module.children().forEach { it.visible = module.instance.id in activeIds }
        }
    }

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val opacity = (profile.shell.backgroundOpacity * 255).toInt().coerceIn(0, 255)
        val background = (opacity shl 24) or (VenusTheme.BACKGROUND and 0x00FFFFFF)
        graphics.fill(0, 0, width, height, background)
        VenusDraw.rect(graphics, geometry.window, VenusTheme.WINDOW)
        VenusDraw.border(graphics, geometry.window, VenusTheme.BORDER)
        renderTopBar(graphics, mouseX, mouseY)
        renderNavigation(graphics, mouseX, mouseY)
        VenusDraw.rect(graphics, geometry.content, VenusTheme.SURFACE)
        graphics.enableScissor(geometry.content.x, geometry.content.y, geometry.content.right, geometry.content.bottom)
        activeModules().forEach { it.render(graphics, font, mouseX, mouseY, partialTick) }
        graphics.disableScissor()
        super.render(graphics, mouseX, mouseY, partialTick)
        renderModal(graphics)
        renderToast(graphics)
    }

    private fun renderTopBar(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (geometry.topBar.height > 0) {
            VenusDraw.rect(graphics, geometry.topBar, VenusTheme.TOP_BAR)
            if (profile.shell.showLogo) {
                VenusDraw.rect(graphics, geometry.topBar.x + 10, geometry.topBar.centerY - 7, 14, 14, VenusTheme.ACCENT)
                VenusDraw.textCentered(
                    graphics,
                    font,
                    "V",
                    Bounds(geometry.topBar.x + 10, geometry.topBar.centerY - 7, 14, 14),
                    VenusTheme.TOP_BAR,
                    false,
                )
            }
            if (profile.shell.showTitle) {
                VenusDraw.text(
                    graphics,
                    font,
                    profile.name,
                    geometry.topBar.x + 32,
                    geometry.topBar.centerY - font.lineHeight / 2,
                    VenusTheme.TEXT,
                    false,
                )
            }
            if (profile.shell.showServerName) {
                val server = SessionState.serverListName ?: SessionState.serverAddress.orEmpty()
                VenusDraw.textRight(
                    graphics,
                    font,
                    server,
                    editBounds().x - 8,
                    geometry.topBar.centerY - font.lineHeight / 2,
                    VenusTheme.TEXT_MUTED,
                    false,
                )
            }
        }
        val edit = editBounds()
        val close = closeBounds()
        val reset = resetBounds()
        if (safeMode) VenusDraw.rect(graphics, reset, if (reset.contains(mouseX, mouseY)) VenusTheme.HOVER else VenusTheme.RAISED)
        VenusDraw.rect(graphics, edit, if (edit.contains(mouseX, mouseY)) VenusTheme.HOVER else VenusTheme.RAISED)
        VenusDraw.rect(graphics, close, if (close.contains(mouseX, mouseY)) VenusTheme.HOVER else VenusTheme.RAISED)
        if (safeMode) VenusDraw.textCentered(graphics, font, "Use Default", reset, VenusTheme.TEXT, false)
        VenusDraw.textCentered(graphics, font, "Edit", edit, VenusTheme.TEXT, false)
        VenusDraw.textCentered(graphics, font, "X", close, VenusTheme.DANGER, false)
    }

    private fun renderNavigation(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        VenusDraw.rect(graphics, geometry.navigation, VenusTheme.SIDEBAR)
        visiblePages().forEachIndexed { index, page ->
            val bounds = navigationItemBounds(index)
            val active = page.id == activePageId
            val hovered = bounds.contains(mouseX, mouseY)
            if (active || hovered) VenusDraw.rect(graphics, bounds, if (active) VenusTheme.ACTIVE else VenusTheme.HOVER)
            val label = if (profile.shell.showNavigationLabels) page.title else page.title.take(1)
            VenusDraw.textTruncated(
                graphics,
                font,
                label,
                bounds.x + 8,
                bounds.y + (bounds.height - font.lineHeight) / 2,
                bounds.width - 16,
                if (active) VenusTheme.TEXT else VenusTheme.TEXT_MUTED,
                false,
            )
        }
    }

    private fun visiblePages(): List<UiPageDefinition> = profile.pages.filter { it.visible }.sortedBy { it.order }

    private fun navigationItemBounds(index: Int): Bounds {
        val pages = visiblePages()
        return if (profile.shell.navigationPlacement == NavigationPlacement.LEFT ||
            profile.shell.navigationPlacement == NavigationPlacement.RIGHT
        ) {
            Bounds(geometry.navigation.x + 6, geometry.navigation.y + 8 + index * 28, geometry.navigation.width - 12, 24)
        } else {
            val itemWidth = geometry.navigation.width / pages.size.coerceAtLeast(1)
            Bounds(geometry.navigation.x + index * itemWidth, geometry.navigation.y + 4, itemWidth, geometry.navigation.height - 8)
        }
    }

    private fun editBounds(): Bounds = Bounds(geometry.window.right - 78, geometry.window.y + 6, 44, 20)

    private fun resetBounds(): Bounds = Bounds(geometry.window.right - 158, geometry.window.y + 6, 74, 20)

    private fun closeBounds(): Bounds = Bounds(geometry.window.right - 28, geometry.window.y + 6, 20, 20)

    override fun mouseClicked(
        event: MouseButtonEvent,
        doubleClick: Boolean,
    ): Boolean {
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()
        val button = event.button()
        if (modal != null) return if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) handleModalClick(mouseX, mouseY) else true
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (safeMode && resetBounds().contains(mouseX, mouseY)) {
                services.profiles.activate(UiProfilesFile.FACTORY_PROFILE_ID)
                minecraft.setScreen(ModularVenusScreen(services, FactoryUiProfile.profile))
                return true
            }
            if (closeBounds().contains(mouseX, mouseY)) {
                onClose()
                return true
            }
            if (editBounds().contains(mouseX, mouseY)) {
                openEditor()
                return true
            }
            visiblePages().forEachIndexed { index, page ->
                if (navigationItemBounds(index).contains(mouseX, mouseY)) {
                    navigate(page.id)
                    return true
                }
            }
        }
        if (activeModules().any { it.mouseClicked(event.x(), event.y(), button) }) return true
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        if (activeModules().any { it.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) }) return true
        if (!geometry.content.contains(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        val maxScroll = (pageContentHeight - geometry.content.height + profile.theme.contentPadding * 2).coerceAtLeast(0)
        if (maxScroll == 0) return false
        val current = pageScroll[activePageId] ?: 0
        pageScroll[activePageId] = (current - (verticalAmount * profile.theme.rowHeight).toInt()).coerceIn(0, maxScroll)
        layoutActiveModules()
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (PanelKeybind.matchesRecovery(event)) {
            minecraft.setScreen(ModularVenusScreen(services, FactoryUiProfile.profile, safeMode = true))
            return true
        }
        if (PanelKeybind.matches(event, focused is net.minecraft.client.gui.components.EditBox)) {
            onClose()
            return true
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (modal != null) {
                modal?.onCancel?.invoke()
                modal = null
            } else {
                onClose()
            }
            return true
        }
        if (modal != null) return true
        activeModules().filterIsInstance<PageWorkflowModule>().forEach {
            if (it.keyPressed(event.key(), 0, event.modifiers())) return true
        }
        return super.keyPressed(event)
    }

    private fun navigate(pageId: String) {
        if (pageId == activePageId) return
        deactivateCurrentPage()
        activePageId = pageId
        layoutActiveModules()
        activateCurrentPage()
    }

    private fun openEditor() {
        services.openEditor(profile)
    }

    private fun showConfirm(
        title: String,
        message: String,
        action: () -> Unit,
        kind: ModalKind,
    ) {
        if (!profile.behavior.confirmDangerousActions) {
            action()
            return
        }
        modal = VenusModalRequest(kind, title, message, onConfirm = action)
    }

    private fun renderModal(graphics: GuiGraphics) {
        val request = modal ?: return
        VenusDraw.rect(graphics, 0, 0, width, height, VenusTheme.MODAL_SCRIM)
        VenusModal(modalBounds(), request).render(graphics, font)
    }

    private fun handleModalClick(
        mouseX: Int,
        mouseY: Int,
    ): Boolean {
        val request = modal ?: return false
        val component = VenusModal(modalBounds(), request)
        when {
            component.confirmBounds(font).contains(mouseX, mouseY) -> request.onConfirm()
            component.cancelBounds(font).contains(mouseX, mouseY) -> request.onCancel()
            !modalBounds().contains(mouseX, mouseY) && request.dismissOnOutsideClick -> request.onCancel()
            else -> return true
        }
        modal = null
        return true
    }

    private fun modalBounds(): Bounds = Bounds(width / 2 - 130, height / 2 - 60, 260, 120)

    private fun showToast(message: String) {
        toast = message to (System.currentTimeMillis() + 4000)
    }

    private fun renderToast(graphics: GuiGraphics) {
        val current = toast ?: return
        if (System.currentTimeMillis() >= current.second) {
            toast = null
            return
        }
        val bounds = Bounds(width - 228, height - 44, 220, 36)
        VenusDraw.rect(graphics, bounds, VenusTheme.RAISED)
        VenusDraw.border(graphics, bounds, VenusTheme.BORDER)
        VenusDraw.textTruncated(graphics, font, current.first, bounds.x + 8, bounds.y + 12, bounds.width - 16, VenusTheme.TEXT, false)
    }

    override fun removed() {
        deactivateCurrentPage()
        dataRuntime.clear()
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false

    internal fun activePageForTest(): String = activePageId

    internal fun isCompactForTest(): Boolean = compact

    internal fun isSafeModeForTest(): Boolean = safeMode

    private companion object {
        const val COMPACT_WIDTH = 900
        const val COMPACT_HEIGHT = 520
    }
}
