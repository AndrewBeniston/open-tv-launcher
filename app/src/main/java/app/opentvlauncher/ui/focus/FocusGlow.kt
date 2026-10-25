package app.opentvlauncher.ui.focus

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import app.opentvlauncher.data.Art
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The focus glow prototype (Up Next exploration, "Focus that is easier to see"): a soft light
 * in the art's colour round the focused card, poster or tile, as Google TV draws next to its
 * focused card. It is light, not an outline. Only the focused item glows, and the glow fades in
 * with the focus scale.
 *
 * The blurred shape is made once per item size on the CPU (BlurMaskFilter on a bitmap canvas),
 * because RenderEffect needs API 31 and the Philips runs API 30. Each frame then draws one
 * tinted image. Its cost on the Chromecast is measured before it ships; [ENABLED] turns it off.
 */
object FocusGlow {
    const val ENABLED = true

    /** 42 % at the source. */
    const val STRENGTH = 0.42f

    /** How far the light reaches past the item: the design's 48 dp blur. */
    val reach = 48.dp

    /** The light sits 4 dp lower than the item. */
    val drop = 4.dp

    private val WarmWhite = Color(0xFFECE9E3)

    /**
     * The glow colour for art whose page tint is [tint], or a tile's tone: its hue, at least 55 %
     * lightness and 40 % saturation. Grey art, or art whose tint is not known yet, glows warm
     * white at half strength (the colour's alpha).
     */
    fun colour(tint: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(tint.copy(alpha = 1f).toArgb(), hsl)
        if (tint == Art.NeutralTint || hsl[1] < GREY_SATURATION) return WarmWhite.copy(alpha = 0.5f)
        return Color.hsl(hsl[0], max(hsl[1], 0.40f), max(hsl[2], 0.55f))
    }

    private const val GREY_SATURATION = 0.12f

    private data class Key(val width: Int, val height: Int, val corner: Int, val reach: Int)

    private val images = object : LruCache<Key, ImageBitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: Key, value: ImageBitmap) = value.width * value.height
    }

    /** The item's shape, blurred, with [reachPx] of room on every side: one alpha mask per size. */
    private fun image(size: Size, cornerPx: Float, reachPx: Float): ImageBitmap {
        val key = Key(size.width.roundToInt(), size.height.roundToInt(), cornerPx.roundToInt(), reachPx.roundToInt())
        images.get(key)?.let { return it }
        val bitmap = createBitmap(key.width + 2 * key.reach, key.height + 2 * key.reach, Bitmap.Config.ALPHA_8)
        // A Gaussian's light fades out over about two sigma, so sigma is half the reach.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(key.reach / 2f / 0.57735f, BlurMaskFilter.Blur.NORMAL) }
        val r = key.reach.toFloat()
        Canvas(bitmap).drawRoundRect(r, r, r + key.width, r + key.height, cornerPx, cornerPx, paint)
        bitmap.prepareToDraw()
        return bitmap.asImageBitmap().also { images.put(key, it) }
    }

    /** Draws the glow behind an item of this draw scope's size, at [strength] (0 to 1) of full. */
    fun draw(scope: DrawScope, colour: Color, cornerPx: Float, strength: Float) = with(scope) {
        val reachPx = reach.toPx()
        drawImage(
            image(size, cornerPx, reachPx),
            topLeft = Offset(-reachPx, -reachPx + drop.toPx()),
            alpha = (STRENGTH * strength * colour.alpha).coerceIn(0f, 1f),
            colorFilter = ColorFilter.tint(colour.copy(alpha = 1f), BlendMode.SrcIn),
        )
    }
}
