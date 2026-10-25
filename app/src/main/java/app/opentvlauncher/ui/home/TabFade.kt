package app.opentvlauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import app.opentvlauncher.ui.theme.LauncherMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The crossfade between tab pages (24 Oct 2026). After the tab changes, the pages it left
 * ([outgoing]) stay composed while they fade out and the new page fades in; then only the new
 * page is composed. Each page fades from the opacity it has, so a change back during a fade
 * turns it round without a jump. Opacity only, read as the pages draw, so the fade adds no
 * layout or composition work of its own.
 */
@Stable
class TabFade internal constructor(
    private val tab: Tab,
    private val alphas: SnapshotStateMap<Tab, Animatable<Float, AnimationVector1D>>,
) {
    /** The pages still fading out, to compose under the shown page. */
    val outgoing: List<Tab> get() = alphas.keys.filter { it != tab }

    /** [page]'s opacity now. Read it while drawing. */
    fun alpha(page: Tab): Float = alphas[page]?.value ?: 0f
}

@Composable
fun rememberTabFade(tab: Tab): TabFade {
    val alphas = remember { mutableStateMapOf(tab to Animatable(1f)) }
    LaunchedEffect(tab) {
        // A new page starts unseen. One that is still fading turns round from where it is, at
        // rest, so its opacity never dips the wrong way first.
        alphas.getOrPut(tab) { Animatable(0f) }
        coroutineScope {
            alphas.forEach { (page, alpha) ->
                launch { alpha.animateTo(if (page == tab) 1f else 0f, LauncherMotion.effectsSlow(), initialVelocity = 0f) }
            }
        }
        alphas.keys.filter { it != tab }.forEach(alphas::remove)
    }
    return remember(tab) { TabFade(tab, alphas) }
}

/**
 * A tab page's opacity, [alpha] read as it draws. Part-way through a fade the page draws through a
 * layer that Android keeps between frames, so each frame blends the page as it is, rather than
 * drawing the whole page again into a new buffer (speed check, perf-1). It is whole-page opacity
 * either way, so the fade looks the same. At rest there is no layer.
 */
fun Modifier.pageFade(alpha: () -> Float): Modifier = graphicsLayer {
    val a = alpha()
    this.alpha = a
    compositingStrategy = if (a > 0f && a < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
}
