package app.opentvlauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.opentvlauncher.settings.TitleAction
import app.opentvlauncher.trakt.SignIn
import app.opentvlauncher.ui.focus.FocusSurface
import app.opentvlauncher.ui.home.Clock
import app.opentvlauncher.ui.home.HomeLayout
import app.opentvlauncher.ui.home.TintWash
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LocalLauncherType
import app.opentvlauncher.ui.theme.SquircleShape
import app.opentvlauncher.ui.theme.Tone
import kotlinx.coroutines.delay

private enum class Row { TraktAccount, SelectingATitle, Screensaver, About }

/**
 * The launcher's Settings (design, "Settings, with the Selecting a Title row"), from All Apps >
 * Personal TV Launcher. Focus opens on Selecting a Title. The panel explains the focused row.
 * The design's Inputs row belongs to the Philips only, so the Chromecast leaves it out. Without
 * Stremio ([stremioInstalled] false), Selecting a Title offers only Show Details (25 Oct 2026).
 */
@Composable
fun SettingsScreen(
    signIn: SignIn,
    titleAction: TitleAction,
    version: String,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onTitleAction: (TitleAction) -> Unit,
    onScreensaver: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    stremioInstalled: Boolean = true,
) {
    val type = LocalLauncherType.current
    var focused by remember { mutableStateOf(Row.SelectingATitle) }
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) { start.requestFocus() }
    BackHandler(onBack = onBack)

    // Signing out asks for a second OK within 5 s.
    var confirmSignOut by remember { mutableStateOf(false) }
    LaunchedEffect(confirmSignOut) {
        if (confirmSignOut) {
            delay(5_000)
            confirmSignOut = false
        }
    }
    val signedIn = signIn == SignIn.SignedIn
    val shown = titleAction.withStremio(stremioInstalled)

    Box(modifier.fillMaxSize()) {
        TintWash(tint = { SettingsWash }, centerX = 0.80f, centerY = 0f, modifier = Modifier.fillMaxSize())
        Clock(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = HomeLayout.sideMargin))
        Label("Settings", type.eyebrow, LauncherColors.onSurfaceVariant, Modifier.at(32.dp))
        Label("Personal TV Launcher", type.pageTitle, LauncherColors.onSurface, Modifier.at(50.dp))

        val rows = listOf(
            Triple(Row.TraktAccount, "Trakt Account", when {
                confirmSignOut -> "Press OK again to sign this TV out."
                signedIn -> "Signed In"
                else -> "Not Signed In. Press OK to get a code."
            }),
            Triple(Row.SelectingATitle, "Selecting a Title", shown.label),
            Triple(Row.Screensaver, "Screensaver", "Top Picks Artwork"),
            Triple(Row.About, "About", "Version $version"),
        )
        rows.forEachIndexed { index, (row, label, value) ->
            SettingsRow(
                label = label,
                value = value,
                onClick = {
                    when (row) {
                        Row.TraktAccount -> when {
                            !signedIn -> onSignIn()
                            confirmSignOut -> onSignOut()
                            else -> confirmSignOut = true
                        }
                        Row.SelectingATitle -> if (stremioInstalled) {
                            onTitleAction(if (titleAction == TitleAction.ShowDetails) TitleAction.OpenInStremio else TitleAction.ShowDetails)
                        }
                        Row.Screensaver -> onScreensaver()
                        Row.About -> Unit
                    }
                },
                onFocused = {
                    focused = row
                    if (row != Row.TraktAccount) confirmSignOut = false
                },
                modifier = Modifier
                    .at(106.dp + 62.dp * index)
                    .then(if (row == Row.SelectingATitle) Modifier.focusRequester(start) else Modifier),
            )
        }

        Panel(focused, signedIn, shown, version, stremioInstalled)
    }
}

private val TitleAction.label get() = when (this) {
    TitleAction.ShowDetails -> "Show Details"
    TitleAction.OpenInStremio -> "Open in Stremio"
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val type = LocalLauncherType.current
    FocusSurface(
        onClick = onClick,
        restCorner = 14.dp,
        focusedCorner = 14.dp,
        tone = RowTone,
        focusedScale = 1.03f,
        description = label,
        onFocusChanged = { if (it) onFocused() },
        modifier = modifier.size(430.dp, 54.dp),
    ) {
        Label(label, type.label, LauncherColors.onSurface, Modifier.offset(18.dp, 9.dp))
        Label(value, type.cardMeta, LauncherColors.onSurfaceVariant, Modifier.offset(18.dp, 30.dp))
    }
}

/** The side panel: what the focused row does. For Selecting a Title, both choices. */
@Composable
private fun Panel(row: Row, signedIn: Boolean, titleAction: TitleAction, version: String, stremioInstalled: Boolean) {
    val type = LocalLauncherType.current
    val heading = type.focusedRowHeader
    Box(
        Modifier
            .offset(532.dp, 106.dp)
            .size(370.dp, 366.dp)
            .background(LauncherColors.surfaceContainerLow, SquircleShape(16.dp)),
    ) {
        val (title, body) = when (row) {
            Row.TraktAccount -> "Trakt Account" to if (signedIn) {
                "Home uses your Trakt data. Press OK twice to sign out."
            } else {
                "Sign in to show your Trakt data on Home. Press OK to get a code for your phone."
            }
            Row.SelectingATitle -> "Selecting a Title" to "Choose which page opens when you select a film or show."
            Row.Screensaver -> "Screensaver" to
                "Shows Top Picks artwork. Press OK to open screensaver settings and select Personal TV Launcher."
            Row.About -> "About" to "Personal TV Launcher, version $version."
        }
        Label(title, heading, LauncherColors.onSurface, Modifier.offset(24.dp, 22.dp))
        Label(body, type.meta, LauncherColors.onSurfaceVariant, Modifier.offset(24.dp, 60.dp).width(320.dp), maxLines = 4)
        if (row == Row.SelectingATitle) {
            Option(
                "Show Details",
                "Shows the title details in this launcher.",
                selected = titleAction == TitleAction.ShowDetails,
                top = 128.dp,
            )
            if (stremioInstalled) {
                Option("Open in Stremio", "Opens the title page in Stremio.", selected = titleAction == TitleAction.OpenInStremio, top = 210.dp)
                Label("Press OK to change this setting.", type.cardMeta, LauncherColors.onSurfaceFaint, Modifier.offset(24.dp, 310.dp))
            }
        }
    }
}

@Composable
private fun Option(label: String, description: String, selected: Boolean, top: Dp) {
    val type = LocalLauncherType.current
    Box(
        Modifier
            .offset(16.dp, top)
            .size(338.dp, 70.dp)
            .background(LauncherColors.surfaceContainer, SquircleShape(14.dp)),
    ) {
        Radio(selected, Modifier.offset(14.dp, 15.dp).size(20.dp))
        Label(label, type.label, LauncherColors.onSurface, Modifier.offset(48.dp, 13.dp))
        Label(description, type.cardMeta, LauncherColors.onSurfaceVariant, Modifier.offset(48.dp, 36.dp).width(275.dp), maxLines = 2)
    }
}

/** A filled circle with a tick when selected, or the track colour when not. */
@Composable
private fun Radio(selected: Boolean, modifier: Modifier = Modifier) {
    val tick = remember { PathParser().parsePathString(TICK).toPath() }
    Spacer(
        modifier.drawBehind {
            drawCircle(if (selected) LauncherColors.onSurface else LauncherColors.track)
            if (selected) {
                // The tick is 14 dp on a 24-unit grid, 3 dp in from the corner.
                val inset = size.width * 0.15f
                val unit = size.width * 0.7f / 24f
                translate(inset, inset) {
                    scale(unit, unit, pivot = Offset.Zero) { drawPath(tick, LauncherColors.inverseOnSurface) }
                }
            }
        },
    )
}

private fun Modifier.at(top: Dp) = offset(x = HomeLayout.sideMargin, y = top)

private val RowTone = Tone(Color.Transparent, LauncherColors.surfaceContainerHighest)
private val SettingsWash = Color(0x2450505A)
private const val TICK = "M9.5 16.2 5.3 12l-1.4 1.4 5.6 5.6 11-11-1.4-1.4z"
