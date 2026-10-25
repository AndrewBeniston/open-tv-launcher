package app.opentvlauncher.ui.art

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.get
import androidx.core.graphics.scale
import app.opentvlauncher.data.Art
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A fade baked into the bitmap. The screen then draws one image, and no scrims on top of it.
 * On the Chromecast's GPU the two full-width scrims cost about 7 ms of each frame.
 */
enum class ArtMask(private val paint: DrawScope.() -> Unit) {
    None({}),

    /**
     * The banner art: clear at the left and the bottom, as the design's CSS masks on `.hero`, and
     * darker at the top, so the bar sits on dark ground over bright art (25 Oct 2026).
     */
    Hero({
        drawRect(
            Brush.horizontalGradient(0.14f to Color.Transparent, 0.36f to Color.Black.copy(alpha = 0.5f), 0.62f to Color.Black),
            blendMode = BlendMode.DstIn,
        )
        drawRect(Brush.verticalGradient(0.48f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        drawRect(Brush.verticalGradient(*TopShade, endY = size.height * TOP_SHADE_DEPTH), blendMode = BlendMode.SrcAtop)
    }),

    /** The details page: full screen, clear at the left and in the bottom 30 % (design, `.hero.full`). */
    HeroFull({
        drawRect(
            Brush.horizontalGradient(0.14f to Color.Transparent, 0.36f to Color.Black.copy(alpha = 0.5f), 0.62f to Color.Black),
            blendMode = BlendMode.DstIn,
        )
        drawRect(Brush.verticalGradient(0.70f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }),

    /** The Stremio hand-off: the art at 42 % brightness, darker again at the left for the text. */
    HandOff({
        drawRect(Color.Black.copy(alpha = 0.58f))
        drawRect(Brush.horizontalGradient(0f to Color(0xEB07080A), 0.45f to Color(0x8C07080A), 0.75f to Color(0x0007080A)))
    }),

    /** The screensaver: the art at 45 % brightness and 85 % saturation (design, `.ss`). */
    Screensaver({
        drawRect(Color(0xFF808080).copy(alpha = 0.15f), blendMode = BlendMode.Saturation)
        drawRect(Color.Black.copy(alpha = 0.55f))
    }),

    /** A title logo as one colour: the design's logo white at 70 %. */
    Silhouette({
        drawRect(Color(0xB3F3EFE6), blendMode = BlendMode.SrcIn)
    }),

    /** A Continue Watching card: darker at the bottom, so its progress bar reads on any art. */
    BottomShade({
        drawRect(Brush.verticalGradient(0.54f to Color.Transparent, 1f to Color(0x9E000000)))
    }),
    ;

    fun draw(scope: DrawScope) = scope.paint()
}

/**
 * Google TV's own shade over the top of its home screen, copied from its launcher: #0E0E0F from
 * 70 % at the top edge, eased to clear 360 dp down, two thirds of the screen. The banner art is
 * 420 dp tall and starts at the top edge.
 */
private const val TOP_SHADE_DEPTH = 360f / 420f
private val TopShade = listOf(
    0f to 0xB3, 0.011f to 0xAB, 0.031f to 0xA3, 0.056f to 0x96, 0.092f to 0x8A, 0.139f to 0x78, 0.197f to 0x69,
    0.267f to 0x57, 0.35f to 0x45, 0.447f to 0x36, 0.561f to 0x26, 0.689f to 0x17, 0.836f to 0x0A, 1f to 0x00,
).map { (at, alpha) -> at to Color(0x0E0E0F or (alpha shl 24)) }.toTypedArray()

/**
 * The finished bitmaps for the screen, in memory: art cropped (or fitted) to the size it is
 * drawn, with its fade baked in, made off the main thread.
 *
 * - Painted art is rendered at half resolution. A placeholder gradient is soft, so it looks the
 *   same and uses a quarter of the memory.
 * - Real art comes from [ArtLoader], one address after another until one works, at full
 *   resolution but never larger than the source. Its content tint is kept in [tint].
 */
object ArtCache {
    private const val PAINTED_RESOLUTION = 0.5f
    private const val RETRY_MILLIS = 10L * 60 * 1000

    /** [fit]: the whole image inside the box, as a logo. Otherwise the image fills and crops. */
    data class Key(val art: Art, val width: Int, val height: Int, val density: Float, val mask: ArtMask, val fit: Boolean = false)

    private val cache = object : LruCache<Key, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: Key, value: ImageBitmap) = value.width * value.height * 4
    }

    /** When each art last failed, so a missing image is not fetched again on every scroll. */
    private val failures = LruCache<Art, Long>(256)

    private val tints = mutableStateMapOf<Art, Color>()

    /** The page tint while [art] has focus: known once its bitmap has loaded. */
    fun tint(art: Art): Color = art.painted?.tint ?: tints[art] ?: Art.NeutralTint

    /** Null when [art] has nothing to draw. */
    fun key(art: Art, size: DpSize, density: Density, mask: ArtMask = ArtMask.None, fit: Boolean = false): Key? {
        if (art.painted == null && art.urls.isEmpty()) return null
        val resolution = if (art.painted != null) PAINTED_RESOLUTION else 1f
        return with(density) {
            Key(
                art = art,
                width = (size.width.toPx() * resolution).roundToInt().coerceAtLeast(1),
                height = (size.height.toPx() * resolution).roundToInt().coerceAtLeast(1),
                density = this.density * resolution,
                mask = mask,
                fit = fit,
            )
        }
    }

    fun key(art: PaintedArt, size: DpSize, density: Density, mask: ArtMask = ArtMask.None): Key = key(Art(art), size, density, mask)!!

    fun peek(key: Key): ImageBitmap? = cache.get(key)

    /** The bitmap for [key], or null when no address works or there is no [loader]. */
    suspend fun load(key: Key, loader: ArtLoader?): ImageBitmap? {
        cache.get(key)?.let { return it }
        val painted = key.art.painted
        if (painted != null) return withContext(Dispatchers.Default) { renderPainted(key, painted).also { cache.put(key, it) } }
        if (loader == null) return null
        failures.get(key.art)?.let { if (SystemClock.elapsedRealtime() - it < RETRY_MILLIS) return null }
        for (url in key.art.urls) {
            val source = runCatching { loader.bitmap(url, key.width, key.height, key.fit) }.getOrNull() ?: continue
            val (bitmap, tint) = withContext(Dispatchers.Default) {
                render(key, source) to if (key.fit) null else ArtTint.of(sample(source))
            }
            cache.put(key, bitmap)
            if (tint != null) tints[key.art] = tint
            return bitmap
        }
        failures.put(key.art, SystemClock.elapsedRealtime())
        return null
    }

    private fun renderPainted(key: Key, painted: PaintedArt): ImageBitmap {
        val bitmap = ImageBitmap(key.width, key.height)
        CanvasDrawScope().draw(
            density = Density(key.density),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap),
            size = Size(key.width.toFloat(), key.height.toFloat()),
        ) {
            painted.draw(this)
            key.mask.draw(this)
        }
        bitmap.prepareToDraw()
        return bitmap
    }

    /**
     * Crops [source] to the key's shape (or fits it inside), scales it to the drawn size and
     * bakes in the mask. A source smaller than the drawn size is not enlarged here: the
     * screen scales the bitmap up as it draws it.
     */
    private fun render(key: Key, source: Bitmap): ImageBitmap {
        val sw = source.width.toFloat()
        val sh = source.height.toFloat()
        val crop: Rect
        val scale: Float
        if (key.fit) {
            crop = Rect(0, 0, source.width, source.height)
            scale = min(1f, min(key.width / sw, key.height / sh))
        } else {
            // Black bars baked into the picture are left out first.
            val rows = letterbox(source.height) { source.darkRow(it) }
            val rh = rows.count().toFloat()
            val aspect = key.width.toFloat() / key.height
            val cw = min(sw, rh * aspect)
            val ch = cw / aspect
            val left = ((sw - cw) / 2).roundToInt()
            val top = rows.first + ((rh - ch) / 2).roundToInt()
            crop = Rect(left, top, left + cw.roundToInt(), top + ch.roundToInt())
            scale = min(1f, key.width / cw)
        }
        val width = max(1, (crop.width() * scale).roundToInt())
        val height = max(1, (crop.height() * scale).roundToInt())
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.nativeCanvas.drawBitmap(source, crop, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        CanvasDrawScope().draw(Density(key.density), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
            key.mask.draw(this)
        }
        bitmap.prepareToDraw()
        return bitmap
    }

    /** True when 32 points across row [y] are all opaque and nearly black. */
    private fun Bitmap.darkRow(y: Int): Boolean = (0 until 32).all { i ->
        val c = this[(2 * i + 1) * width / 64, y]
        c ushr 24 > 250 && (c shr 16 and 0xFF) < 24 && (c shr 8 and 0xFF) < 24 && (c and 0xFF) < 24
    }

    /** A 16 × 9 copy of the picture's pixels, enough for its average hue. */
    private fun sample(source: Bitmap): IntArray {
        val small = source.scale(16, 9)
        return IntArray(16 * 9).also { small.getPixels(it, 0, 16, 0, 0, 16, 9) }
    }
}

/**
 * The rows of a picture [height] rows tall between black bars baked in at the top and the
 * bottom, as on Upgrade's Metahub backdrop. Bars count only when both are there, each at least
 * 1 % and at most a quarter of the height, and about as tall as each other, so a dark sky at the
 * top of a picture stays.
 */
internal fun letterbox(height: Int, darkRow: (Int) -> Boolean): IntRange {
    val all = 0 until height
    val limit = height / 4
    val top = (0 until limit).firstOrNull { !darkRow(it) } ?: return all
    val bottom = (0 until limit).firstOrNull { !darkRow(height - 1 - it) } ?: return all
    val least = max(2, height / 100)
    val skew = max(2, height / 40)
    if (top < least || bottom < least || abs(top - bottom) > skew) return all
    return top until height - bottom
}
