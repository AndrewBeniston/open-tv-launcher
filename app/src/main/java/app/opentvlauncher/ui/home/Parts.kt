package app.opentvlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LocalLauncherType
import app.opentvlauncher.ui.theme.SquircleShape

/** Amber fill, a 3 dp gap, then the track. Amber means progress and nothing else. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Spacer(
        modifier.drawBehind {
            val gap = 3.dp.toPx()
            val radius = CornerRadius(size.height / 2f)
            val fill = (size.width * progress.coerceIn(0f, 1f)).coerceAtLeast(size.height)
            drawRoundRect(LauncherColors.primary, size = Size(fill, size.height), cornerRadius = radius)
            val trackStart = fill + gap
            if (trackStart < size.width) {
                drawRoundRect(
                    LauncherColors.track,
                    topLeft = Offset(trackStart, 0f),
                    size = Size(size.width - trackStart, size.height),
                    cornerRadius = radius,
                )
            }
        },
    )
}

/** The play triangle from the design's buttons (Material "play_arrow"). */
@Composable
fun PlayIcon(color: ColorProducer, modifier: Modifier = Modifier) {
    Spacer(
        modifier.drawBehind {
            val unit = size.minDimension / 24f
            val path = Path().apply {
                moveTo(8f * unit, 5f * unit)
                lineTo(8f * unit, 19f * unit)
                lineTo(19f * unit, 12f * unit)
                close()
            }
            drawPath(path, color())
        },
    )
}

/**
 * The quiet chip next to the clock (design, "Trakt offline"). It says what is wrong and when
 * the next try is. It never takes focus.
 */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(28.dp)
            .background(LauncherColors.surfaceContainerHigh, SquircleShape(14.dp))
            .padding(start = 10.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CloudOffIcon(Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Label(text, LocalLauncherType.current.chip, LauncherColors.onSurfaceVariant)
    }
}

private val cloudOff = listOf(
    "M7 18h10.5a3.5 3.5 0 0 0 .6-6.95A6 6 0 0 0 7.2 9.1 4.5 4.5 0 0 0 7 18z",
    "M4 4l16 16",
)

/** The design's cloud with a line through it, drawn on a 24-unit grid. */
@Composable
private fun CloudOffIcon(modifier: Modifier = Modifier) {
    val paths = remember { cloudOff.map { PathParser().parsePathString(it).toPath() } }
    Spacer(
        modifier.drawBehind {
            val unit = size.minDimension / 24f
            scale(unit, unit, pivot = Offset.Zero) {
                paths.forEach { drawPath(it, LauncherColors.onSurfaceVariant, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            }
        },
    )
}
