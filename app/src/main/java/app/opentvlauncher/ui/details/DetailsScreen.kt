package app.opentvlauncher.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.opentvlauncher.home.Metahub
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.WatchlistUndo
import app.opentvlauncher.home.backdropArt
import app.opentvlauncher.home.logoArt
import app.opentvlauncher.system.LocalTitleApps
import app.opentvlauncher.system.TitleApp
import app.opentvlauncher.ui.art.ArtCache
import app.opentvlauncher.ui.art.ArtImage
import app.opentvlauncher.ui.art.ArtMask
import app.opentvlauncher.ui.art.LogoImage
import app.opentvlauncher.ui.focus.PillButton
import app.opentvlauncher.ui.focus.activePage
import app.opentvlauncher.ui.home.Clock
import app.opentvlauncher.ui.home.HomeLayout
import app.opentvlauncher.ui.home.TintWash
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LocalLauncherType

/** The watchlist button's faces. */
enum class WatchlistButton(val label: String) {
    Add("Add to Watchlist"),
    Remove("Remove from Watchlist"),

    /** For 10 s after "Remove from Watchlist". */
    Undo("Add Back to Watchlist"),

    /** The removal is sent and waits for Trakt's answer. OK does nothing. */
    Removing("Removing from Watchlist"),

    /** Trakt did not confirm the removal. OK tries again. */
    RetryRemove("Retry Removal"),
}

/** The face for a title whose removal is in [phase], or in none, and that is [listed] or not. */
fun watchlistButton(phase: WatchlistUndo.Phase?, listed: Boolean) = when {
    phase == WatchlistUndo.Phase.Waiting -> WatchlistButton.Undo
    phase == WatchlistUndo.Phase.Removing -> WatchlistButton.Removing
    listed && phase == WatchlistUndo.Phase.Failed -> WatchlistButton.RetryRemove
    listed -> WatchlistButton.Remove
    else -> WatchlistButton.Add
}

/**
 * Selecting a title, outcome A (design, "details page"): backdrop, title logo, year, runtime,
 * genres, Trakt rating, summary, the app buttons (Stremio, then Netflix for a title on Netflix)
 * and the watchlist button. The first button has focus. Back closes it. [active] is false while
 * the hand-off covers the page; after it, the app button that was pressed takes focus again, or
 * the first button if that one has gone. When the focused button goes while the page shows, such
 * as Netflix when its answer stops counting, the first button that is left takes focus.
 */
@Composable
fun DetailsScreen(
    title: Title,
    watchlist: WatchlistButton,
    onPlay: () -> Unit,
    onWatchlist: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onNetflix: () -> Unit = {},
) {
    val type = LocalLauncherType.current
    val firstButton = remember { FocusRequester() }
    val appFocus = remember { TitleApp.entries.associateWith { FocusRequester() } }
    var pressed by remember { mutableStateOf<TitleApp?>(null) }
    // The button that has focus: a TitleApp, or WatchlistAction.
    var focused by remember { mutableStateOf<Any?>(null) }
    val icons = LocalTitleApps.current
    val apps = TitleApp.forTitle(title, icons.installed)
    val listed = title.traktId != null
    // With no app installed the watchlist button is first. With no button at all, nothing takes focus.
    val hasButton = apps.isNotEmpty() || listed
    LaunchedEffect(active, hasButton) {
        if (active && hasButton) (pressed?.takeIf { it in apps }?.let(appFocus::getValue) ?: firstButton).requestFocus()
    }
    // Read in the composition that removes the button, before its focus goes.
    val actions = apps + listOfNotNull(WatchlistAction.takeIf { listed })
    val focusedGone = active && hasButton && focused != null && focused !in actions
    LaunchedEffect(actions) { if (focusedGone) firstButton.requestFocus() }
    BackHandler(enabled = active, onBack = onBack)
    val backdrop = remember(title) { title.backdropArt(Metahub.MEDIUM) }
    val screen = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.let { DpSize(it.width.toDp(), it.height.toDp()) } }

    // The page is opaque, so Home under it draws nothing that shows.
    Box(modifier.fillMaxSize().activePage(active).drawBehind { drawRect(LauncherColors.surface) }) {
        TintWash(tint = { ArtCache.tint(backdrop) }, centerX = 0.74f, centerY = 0.20f, modifier = Modifier.fillMaxSize())
        ArtImage(backdrop, screen, mask = ArtMask.HeroFull, placeholder = Color.Transparent)
        Clock(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = HomeLayout.sideMargin))
        TitleLogo(title)
        Row(Modifier.at(231.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(TitleText.detailsMeta(title), type.meta, LauncherColors.onSurfaceVariant)
            TitleText.rating(title)?.let { rating ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeartIcon(Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Label(rating, type.meta, LauncherColors.onSurface)
                }
            }
        }
        Label(
            title.overview.orEmpty(),
            type.synopsis,
            LauncherColors.onSurfaceVariant,
            Modifier.at(263.dp).width(450.dp),
            maxLines = 3,
        )
        Row(Modifier.at(357.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TitleAppButtons(
                title,
                apps,
                icons,
                onOpen = { app ->
                    pressed = app
                    if (app == TitleApp.Netflix) onNetflix() else onPlay()
                },
                modifier = Modifier.focusRequester(firstButton),
                each = { app -> Modifier.focusRequester(appFocus.getValue(app)).onFocusChanged { if (it.isFocused) focused = app } },
            )
            if (listed) {
                PillButton(
                    watchlist.label,
                    onWatchlist,
                    (if (apps.isEmpty()) Modifier.focusRequester(firstButton) else Modifier).onFocusChanged { if (it.isFocused) focused = WatchlistAction },
                )
            }
        }
    }
}

/** The watchlist button, as a focused action (DetailsScreen). */
private data object WatchlistAction

/**
 * The title logo, a transparent image, standing on the line above the year. Wide logos fill the
 * width and tall ones the height. Without a logo the title shows as text.
 */
@Composable
private fun TitleLogo(title: Title) {
    val type = LocalLauncherType.current
    val logo = remember(title) { title.logoArt() }
    LogoImage(logo, DpSize(400.dp, 120.dp), Alignment.BottomStart, Modifier.at(93.dp)) {
        Label(title.title, type.bannerTitle, LauncherColors.onSurface, Modifier.at(169.dp).width(844.dp))
    }
}

private fun Modifier.at(top: Dp) = offset(x = HomeLayout.sideMargin, y = top)

/** The design's heart, on a 24-unit grid. */
@Composable
private fun HeartIcon(modifier: Modifier = Modifier) {
    val path = remember { PathParser().parsePathString(HEART).toPath() }
    Spacer(
        modifier.drawBehind {
            val unit = size.minDimension / 24f
            scale(unit, unit, pivot = Offset.Zero) { drawPath(path, LauncherColors.onSurfaceVariant) }
        },
    )
}

private const val HEART = "M12 20.5 4.2 12.9A4.9 4.9 0 0 1 11.1 6l.9.9.9-.9a4.9 4.9 0 0 1 6.9 6.9z"
