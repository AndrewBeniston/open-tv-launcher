package app.opentvlauncher.system

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * An app's mark alone, with no box round it (25 Oct 2026: the icons on the Stremio and
 * Netflix buttons had a square behind them). An adaptive icon, such as Stremio's, draws its
 * background layer as a square on the TV, so only its foreground layer is used. A plain icon
 * loses the background that touches its edges.
 * The mark is then trimmed to its own bounds and fitted, centred, in a square of the icon's size.
 */
object AppMark {
    /** [icon]'s mark in a square of [px]. An icon that is all background is kept as it is. */
    fun of(icon: Drawable, px: Int): Bitmap {
        // Drawn larger than shown, so the trimmed mark keeps clean edges when it is scaled down.
        val size = px * DETAIL
        val drawn = createBitmap(size, size)
        val canvas = Canvas(drawn)
        val foreground = (icon as? AdaptiveIconDrawable)?.foreground
        if (foreground != null) {
            // An adaptive icon's layers are larger than the icon, which shows only their middle.
            val extra = (size * AdaptiveIconDrawable.getExtraInsetFraction()).roundToInt()
            foreground.setBounds(-extra, -extra, size + extra, size + extra)
            foreground.draw(canvas)
        } else {
            icon.setBounds(0, 0, size, size)
            icon.draw(canvas)
            clearEdgeBackground(drawn)
        }
        val bounds = markBounds(drawn) ?: return icon.toBitmap(px, px)
        return fit(drawn, bounds, px)
    }

    /**
     * Makes the background clear: the opaque colour of the top left corner, and every pixel near
     * it that joins the edge through pixels near it. A pixel part way between the background
     * and the mark, on the mark's edge, keeps the mark's colour with the matching opacity.
     */
    private fun clearEdgeBackground(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val background = pixels[0]
        if (Color.alpha(background) < 255) return
        val reached = BooleanArray(pixels.size)
        val queue = ArrayDeque<Int>()
        for (x in 0 until width) {
            queue += x
            queue += (height - 1) * width + x
        }
        for (y in 0 until height) {
            queue += y * width
            queue += y * width + width - 1
        }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            if (reached[i] || !near(pixels[i], background)) continue
            reached[i] = true
            pixels[i] = withoutBackground(pixels[i], background)
            val x = i % width
            val y = i / width
            if (x > 0) queue += i - 1
            if (x < width - 1) queue += i + 1
            if (y > 0) queue += i - width
            if (y < height - 1) queue += i + width
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun near(pixel: Int, background: Int): Boolean =
        Color.alpha(pixel) == 255 &&
            abs(Color.red(pixel) - Color.red(background)) <= NEAR &&
            abs(Color.green(pixel) - Color.green(background)) <= NEAR &&
            abs(Color.blue(pixel) - Color.blue(background)) <= NEAR

    /** [pixel] with [background] taken out: the colour that, over the background, gives the pixel. */
    private fun withoutBackground(pixel: Int, background: Int): Int {
        val channels = listOf(Color::red, Color::green, Color::blue)
        val alpha = channels.maxOf { channel ->
            val c = channel(pixel) / 255f
            val b = channel(background) / 255f
            when {
                c > b -> (c - b) / (1 - b)
                c < b -> (b - c) / b
                else -> 0f
            }
        }
        if (alpha <= 0f) return Color.TRANSPARENT
        val (r, g, b) = channels.map { channel ->
            val c = channel(pixel) / 255f
            val back = channel(background) / 255f
            (((c - back) / alpha + back).coerceIn(0f, 1f) * 255).roundToInt()
        }
        return Color.argb((alpha * 255).roundToInt(), r, g, b)
    }

    /** The bounds of the pixels that show, or null when none do. */
    private fun markBounds(bitmap: Bitmap): Rect? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (Color.alpha(pixels[y * width + x]) <= SHOWS) continue
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
        }
        return if (right < 0) null else Rect(left, top, right + 1, bottom + 1)
    }

    private fun fit(bitmap: Bitmap, bounds: Rect, px: Int): Bitmap {
        val scale = minOf(px.toFloat() / bounds.width(), px.toFloat() / bounds.height())
        val width = (bounds.width() * scale).roundToInt().coerceIn(1, px)
        val height = (bounds.height() * scale).roundToInt().coerceIn(1, px)
        val left = (px - width) / 2
        val top = (px - height) / 2
        val out = createBitmap(px, px)
        Canvas(out).drawBitmap(bitmap, bounds, Rect(left, top, left + width, top + height), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private const val DETAIL = 4

    /** How far from the background colour a pixel may be, in each channel, and still be background. */
    private const val NEAR = 150

    /** Alpha at or below this does not count as part of the mark. */
    private const val SHOWS = 8
}
