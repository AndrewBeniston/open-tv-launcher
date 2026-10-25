package app.opentvlauncher.ui.focus

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.opentvlauncher.ui.home.PlayIcon
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LauncherElevation
import app.opentvlauncher.ui.theme.LauncherMotion
import app.opentvlauncher.ui.theme.LocalLauncherType
import app.opentvlauncher.ui.theme.drawSquircle

/**
 * The design's pill button: banner, sign-in, details and hand-off. Focus: inverse fill, dark
 * label, scale 1.05. Press: scale 0.95 with the one bounce. [icon] is an app's own icon, before
 * the label, in place of the play icon (25 Oct 2026).
 */
@Composable
fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, showPlayIcon: Boolean = false, icon: ImageBitmap? = null) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focus = animateFloatAsState(if (focused) 1f else 0f, LauncherMotion.focus(), label = "button focus")
    val press = animateFloatAsState(if (pressed) 1f else 0f, LauncherMotion.press(), label = "button press")
    // One value draws the fill and the shadow. The shadow comes only once the fill is nearly opaque
    // (25 Oct 2026): Android draws a shadow under the whole shape, and under a see-through
    // fill it showed as a dark flash on the button gaining or losing focus.
    val lift = animateFloatAsState(if (focused) 1f else 0f, LauncherMotion.effectsFast(), label = "button fill")
    val ink = animateColorAsState(
        if (focused) LauncherColors.inverseOnSurface else LauncherColors.onSurface,
        LauncherMotion.effectsFast(),
        label = "button label",
    )
    val elevation = with(LocalDensity.current) { LauncherElevation.button.toPx() }
    val pill = with(LocalDensity.current) { 20.dp.toPx() }
    val pressedCorner = with(LocalDensity.current) { 16.dp.toPx() }
    // A full pill keeps round ends. The press corner of 16 dp gets part of the squircle smoothing.
    // The blurred shadow uses a round rectangle, as on FocusSurface.
    fun corner() = pill + (pressedCorner - pill) * press.value

    Row(
        modifier = modifier
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                val s = (1f + 0.05f * focus.value) * (1f - 0.05f * press.value)
                scaleX = s
                scaleY = s
                shape = RoundedCornerShape(corner())
                shadowElevation = elevation * buttonShadow(lift.value)
            }
            // The fill takes the squircle, so the button needs no clip.
            .drawBehind { drawSquircle(corner(), buttonFill(lift.value)) }
            .okPress(interaction, onClick)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = if (showPlayIcon || icon != null) 18.dp else 22.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(AppIconSize))
            Spacer(Modifier.width(8.dp))
        } else if (showPlayIcon) {
            PlayIcon(color = { ink.value }, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
        }
        Label(label, LocalLauncherType.current.label, color = { ink.value })
    }
}

/** An app icon on a pill button: the size All Apps draws app icons. */
val AppIconSize = 22.dp

/** The fill at [lift], from rest (0) to focused (1): the same colour, from 14 % to opaque. */
internal fun buttonFill(lift: Float) = lerp(LauncherColors.buttonRest, LauncherColors.inverseSurface, lift)

/** The share of the focus shadow at [lift]: none until the fill is 87 % opaque, then up to all of it as the fill turns opaque. */
internal fun buttonShadow(lift: Float) = ((lift - SHADOW_FROM) / (1f - SHADOW_FROM)).coerceIn(0f, 1f)

private const val SHADOW_FROM = 0.85f
