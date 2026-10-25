package app.opentvlauncher.ui.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.opentvlauncher.ui.home.HomeLayout
import app.opentvlauncher.ui.theme.LauncherElevation
import app.opentvlauncher.ui.theme.LauncherMotion
import app.opentvlauncher.ui.theme.SquircleDepth
import app.opentvlauncher.ui.theme.Tone
import app.opentvlauncher.ui.theme.drawSquircle

private val NoTone = Tone(Color.Transparent, Color.Transparent)

/** The content of a [FocusSurface]. Content that reaches the edges draws itself with [corner]. */
interface FocusSurfaceScope : BoxScope {
    /** The squircle corner in pixels now, part-way through the morph while focus moves. Read it while drawing. */
    val corner: () -> Float

    /**
     * How far the fill is from its rest tone (0) to its focused tone (1) now. Content whose colour
     * depends on the fill behind it reads this while drawing, so the two never part.
     */
    val fill: () -> Float
}

private class SurfaceScope(box: BoxScope, override val corner: () -> Float, override val fill: () -> Float) :
    FocusSurfaceScope,
    BoxScope by box

/**
 * The design's focus treatment for tiles, cards and posters: scale, one tone lighter,
 * a corner one step larger and a shadow. There is no outline, ring or edge border.
 *
 * Compose for TV's own Surface animates focus with fixed tweens. The design asks for springs,
 * which stop cleanly when a new key arrives, so this surface is built from foundation parts.
 *
 * The corners are squircles. The surface does not clip its content, because a clip to a curved
 * outline costs a mask on each frame. The fill and the art draw themselves through a cached
 * squircle mask (drawSquircle, ArtImage).
 *
 * Every animated value is read in the layer or draw phase, so focus changes do not recompose [content].
 *
 * @param restAlpha opacity when this item does not have focus: 1, or the row dim (0.86 or 0.6).
 * @param description what TalkBack reads for an item that shows no text of its own, such as a card.
 * @param depth light from above over the tone (SquircleDepth), for the app tiles.
 * @param onLongPress runs for the remote's menu key, or for OK held down. The OK release that
 *   follows a long press does not click, here or on the item that has focus after it ([okPress]).
 * @param glow the colour of the light round the item while it has focus (FocusGlow), read as it draws.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    restCorner: Dp,
    focusedCorner: Dp,
    modifier: Modifier = Modifier,
    tone: Tone = NoTone,
    focusedScale: Float = HomeLayout.FOCUS_SCALE,
    elevation: Dp = LauncherElevation.focus,
    restAlpha: Float = 1f,
    description: String? = null,
    depth: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    glow: (() -> Color)? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    content: @Composable FocusSurfaceScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val progress = animateFloatAsState(if (focused) 1f else 0f, LauncherMotion.focus(), label = "focus")
    val alpha = animateFloatAsState(if (focused) 1f else restAlpha, LauncherMotion.effectsFast(), label = "dim")
    val fill = animateFloatAsState(if (focused) 1f else 0f, LauncherMotion.effectsFast(), label = "fill")
    val interaction = remember { MutableInteractionSource() }

    val density = LocalDensity.current
    val restPx = with(density) { restCorner.toPx() }
    val focusedPx = with(density) { focusedCorner.toPx() }
    val elevationPx = with(density) { elevation.toPx() }
    val corner = remember(restPx, focusedPx) { { restPx + (focusedPx - restPx) * progress.value } }
    // The shadow is blurred over 28 dp, so a round rectangle casts the same shadow as the
    // squircle. Android draws a round rectangle's shadow directly; for a path outline it builds
    // the shadow on the CPU on each frame of the focus scale.
    val restShadow = remember(restPx) { RoundedCornerShape(restPx) }
    val focusedShadow = remember(focusedPx) { RoundedCornerShape(focusedPx) }
    val depthLight = remember(depth) { if (depth) SquircleDepth() else null }

    Box(
        modifier = modifier
            .onFocusChanged {
                if (it.isFocused != focused) {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
            }
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .okPress(interaction, onClick, onLongPress)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            // Focus asks the row to bring the click target's bounds into view. This layout step
            // keeps the scale below out of those bounds, so a sliding row puts the focused item
            // exactly where the first item was, not 5 % of its width further right.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                val p = progress.value
                val s = 1f + (focusedScale - 1f) * p
                scaleX = s
                scaleY = s
                shape = when (p) {
                    0f -> restShadow
                    1f -> focusedShadow
                    else -> RoundedCornerShape(corner())
                }
                shadowElevation = elevationPx * p
                this.alpha = alpha.value
                // Dimmed rows apply alpha per draw call, so no offscreen buffer is made for each card.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawBehind {
                val p = progress.value
                if (glow != null && p > 0f && FocusGlow.ENABLED) FocusGlow.draw(this, glow(), focusedPx, p)
                val color = lerp(tone.rest, tone.focused, fill.value)
                if (color.alpha > 0f) drawSquircle(corner(), color)
                depthLight?.draw(this, corner())
            },
    ) {
        SurfaceScope(this, corner) { fill.value }.content()
    }
}
