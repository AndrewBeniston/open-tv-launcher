package app.opentvlauncher.ui.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * The content tint: a wash in the focused art's hue over the top of the page (design, "Colour
 * roles"). It copies the wireframe's `radial-gradient(78.125cqw 46.875cqw at 72% 8%, tint,
 * transparent 70%)` and sits under the banner art, as in the wireframe.
 *
 * A live radial gradient across the screen cost about 21 ms of each frame on the Chromecast.
 * This draws a small alpha bitmap, made once, tinted as it is drawn, and only over the part of
 * the screen where the wash is visible. The sign-in screen moves its centre to the right.
 */
@Composable
fun TintWash(tint: () -> Color, modifier: Modifier = Modifier, centerX: Float = 0.72f, centerY: Float = 0.08f) {
    val falloff = remember { radialFalloff() }
    Spacer(
        modifier.drawBehind {
            // The wash is clear beyond 70 % of its radius, so the bitmap covers only that ellipse.
            val rx = 0.78125f * size.width * 0.7f
            val ry = 0.46875f * size.width * 0.7f
            val cx = centerX * size.width
            val cy = centerY * size.height
            drawImage(
                image = falloff,
                dstOffset = IntOffset((cx - rx).roundToInt(), (cy - ry).roundToInt()),
                dstSize = IntSize((2 * rx).roundToInt(), (2 * ry).roundToInt()),
                colorFilter = ColorFilter.tint(tint(), BlendMode.SrcIn),
                filterQuality = FilterQuality.Low,
            )
        },
    )
}

/** Opaque at the centre, clear at the edge of the ellipse that fills the bitmap. */
private fun radialFalloff(): ImageBitmap {
    val width = 256
    val height = 154
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        val center = Offset(width / 2f, height / 2f)
        val radius = width / 2f
        scale(scaleX = 1f, scaleY = height.toFloat() / width, pivot = center) {
            drawCircle(
                Brush.radialGradient(0f to Color.White, 1f to Color.White.copy(alpha = 0f), center = center, radius = radius),
                radius = radius,
                center = center,
            )
        }
    }
    bitmap.prepareToDraw()
    return bitmap
}
