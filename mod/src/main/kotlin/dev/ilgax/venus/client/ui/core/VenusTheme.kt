package dev.ilgax.venus.client.ui.core

/**
 * Centralized Venus theme. One object holds every color, spacing token, and
 * dimension used by the UI kit so pages never scatter magic numbers.
 *
 * Aesthetic: dark, compact control-console. Cyan primary accent. Green/amber/red
 * reserved for semantic states. Thin borders, limited transparency, minimal glow.
 */
object VenusTheme {
    // ---- Backgrounds ----
    const val BACKGROUND = 0xFF0A0E13.toInt()
    const val WINDOW = 0xFF121821.toInt()
    const val TOP_BAR = 0xFF0D1219.toInt()
    const val SIDEBAR = 0xFF0F141B.toInt()
    const val SURFACE = 0xFF161D27.toInt()
    const val RAISED = 0xFF1C2430.toInt()
    const val HOVER = 0xFF243040.toInt()
    const val ACTIVE = 0xFF2F6F85.toInt()
    const val BORDER = 0xFF2A3340.toInt()
    const val BORDER_BRIGHT = 0xFF3A4658.toInt()

    // ---- Accent ----
    const val ACCENT = 0xFF2BD9E0.toInt()
    const val ACCENT_DIM = 0xFF1A8B91.toInt()
    const val ACCENT_MUTED = 0xFF147078.toInt()

    // ---- Text ----
    const val TEXT = 0xFFEAF1F8.toInt()
    const val TEXT_MUTED = 0xFF8A99AC.toInt()
    const val TEXT_DISABLED = 0xFF4C5666.toInt()
    const val TEXT_ACCENT = 0xFF7BEDF2.toInt()

    // ---- Semantic ----
    const val SUCCESS = 0xFF3DDC84.toInt()
    const val WARNING = 0xFFFFB454.toInt()
    const val DANGER = 0xFFFF5C6C.toInt()
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
}

/**
 * Spacing scale. Small, predictable increments.
 */
object VenusSpacing {
    const val XS = 4
    const val SM = 6
    const val MD = 8
    const val LG = 12
    const val XL = 16
    const val XXL = 24
}

/**
 * Standard layout dimensions. All widgets and pages reference these instead of
 * ad-hoc numbers so the layout stays consistent across pages and GUI scales.
 */
object VenusDimensions {
    // Window
    const val TOP_BAR_HEIGHT = 36
    const val TOP_BAR_HEIGHT_COMPACT = 30
    const val SIDEBAR_WIDTH = 128
    const val SIDEBAR_WIDTH_COMPACT = 104
    const val WINDOW_MARGIN = 12
    const val WINDOW_MARGIN_COMPACT = 8
    const val CONTENT_PADDING = 12

    // Rows & items
    const val ROW_HEIGHT = 22
    const val ROW_HEIGHT_COMPACT = 18
    const val ROW_PADDING = 8

    // Controls
    const val BUTTON_HEIGHT = 20
    const val INPUT_HEIGHT = 18
    const val TOGGLE_HEIGHT = 18
    const val TOGGLE_WIDTH = 32
    const val SLIDER_HEIGHT = 20
    const val DROPDOWN_HEIGHT = 20

    // Cards & sections
    const val CARD_PADDING = 10
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
}
