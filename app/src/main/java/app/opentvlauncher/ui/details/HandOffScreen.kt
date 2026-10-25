package app.opentvlauncher.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.opentvlauncher.home.Metahub
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.backdropArt
import app.opentvlauncher.system.TitleApp
import app.opentvlauncher.ui.art.ArtImage
import app.opentvlauncher.ui.art.ArtMask
import app.opentvlauncher.ui.home.HomeLayout
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LocalLauncherType
import app.opentvlauncher.ui.theme.StarLoader

/**
 * Selecting a title, outcome B (design, "hand-off to Stremio"): shown from the key press until
 * Stremio draws its title page, which took up to 3.2 s on 23 October 2026. Without it the wait
 * looks like a dead key press. Back closes it. Netflix's button uses it too, with Netflix's name
 * ([app], 25 Oct 2026).
 *
 * The page holds focus itself, so OK and the D-pad cannot reach the page under it.
 */
@Composable
fun HandOffScreen(title: Title, onBack: () -> Unit, modifier: Modifier = Modifier, app: TitleApp = TitleApp.Stremio) {
    val type = LocalLauncherType.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onBack)
    val backdrop = remember(title) { title.backdropArt(Metahub.MEDIUM) }
    val screen = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.let { DpSize(it.width.toDp(), it.height.toDp()) } }
    val margin = HomeLayout.sideMargin

    Box(modifier.fillMaxSize().focusRequester(focus).focusable().drawBehind { drawRect(HandOffBackground) }) {
        ArtImage(backdrop, screen, mask = ArtMask.HandOff, placeholder = Color.Transparent)
        Label("Opening in ${app.label}", type.eyebrow, LauncherColors.onSurfaceVariant, Modifier.offset(margin, 202.dp))
        Label(title.title, type.bannerTitle, LauncherColors.onSurface, Modifier.offset(margin, 221.dp))
        Label(TitleText.handOffMeta(title), type.meta, LauncherColors.onSurfaceVariant, Modifier.offset(margin, 271.dp))
        StarLoader(spinning = true, modifier = Modifier.offset(margin, 306.dp).size(22.dp))
        Label("Waiting for ${app.label}", type.meta, LauncherColors.onSurfaceVariant, Modifier.offset(92.dp, 308.dp))
    }
}

private val HandOffBackground = Color(0xFF07080A)
