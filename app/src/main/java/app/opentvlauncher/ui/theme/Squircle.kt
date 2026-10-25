package app.opentvlauncher.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.createBitmap
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Cubic
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * How far each corner's curve runs on into the straight edges (graphics-shapes CornerRounding).
 * 0 is a circular corner. At 0.6 the curvature grows smoothly from the edge, as in a superellipse.
 */
const val SQUIRCLE_SMOOTHING = 0.6f

/**
 * A rectangle with continuous ("squircle") corners. [corner] is the design's corner radius: the
 * radius of the circular arc at the middle of each corner.
 *
 * A side that is too short for the smoothing gets less of it, so a full pill or a dot keeps
 * exact round ends.
 */
@Immutable
class SquircleShape(private val corner: CornerSize) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(squirclePath(size, corner.toPx(size, density)))

    override fun equals(other: Any?) = other is SquircleShape && other.corner == corner

    override fun hashCode() = corner.hashCode()

    override fun toString() = "SquircleShape($corner)"
}

fun SquircleShape(corner: Dp) = SquircleShape(CornerSize(corner))

/** For a corner that animates, in pixels. */
fun SquircleShape(cornerPx: Float) = SquircleShape(CornerSize(cornerPx))

private data class PathKey(val width: Int, val height: Int, val quarterPixels: Int)

/** Enough for every size and corner on screen, with the focus morph's steps. */
private val paths = LruCache<PathKey, Path>(128)

/**
 * The squircle outline for [size] and [cornerPx]. A path is made once for each whole-pixel size
 * and quarter-pixel corner, then shared, so nothing may change a returned path.
 */
fun squirclePath(size: Size, cornerPx: Float): Path {
    val key = PathKey(size.width.roundToInt(), size.height.roundToInt(), (cornerPx * 4).roundToInt())
    return paths.get(key) ?: makePath(key).also { paths.put(key, it) }
}

private fun makePath(key: PathKey): Path {
    val width = key.width.toFloat()
    val height = key.height.toFloat()
    val cubics = RoundedPolygon.rectangle(
        width = width,
        height = height,
        rounding = CornerRounding(radius = key.quarterPixels / 4f, smoothing = SQUIRCLE_SMOOTHING),
        centerX = width / 2,
        centerY = height / 2,
    ).cubics.filterNot { it.isPoint() }

    // Skia draws a convex outline on its fast path, and anything else as a mask made on the CPU,
    // which made each frame slower on the Chromecast. Skia's convexity test has no tolerance, so
    // two fixes keep the outline convex:
    // - A point that lies on a side is put exactly on it, so a side and the curves that run into
    //   it make no turn at all.
    // - Where two curves meet away from the sides, the controls on each side and the shared point
    //   lie on one line, and float rounding can make that a tiny inward turn. That point moves
    //   outward by 1/1024 px, far below what shows and far above the rounding. It never moves
    //   more than half way to a side, or a short curve next to the side would turn outward.
    fun snapX(x: Float) = if (abs(x) < 1e-3f) 0f else if (abs(x - width) < 1e-3f) width else x
    fun snapY(y: Float) = if (abs(y) < 1e-3f) 0f else if (abs(y - height) < 1e-3f) height else y
    val ends = cubics.mapIndexed { i, cubic ->
        val x = snapX(cubic.anchor1X)
        val y = snapY(cubic.anchor1Y)
        val next = cubics[(i + 1) % cubics.size]
        val onSide = x == 0f || x == width || y == 0f || y == height
        val tx = next.control0X - cubic.control1X
        val ty = next.control0Y - cubic.control1Y
        val length = hypot(tx, ty)
        val nudge = minOf(JOIN_NUDGE, x / 2, (width - x) / 2, y / 2, (height - y) / 2)
        if (onSide || cubic.isStraight() || next.isStraight() || length == 0f) {
            Offset(x, y)
        } else {
            Offset(x + ty / length * nudge, y - tx / length * nudge)
        }
    }
    val path = android.graphics.Path()
    path.moveTo(ends.last().x, ends.last().y)
    cubics.forEachIndexed { i, cubic ->
        path.cubicTo(
            snapX(cubic.control0X), snapY(cubic.control0Y),
            snapX(cubic.control1X), snapY(cubic.control1Y),
            ends[i].x, ends[i].y,
        )
    }
    path.close()
    return path.asComposePath()
}

private const val JOIN_NUDGE = 1f / 1024

/** A side with no length, as on a pill's short side, where two corners meet. */
private fun Cubic.isPoint() = abs(anchor0X - anchor1X) < 1e-3f && abs(anchor0Y - anchor1Y) < 1e-3f

/** A straight side: graphics-shapes writes a side as a cubic along one axis. */
private fun Cubic.isStraight() = abs(anchor0X - anchor1X) < 1e-3f || abs(anchor0Y - anchor1Y) < 1e-3f

private data class MaskKey(val width: Int, val height: Int, val corner: Int)

private val masks = object : LruCache<MaskKey, BitmapShader>(4 * 1024 * 1024) {
    override fun sizeOf(key: MaskKey, value: BitmapShader) = key.width * key.height
}

/**
 * A squircle as an alpha mask the size of the surface, with the corner to the nearest pixel.
 *
 * A filled path costs the Chromecast's GPU an edge test on every pixel it covers, and on a card
 * that made each frame slower than a clip. Through a mask, a squircle fill is a plain textured
 * rectangle. Each size and corner gets one mask, shared by every surface of that size.
 */
fun squircleMask(size: Size, cornerPx: Float): BitmapShader {
    val key = MaskKey(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1), cornerPx.roundToInt())
    return masks.get(key) ?: makeMask(key).also { masks.put(key, it) }
}

private fun makeMask(key: MaskKey): BitmapShader {
    val bitmap = createBitmap(key.width, key.height, Bitmap.Config.ALPHA_8)
    val outline = squirclePath(Size(key.width.toFloat(), key.height.toFloat()), key.corner.toFloat())
    Canvas(bitmap).drawPath(outline.asAndroidPath(), Paint(Paint.ANTI_ALIAS_FLAG))
    bitmap.prepareToDraw()
    return BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
}

/** Fills the draw area with [color] in a squircle with a corner of [cornerPx]. */
fun DrawScope.drawSquircle(cornerPx: Float, color: Color) {
    drawRect(ShaderBrush(squircleMask(size, cornerPx)), colorFilter = ColorFilter.tint(color, BlendMode.SrcIn))
}

/**
 * Light from above over a squircle fill: a little lighter at the top, darker at the bottom, so a
 * flat tile reads as a raised surface (24 Oct 2026). It has no edge line or border. The
 * gradient and the mask are one shader, made again only when the size or the corner changes.
 */
class SquircleDepth {
    private var drawnSize = Size.Unspecified
    private var drawnCorner = -1
    private var brush: ShaderBrush? = null

    fun draw(scope: DrawScope, cornerPx: Float) {
        val size = scope.size
        val corner = cornerPx.roundToInt()
        val brush = this.brush.takeIf { size == drawnSize && corner == drawnCorner }
            ?: ShaderBrush(
                ComposeShader(
                    LinearGradientShader(Offset.Zero, Offset(0f, size.height), DepthColors, DepthStops),
                    squircleMask(size, cornerPx),
                    android.graphics.BlendMode.DST_IN,
                ),
            ).also {
                this.brush = it
                drawnSize = size
                drawnCorner = corner
            }
        scope.drawRect(brush)
    }

    private companion object {
        val DepthColors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent, Color.Black.copy(alpha = 0.18f))
        val DepthStops = listOf(0f, 0.45f, 1f)
    }
}
