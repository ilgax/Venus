package dev.ilgax.venus.client.ui

import dev.ilgax.venus.client.ui.component.VenusModal
import dev.ilgax.venus.client.ui.component.VenusSidebar
import dev.ilgax.venus.client.ui.component.VenusToast
import dev.ilgax.venus.client.ui.component.VenusTopBar
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.FrameTimer
import dev.ilgax.venus.client.ui.core.ModalKind
import dev.ilgax.venus.client.ui.core.ToastKind
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusModalRequest
import dev.ilgax.venus.client.ui.core.VenusPage
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.core.VenusToastRequest
import dev.ilgax.venus.client.ui.core.VenusUiState
import dev.ilgax.venus.client.ui.page.AuthPage
import dev.ilgax.venus.client.ui.page.ConsolePage
import dev.ilgax.venus.client.ui.page.DashboardPage
import dev.ilgax.venus.client.ui.page.PlayersPage
import dev.ilgax.venus.client.ui.page.SettingsPage
import dev.ilgax.venus.client.ui.render.VenusDraw
import dev.ilgax.venus.client.ui.widget.VenusIconButton
import dev.ilgax.venus.keybind.PanelKeybind
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.concurrent.atomic.AtomicLong

/**
 * Root Venus screen. A centered Venus window with top bar, sidebar, main
 * content region, optional modal layer, and toast layer.
 *
 * Layout is recomputed in [init] and on resize — never every frame. Pages own
 * their own scroll/selection state; this screen owns navigation and the
 * modal/toast stacks. All server data flows through [SessionState]; pages
 * receive callbacks, never networking.
 */
class VenusScreen(
    private val sendConsoleCommand: (String) -> Unit,
    private val subscribeLogs: () -> Unit,
    private val requestPlayerList: () -> Unit,
    private val requestPlayerDetail: (String) -> Unit,
    private val sendPlayerAction: (String, String, Any?) -> String,
    private val subscribeStats: () -> Unit,
    private val onSaveSettings: (SettingsPage.Settings) -> Unit,
    private val initialSettings: SettingsPage.Settings,
) : Screen(Component.translatable("screen.venus.panel")) {
    private val uiState = VenusUiState()
    private val frameTimer = FrameTimer()
    private val toastIdCounter = AtomicLong(0)

    private var topBar: VenusTopBar? = null
    private var sidebar: VenusSidebar? = null
    private var closeBtn: VenusIconButton? = null
    private var contentBounds: Bounds = Bounds(0, 0, 0, 0)

    private lateinit var dashboardPage: DashboardPage
    private lateinit var playersPage: PlayersPage
    private lateinit var consolePage: ConsolePage
    private lateinit var authPage: AuthPage
    private lateinit var settingsPage: SettingsPage
    private var currentPage: dev.ilgax.venus.client.ui.page.VenusPageContract? = null

    override fun init() {
        uiState.compactMode = width < VenusDimensions.COMPACT_WIDTH || height < VenusDimensions.COMPACT_HEIGHT
        val margin = if (uiState.compactMode) VenusDimensions.WINDOW_MARGIN_COMPACT else VenusDimensions.WINDOW_MARGIN
        val topH = if (uiState.compactMode) VenusDimensions.TOP_BAR_HEIGHT_COMPACT else VenusDimensions.TOP_BAR_HEIGHT
        val sideW = if (uiState.compactMode) VenusDimensions.SIDEBAR_WIDTH_COMPACT else VenusDimensions.SIDEBAR_WIDTH

        val window = Bounds(margin, margin, width - margin * 2, height - margin * 2)
        val topBounds = Bounds(window.x, window.y, window.width, topH)
        val sideBounds = Bounds(window.x, window.y + topH, sideW, window.height - topH)
        contentBounds = Bounds(window.x + sideW, window.y + topH, window.width - sideW, window.height - topH)

        topBar = VenusTopBar(topBounds)
        sidebar = VenusSidebar(sideBounds)
        closeBtn =
            VenusIconButton(
                topBounds.right - 24,
                topBounds.y + (topH - 20) / 2,
                tooltipText = "Close",
                icon = VenusIconButton.IconGlyph.CLOSE,
            ) {
                onClose()
            }

        buildPages()
        currentPage?.onLeave()
        currentPage = pageFor(uiState.activePage)
        currentPage?.layout(contentBounds)
        currentPage?.onEnter()
        updateWidgetVisibility()
    }

    private fun buildPages() {
        if (!::dashboardPage.isInitialized) {
            dashboardPage =
                DashboardPage(
                    subscribeStats,
                    requestPlayerList,
                    { settingsPage.showPlayerHeads },
                ) { uuid ->
                    playersPage.selectAndNavigate(uuid)
                    navigateTo(VenusPage.PLAYERS)
                }
            playersPage =
                PlayersPage(
                    requestPlayerList,
                    requestPlayerDetail,
                    sendPlayerAction,
                ) { settingsPage.showPlayerHeads }
            consolePage = ConsolePage(sendConsoleCommand, subscribeLogs) { settingsPage.consoleHistoryLimit }
            authPage = AuthPage({ id -> }, { id -> }, { id -> })
            settingsPage = SettingsPage(onSaveSettings)
            settingsPage.applySettings(initialSettings)
        }
        playersPage.searchField()?.let { addRenderableWidget(it.editBox()) }
        consolePage.inputField()?.let { addRenderableWidget(it.editBox()) }
        settingsPage.widgets().forEach { addRenderableWidget(it) }
    }

    private fun updateWidgetVisibility() {
        val page = uiState.activePage
        playersPage.searchField()?.editBox()?.setVisible(page == VenusPage.PLAYERS)
        consolePage.inputField()?.editBox()?.setVisible(page == VenusPage.CONSOLE)
        settingsPage.widgets().forEach { it.visible = page == VenusPage.SETTINGS }
        if (page == VenusPage.CONSOLE) {
            consolePage.inputField()?.editBox()?.let {
                it.setFocused(true)
                setFocused(it)
            }
        } else if (page == VenusPage.PLAYERS) {
            playersPage.searchField()?.editBox()?.let {
                setFocused(it)
            }
        }
    }

    private fun pageFor(page: VenusPage): dev.ilgax.venus.client.ui.page.VenusPageContract =
        when (page) {
            VenusPage.DASHBOARD -> dashboardPage
            VenusPage.PLAYERS -> playersPage
            VenusPage.CONSOLE -> consolePage
            VenusPage.AUTH -> authPage
            VenusPage.SETTINGS -> settingsPage
        }

    override fun render(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val deltaMs = frameTimer.deltaMs()
        uiState.tickToasts(System.currentTimeMillis())

        val opacity = (settingsPage.backgroundOpacity * 255).toInt().coerceIn(0, 255)
        val bgAlpha = opacity shl 24
        g.fill(0, 0, width, height, bgAlpha or (VenusTheme.BACKGROUND and 0x00FFFFFF))
        VenusDraw.rect(g, 0, 0, width, height, bgAlpha or (VenusTheme.BACKGROUND and 0x00FFFFFF))

        val margin = if (uiState.compactMode) VenusDimensions.WINDOW_MARGIN_COMPACT else VenusDimensions.WINDOW_MARGIN
        val window = Bounds(margin, margin, width - margin * 2, height - margin * 2)
        VenusDraw.rect(g, window, VenusTheme.WINDOW)
        VenusDraw.border(g, window, VenusTheme.BORDER)

        topBar?.let {
            it.serverName = SessionState.serverListName ?: SessionState.serverAddress ?: ""
            it.connected = SessionState.sessionActive
            it.render(g, font, mouseX, mouseY, closeBtn!!.bounds)
        }
        closeBtn?.renderVenus(g, mouseX, mouseY, partialTick)
        sidebar?.render(g, font, mouseX, mouseY, uiState.activePage)

        val page = currentPage
        if (page != null) {
            VenusDraw.rect(g, contentBounds, VenusTheme.SURFACE)
            page.render(g, font, mouseX, mouseY, partialTick)
        }

        super.render(g, mouseX, mouseY, partialTick)

        renderModals(g, font, deltaMs)
        renderToasts(g, font, deltaMs)
    }

    private fun renderModals(
        g: GuiGraphics,
        font: net.minecraft.client.gui.Font,
        deltaMs: Float,
    ) {
        val modal = uiState.currentModal ?: return
        VenusDraw.rect(g, 0, 0, width, height, VenusTheme.MODAL_SCRIM)
        val mw = VenusDimensions.MODAL_WIDTH
        val mh = maxOf(VenusDimensions.MODAL_MIN_HEIGHT, font.lineHeight * 6)
        val mb = Bounds(width / 2 - mw / 2, height / 2 - mh / 2, mw, mh)
        val vm = VenusModal(mb, modal)
        vm.tick(deltaMs)
        vm.render(g, font)
    }

    private fun renderToasts(
        g: GuiGraphics,
        font: net.minecraft.client.gui.Font,
        deltaMs: Float,
    ) {
        val toastW = 200
        val toastH = 48
        val margin = 8
        var y = height - toastH - margin
        uiState.toasts.forEach { req ->
            val tb = Bounds(width - toastW - margin, y, toastW, toastH)
            val vt = VenusToast(tb, req)
            vt.tick(deltaMs)
            vt.render(g, font)
            y -= toastH + margin
        }
    }

    override fun isPauseScreen(): Boolean = false

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (PanelKeybind.matches(keyEvent, isTextInputFocused())) {
            onClose()
            return true
        }
        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (uiState.currentModal != null) {
                val modal = uiState.currentModal!!
                if (modal.dismissOnEscape) {
                    modal.onCancel()
                    uiState.popModal()
                }
                return true
            }
            onClose()
            return true
        }
        if (uiState.currentModal != null) return true

        if (currentPage is ConsolePage) {
            if ((currentPage as ConsolePage).keyPressed(keyEvent.key(), 0, keyEvent.modifiers())) return true
        }

        // Keyboard page navigation
        when (keyEvent.key()) {
            GLFW.GLFW_KEY_TAB -> {
                val next =
                    if (keyEvent.modifiers() and GLFW.GLFW_MOD_SHIFT != 0) {
                        uiState.activePage.ordinal - 1
                    } else {
                        uiState.activePage.ordinal + 1
                    }
                val pages = VenusPage.entries
                navigateTo(pages[(next + pages.size) % pages.size])
                return true
            }
        }
        return super.keyPressed(keyEvent)
    }

    private fun isTextInputFocused(): Boolean {
        val focused = focused
        if (focused is net.minecraft.client.gui.components.EditBox) return focused.isFocused
        return false
    }

    override fun mouseClicked(
        mouseButtonEvent: MouseButtonEvent,
        doubleClick: Boolean,
    ): Boolean {
        val mouseX = mouseButtonEvent.x().toInt()
        val mouseY = mouseButtonEvent.y().toInt()

        // Modal captures input
        if (uiState.currentModal != null) {
            return handleModalClick(mouseX, mouseY)
        }

        closeBtn?.let {
            if (it.bounds.contains(mouseX, mouseY)) {
                onClose()
                return true
            }
        }

        sidebar?.hitTest(mouseX, mouseY)?.let { page ->
            navigateTo(page)
            return true
        }

        if (currentPage?.mouseClicked(mouseButtonEvent.x(), mouseButtonEvent.y(), 0) == true) return true

        return super.mouseClicked(mouseButtonEvent, doubleClick)
    }

    private fun handleModalClick(
        mouseX: Int,
        mouseY: Int,
    ): Boolean {
        val modal = uiState.currentModal ?: return false
        val mw = VenusDimensions.MODAL_WIDTH
        val mh = maxOf(VenusDimensions.MODAL_MIN_HEIGHT, font.lineHeight * 6)
        val mb = Bounds(width / 2 - mw / 2, height / 2 - mh / 2, mw, mh)
        val vm = VenusModal(mb, modal)

        val confirmB = vm.confirmBounds(font)
        if (confirmB.contains(mouseX, mouseY)) {
            modal.onConfirm()
            uiState.popModal()
            return true
        }
        val cancelB = vm.cancelBounds(font)
        if (cancelB.contains(mouseX, mouseY)) {
            modal.onCancel()
            uiState.popModal()
            return true
        }
        if (modal.dismissOnOutsideClick && !mb.contains(mouseX, mouseY)) {
            modal.onCancel()
            uiState.popModal()
            return true
        }
        return true
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        if (currentPage?.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) == true) return true
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    private fun navigateTo(page: VenusPage) {
        if (page == uiState.activePage) return
        currentPage?.onLeave()
        uiState.navigateTo(page)
        currentPage = pageFor(page)
        currentPage?.layout(contentBounds)
        currentPage?.onEnter()
        updateWidgetVisibility()
    }

    /**
     * Public API for pushing a confirm modal (used by pages for dangerous actions).
     */
    fun showConfirm(
        title: String,
        message: String,
        onConfirm: () -> Unit,
        kind: ModalKind = ModalKind.CONFIRM,
    ) {
        uiState.pushModal(
            VenusModalRequest(
                kind = kind,
                title = title,
                message = message,
                onConfirm = onConfirm,
            ),
        )
    }

    /**
     * Public API for showing a toast.
     */
    fun showToast(
        kind: ToastKind,
        title: String,
        message: String,
        durationMs: Long = 4000,
    ) {
        val now = System.currentTimeMillis()
        uiState.addToast(
            VenusToastRequest(
                id = toastIdCounter.incrementAndGet(),
                kind = kind,
                title = title,
                message = message,
                createdAtMs = now,
                expireAtMs = now + durationMs,
            ),
        )
    }
}
