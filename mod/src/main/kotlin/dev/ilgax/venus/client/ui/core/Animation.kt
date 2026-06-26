package dev.ilgax.venus.client.ui.core

/**
 * Delta-time driven animation utility. A single [Animation] advances a value
 * from 0..1 toward a [target] at a fixed rate, independent of frame rate.
 *
 * Usage:
 *   val hover = Animation(durationMs = 120f)
 *   hover.target = if (hovered) 1f else 0f
 *   hover.tick(partialTick)  // or a measured delta
 *   val t = hover.value
 *
 * Designed for subtle hover fades, sidebar selection movement, modal open,
 * toast entrance/exit, toggle position. Not a generic property animator.
 */
class Animation(
    private val durationMs: Float,
    private val reducedMotion: () -> Boolean = { false },
) {
    private var current: Float = 0f
    private var goal: Float = 0f

    var target: Float
        get() = goal
        set(value) {
            goal = value.coerceIn(0f, 1f)
        }

    val value: Float
        get() = if (reducedMotion()) snapToTarget() else current

    val isComplete: Boolean
        get() = current == goal

    fun setImmediate(v: Float) {
        current = v.coerceIn(0f, 1f)
        goal = current
    }

    /**
     * Advance by [deltaMs] milliseconds (use real elapsed time, not partialTick).
     * Returns the new value.
     */
    fun tick(deltaMs: Float): Float {
        if (reducedMotion()) return snapToTarget()
        if (current == goal) return current
        if (durationMs <= 0f) {
            current = goal
            return current
        }
        val direction = if (goal > current) 1 else -1
        val step = (deltaMs / durationMs) * direction
        current =
            if (direction > 0) {
                (current + step).coerceAtMost(goal)
            } else {
                (current + step).coerceAtLeast(goal)
            }
        return current
    }

    /**
     * Convenience for callers that only have the render partial tick. Advances
     * by approximately one frame (~16.67ms). Prefer [tick] with a real delta
     * for accuracy.
     */
    fun tickFrame(): Float = tick(FRAME_MS)

    private fun snapToTarget(): Float {
        current = goal
        return current
    }

    companion object {
        private const val FRAME_MS = 16.667f
    }
}

/**
 * Tracks the last [System.nanoTime] snapshot and computes elapsed milliseconds
 * between [deltaMs] calls. Use one instance per screen to feed all animations.
 */
class FrameTimer {
    private var lastNanos: Long = System.nanoTime()

    fun deltaMs(): Float {
        val now = System.nanoTime()
        val elapsed = (now - lastNanos) / 1_000_000f
        lastNanos = now
        return elapsed.coerceAtMost(MAX_DELTA_MS)
    }

    fun reset() {
        lastNanos = System.nanoTime()
    }

    private companion object {
        const val MAX_DELTA_MS = 100f
    }
}
