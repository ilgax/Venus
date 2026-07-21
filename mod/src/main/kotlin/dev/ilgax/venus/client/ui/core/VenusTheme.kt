@file:Suppress("ktlint:standard:property-naming")

package dev.ilgax.venus.client.ui.core

import dev.ilgax.venus.client.ui.profile.UiTheme

/**
 * Centralized Venus theme. One object holds every color, spacing token, and
 * dimension used by the UI kit so pages never scatter magic numbers.
 *
 * Aesthetic: dark, compact control-console. Cyan primary accent. Green/amber/red
 * reserved for semantic states. Thin borders, limited transparency, minimal glow.
 */
object VenusTheme {
    // ---- Backgrounds ----
    var BACKGROUND = 0xFF0A0E13.toInt()
        private set
    var WINDOW = 0xFF121821.toInt()
        private set
    var TOP_BAR = 0xFF0D1219.toInt()
        private set
    var SIDEBAR = 0xFF0F141B.toInt()
        private set
    var SURFACE = 0xFF161D27.toInt()
        private set
    var RAISED = 0xFF1C2430.toInt()
        private set
    var HOVER = 0xFF243040.toInt()
        private set
    var ACTIVE = 0xFF2F6F85.toInt()
        private set
    var BORDER = 0xFF2A3340.toInt()
        private set
    var BORDER_BRIGHT = 0xFF3A4658.toInt()
        private set

    // ---- Accent ----
    var ACCENT = 0xFF2BD9E0.toInt()
        private set
    var ACCENT_DIM = 0xFF1A8B91.toInt()
        private set
    var ACCENT_MUTED = 0xFF147078.toInt()
        private set

    // ---- Text ----
    var TEXT = 0xFFEAF1F8.toInt()
        private set
    var TEXT_MUTED = 0xFF8A99AC.toInt()
        private set
    const val TEXT_DISABLED = 0xFF4C5666.toInt()
    const val TEXT_ACCENT = 0xFF7BEDF2.toInt()

    // ---- Semantic ----
    var SUCCESS = 0xFF3DDC84.toInt()
        private set
    var WARNING = 0xFFFFB454.toInt()
        private set
    var DANGER = 0xFFFF5C6C.toInt()
        private set
    const val DANGER_DIM = 0xFF8A2A33.toInt()

    // ---- Scrim/backdrop ----
    const val SCRIM = 0xAA000000.toInt()
    const val MODAL_SCRIM = 0xB3000000.toInt()

    // ---- Console / monospace accents ----
    const val CONSOLE_INFO = 0xFF7BEDF2.toInt()
    const val CONSOLE_WARN = 0xFFFFB454.toInt()
    const val CONSOLE_ERROR = 0xFFFF5C6C.toInt()
    const val CONSOLE_DEBUG = 0xFF8A99AC.toInt()
    const val CONSOLE_DEFAULT = 0xFFC8D3E0.toInt()
    const val CONSOLE_TIMESTAMP = 0xFF5A6678.toInt()

    internal fun apply(theme: UiTheme) {
        BACKGROUND = theme.background
        WINDOW = theme.window
        TOP_BAR = theme.topBar
        SIDEBAR = theme.navigation
        SURFACE = theme.surface
        RAISED = theme.raised
        HOVER = theme.hover
        ACTIVE = theme.active
        BORDER = theme.border
        BORDER_BRIGHT = theme.border
        ACCENT = theme.accent
        ACCENT_DIM = theme.accent
        ACCENT_MUTED = theme.accent
        TEXT = theme.text
        TEXT_MUTED = theme.textMuted
        SUCCESS = theme.success
        WARNING = theme.warning
        DANGER = theme.danger
    }
}

/**
 * Spacing scale. Small, predictable increments.
 */
object VenusSpacing {
    var XS = 4
        private set
    var SM = 6
        private set
    var MD = 8
        private set
    var LG = 12
        private set
    var XL = 16
        private set
    var XXL = 24
        private set

    internal fun apply(theme: UiTheme) {
        XS = UiThemeRuntime.scaled(4, theme)
        SM = UiThemeRuntime.scaled(6, theme)
        MD = UiThemeRuntime.scaled(8, theme)
        LG = UiThemeRuntime.scaled(12, theme)
        XL = UiThemeRuntime.scaled(16, theme)
        XXL = UiThemeRuntime.scaled(24, theme)
    }
}

/**
 * Standard layout dimensions. All widgets and pages reference these instead of
 * ad-hoc numbers so the layout stays consistent across pages and GUI scales.
 */
object VenusDimensions {
    // Window
    var TOP_BAR_HEIGHT = 36
        private set
    const val TOP_BAR_HEIGHT_COMPACT = 30
    var SIDEBAR_WIDTH = 128
        private set
    const val SIDEBAR_WIDTH_COMPACT = 104
    const val WINDOW_MARGIN = 12
    const val WINDOW_MARGIN_COMPACT = 8
    var CONTENT_PADDING = 12
        private set

    // Rows & items
    var ROW_HEIGHT = 22
        private set
    const val ROW_HEIGHT_COMPACT = 18
    const val ROW_PADDING = 8

    // Controls
    var BUTTON_HEIGHT = 20
        private set
    const val INPUT_HEIGHT = 18
    const val TOGGLE_HEIGHT = 18
    const val TOGGLE_WIDTH = 32
    const val SLIDER_HEIGHT = 20
    const val DROPDOWN_HEIGHT = 20

    // Cards & sections
    var CARD_PADDING = 10
        private set
    const val SECTION_GAP = 10
    const val SECTION_TITLE_GAP = 6

    // Modal
    const val MODAL_WIDTH = 260
    const val MODAL_MIN_HEIGHT = 120
    const val MODAL_PADDING = 14

    // Icons
    const val ICON_SMALL = 12
    const val ICON = 16
    const val ICON_LARGE = 24

    // Scrollbar
    const val SCROLLBAR_WIDTH = 6
    const val SCROLLBAR_HIT_WIDTH = 10
    const val SCROLLBAR_THUMB_MIN = 12
    const val SCROLL_LINES = 3

    // Player head
    const val PLAYER_HEAD_SIZE = 16

    // Compact layout thresholds (screen px before GUI scale)
    const val COMPACT_WIDTH = 900
    const val COMPACT_HEIGHT = 520

    // Bounded histories
    const val CONSOLE_HISTORY_DEFAULT = 500
    const val CONSOLE_HISTORY_MAX = 5000
    const val MAX_COMMAND_HISTORY = 50

    // Animation durations (ms)
    const val ANIM_HOVER_MS = 120f
    const val ANIM_TOGGLE_MS = 110f
    const val ANIM_MODAL_MS = 150f
    const val ANIM_TOAST_MS = 220f
    const val ANIM_SIDEBAR_MS = 160f

    internal fun apply(theme: UiTheme) {
        TOP_BAR_HEIGHT = theme.topBarHeight
        SIDEBAR_WIDTH = theme.navigationSize
        CONTENT_PADDING = theme.contentPadding
        ROW_HEIGHT = theme.rowHeight
        BUTTON_HEIGHT = theme.controlHeight
        CARD_PADDING = theme.cardPadding
    }
}
