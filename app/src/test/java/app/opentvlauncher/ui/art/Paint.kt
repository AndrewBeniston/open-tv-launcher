package app.opentvlauncher.ui.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp

// Small drawing helpers that copy the CSS placeholders in the design's wireframes.
// All positions are fractions of the canvas, as CSS percentages are.

internal fun DrawScope.vertical(vararg stops: Pair<Float, Color>) {
    drawRect(Brush.verticalGradient(*stops))
}

internal fun DrawScope.horizontal(vararg stops: Pair<Float, Color>) {
    drawRect(Brush.horizontalGradient(*stops))
}

/** CSS linear-gradient(135deg, from, to). */
internal fun DrawScope.diagonal(from: Color, to: Color) {
    drawRect(Brush.linearGradient(listOf(from, to), start = Offset.Zero, end = Offset(size.width, size.height)))
}

/** CSS radial-gradient(rx ry at cx cy, color, transparent fade). */
internal fun DrawScope.glow(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, fade: Float = 0.7f) {
    val center = Offset(cx * size.width, cy * size.height)
    val radius = rx * size.width
    val squash = ry * size.height / radius
    scale(scaleX = 1f, scaleY = squash, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to color,
                fade to color.copy(alpha = 0f),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

/** A radial gradient that fills the whole canvas: its last colour continues to the edges. */
internal fun DrawScope.radialFill(cx: Float, cy: Float, rx: Float, ry: Float, vararg stops: Pair<Float, Color>) {
    val center = Offset(cx * size.width, cy * size.height)
    val radius = rx * size.width
    val squash = ry * size.height / radius
    scale(scaleX = 1f, scaleY = squash, pivot = center) {
        drawRect(
            brush = Brush.radialGradient(*stops, center = center, radius = radius),
            topLeft = Offset(0f, center.y - center.y / squash),
            size = Size(size.width, size.height / squash),
        )
    }
}

/** CSS clip-path: polygon(...) inside [box], filled with [brush]. */
internal fun DrawScope.polygon(box: Rect, points: List<Pair<Float, Float>>, brush: Brush, alpha: Float = 1f) {
    val path = Path().apply {
        points.forEachIndexed { i, (x, y) ->
            val px = box.left + x * box.width
            val py = box.top + y * box.height
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    drawPath(path, brush, alpha = alpha)
}

internal fun DrawScope.box(left: Float, top: Float, width: Float, height: Float) =
    Rect(left * size.width, top * size.height, (left + width) * size.width, (top + height) * size.height)

/** A dark tree trunk, as the `.tr` element in the wireframes. */
internal data class Trunk(val left: Float, val width: Float, val alpha: Float)

internal fun DrawScope.trunks(trunks: List<Trunk>) {
    trunks.forEach { t ->
        val r = box(t.left, -0.04f, t.width, 1.04f)
        polygon(
            box = r,
            points = listOf(0.28f to 0f, 0.72f to 0f, 1f to 1f, 0f to 1f),
            brush = Brush.verticalGradient(
                0f to Color(0xF5030807),
                0.7f to Color(0xE0060E0D),
                1f to Color(0x59060E0D),
                startY = r.top,
                endY = r.bottom,
            ),
            alpha = t.alpha,
        )
    }
}

/** Light shafts between the trees in Tales of the Quiet Coast. */
internal fun DrawScope.beam(left: Float, width: Float) {
    val r = box(left, 0.30f, width, 0.16f)
    rotate(degrees = -10f, pivot = r.center) {
        polygon(
            box = r,
            points = listOf(0f to 0.46f, 1f to 0f, 1f to 1f, 0f to 0.54f),
            brush = Brush.horizontalGradient(
                0f to Color(0x00E6F5F0),
                1f to Color(0x38E6F5F0),
                startX = r.left,
                endX = r.right,
            ),
        )
    }
}

/** A repeating grid of small dots: film grain, snow or city lights. */
internal fun DrawScope.dots(spacing: Dp, radius: Dp, color: Color, offsetX: Dp = Dp(0f), offsetY: Dp = Dp(0f)) {
    val step = spacing.toPx()
    val r = radius.toPx()
    var y = offsetY.toPx()
    while (y < size.height) {
        var x = offsetX.toPx()
        while (x < size.width) {
            drawCircle(color, r, Offset(x, y))
            x += step
        }
        y += step
    }
}
