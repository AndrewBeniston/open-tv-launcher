package app.opentvlauncher.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Fades a node by drawing each of its parts with [alpha]. A plain alpha layer first renders the
 * node into an offscreen texture. On the Chromecast that cost 8 to 23 ms on each frame where the
 * row titles started to fade in.
 *
 * Use it where the parts do not overlap, or where a part that shows through another part for
 * the length of a fade does not matter.
 */
fun Modifier.fade(alpha: () -> Float): Modifier = graphicsLayer {
    this.alpha = alpha()
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

/**
 * Compose's Crossfade, with [fade] in place of its alpha layer. The two states fade at the same
 * time, as in Crossfade. Each state's parts must not overlap each other.
 */
@Composable
fun <T> FlatCrossfade(
    targetState: T,
    animationSpec: FiniteAnimationSpec<Float>,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val transition = updateTransition(targetState, label)
    val shown = remember { mutableStateListOf(transition.currentState) }
    if (transition.currentState == transition.targetState && (shown.size != 1 || shown[0] != transition.targetState)) {
        shown.retainAll { it == transition.targetState }
    }
    if (transition.targetState !in shown) shown.add(transition.targetState)
    Box(modifier) {
        shown.forEach { state ->
            key(state) {
                val alpha by transition.animateFloat({ animationSpec }, label = label) { if (it == state) 1f else 0f }
                Box(Modifier.fade { alpha }) { content(state) }
            }
        }
    }
}
