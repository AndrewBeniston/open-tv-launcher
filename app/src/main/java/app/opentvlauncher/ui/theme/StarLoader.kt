package app.opentvlauncher.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.cos
import kotlin.math.sin

/** The design's waiting mark: a 20-point star that turns slowly while the TV waits. */
@Composable
fun StarLoader(spinning: Boolean, modifier: Modifier = Modifier) {
    val turn = if (spinning) {
        val transition = rememberInfiniteTransition(label = "loader")
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    } else {
        null
    }
    Spacer(
        modifier
            .graphicsLayer { rotationZ = 12f + (turn?.value ?: 0f) }
            .drawBehind {
                val star = Path()
                val points = 20
                for (i in 0 until points) {
                    val radius = if (i % 2 == 0) size.minDimension / 2 else size.minDimension * 0.4f
                    val angle = Math.PI * 2 * i / points - Math.PI / 2
                    val x = center.x + radius * cos(angle).toFloat()
                    val y = center.y + radius * sin(angle).toFloat()
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                drawPath(star, LauncherColors.onSurfaceVariant)
            },
    )
}
