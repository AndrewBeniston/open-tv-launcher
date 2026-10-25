package app.opentvlauncher.ui.focus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import app.opentvlauncher.ui.theme.LauncherMotion

/**
 * Google TV's fixed focus position: the focused item stays at the row's start, [margin] from
 * the screen edge, and the row slides under it. Right on the first item brings the second item
 * to where the first one was. Near the end of a row the row cannot slide further, so focus then
 * moves along the last visible items. A row that fits on the screen never slides.
 *
 * Without this, Compose on a TV pivots every focused item to 30 % of the row width.
 */
@OptIn(ExperimentalFoundationApi::class)
private class StartPivotSpec(private val marginPx: Float) : BringIntoViewSpec {
    // Compose 1.12 marks this deprecated but still uses it for the slide; it keeps the
    // design's spring. If a later Compose ignores it, the row slides with Compose's own spring.
    @Deprecated("Compose 1.12: animation spec customization is no longer supported")
    override val scrollAnimationSpec: AnimationSpec<Float> = LauncherMotion.scroll()

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - marginPx
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StartPivotRowScrolling(margin: Dp, content: @Composable () -> Unit) {
    val marginPx = with(LocalDensity.current) { margin.toPx() }
    val spec = remember(marginPx) { StartPivotSpec(marginPx) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
