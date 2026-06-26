package dev.ilgax.venus.client.ui.core

import kotlin.math.roundToInt

/**
 * Immutable integer rectangle used throughout the Venus UI kit.
 * Mirrors the ad-hoc `Rect`/`Bounds` used by the legacy tabs but shared and typed.
 */
data class Bounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2

    fun contains(
        px: Int,
        py: Int,
    ): Boolean = px >= x && px < right && py >= y && py < bottom

    fun contains(
        px: Double,
        py: Double,
    ): Boolean = contains(px.toInt(), py.toInt())

    fun inset(
        horizontal: Int = 0,
        vertical: Int = 0,
    ): Bounds = Bounds(x + horizontal, y + vertical, width - horizontal * 2, height - vertical * 2)

    fun inset(all: Int): Bounds = inset(all, all)

    fun shrink(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ): Bounds = Bounds(x + left, y + top, width - left - right, height - top - bottom)

    fun withWidth(newWidth: Int): Bounds = copy(width = newWidth)

    fun withHeight(newHeight: Int): Bounds = copy(height = newHeight)

    fun moveTo(
        newX: Int,
        newY: Int,
    ): Bounds = copy(x = newX, y = newY)

    fun translate(
        dx: Int,
        dy: Int,
    ): Bounds = copy(x = x + dx, y = y + dy)
}

/**
 * Mutable builder for row/column layout passes. Not render-thread critical:
 * layout is recomputed in [net.minecraft.client.gui.screens.Screen.init] and on
 * resize, never every frame.
 */
class LayoutCursor(
    var x: Int,
    var y: Int,
) {
    fun row(height: Int): Bounds {
        val b = Bounds(x, y, 0, height)
        y += height
        return b
    }

    fun advance(dx: Int) {
        x += dx
    }

    fun newline(dy: Int) {
        y += dy
    }
}

/**
 * Linear interpolation helpers that keep widget animations framerate-independent.
 */
fun lerp(
    a: Float,
    b: Float,
    t: Float,
): Float = a + (b - a) * t

fun lerp(
    a: Int,
    b: Int,
    t: Float,
): Int = (a + (b - a) * t).roundToInt()

fun clamp(
    value: Int,
    min: Int,
    max: Int,
): Int = value.coerceIn(min, max)

fun clamp(
    value: Float,
    min: Float,
    max: Float,
): Float = value.coerceIn(min, max)

fun clamp(
    value: Double,
    min: Double,
    max: Double,
): Double = value.coerceIn(min, max)
