package app.opentvlauncher.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleChange
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.system.LocalTitleApps
import app.opentvlauncher.system.TitleApp
import app.opentvlauncher.ui.details.TitleText
import app.opentvlauncher.ui.focus.FocusSurface
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LocalLauncherType
import app.opentvlauncher.ui.theme.SquircleShape
import app.opentvlauncher.ui.theme.Tone
import kotlinx.coroutines.delay

/** Where the menu's last choice is. */
sealed interface TitleMenuStatus {
    data object Ready : TitleMenuStatus

    /** A change that the launcher cannot take back waits 5 s for a second OK. */
    data class Confirming(val change: TitleChange) : TitleMenuStatus

    data class Sending(val change: TitleChange) : TitleMenuStatus

    /** Trakt did not confirm the change. OK sends it again, with no second OK. */
    data class Failed(val change: TitleChange) : TitleMenuStatus

    /** Mark as Unwatched found no plays: Trakt's history has none of this title. */
    data class NothingToRemove(val change: TitleChange) : TitleMenuStatus
}

/**
 * The menu's changes for [title], in order. Watched and Unwatched need a film or a paused
 * episode; an up-next episode has Watched only, as it has no plays to remove (25 Oct
 * 2026). Reset Progress needs paused items; Remove from Watchlist needs the title on the
 * watchlist ([listed]).
 */
fun titleMenuChanges(title: Title, listed: Boolean): List<TitleChange> = buildList {
    if (title.kind == TitleKind.Movie || title.episode != null) {
        add(TitleChange.MarkWatched)
        if (title.upNextAt == null) add(TitleChange.MarkUnwatched)
    }
    if (title.playbackIds.isNotEmpty()) add(TitleChange.ResetProgress)
    if (listed) add(TitleChange.RemoveFromWatchlist)
}

val TitleChange.label: String
    get() = when (this) {
        TitleChange.MarkWatched -> "Mark as Watched"
        TitleChange.MarkUnwatched -> "Mark as Unwatched"
        TitleChange.ResetProgress -> "Reset Progress"
        TitleChange.RemoveFromWatchlist -> "Remove from Watchlist"
    }

/** Mark as Watched only adds a play. The others remove plays, progress or a watchlist entry. */
val TitleChange.needsConfirm: Boolean get() = this != TitleChange.MarkWatched

/** What OK on [change] does next: ask for a second OK, or send. Null while a change is sending. */
fun TitleMenuStatus.next(change: TitleChange): TitleMenuStatus? = when {
    this is TitleMenuStatus.Sending -> null
    change.needsConfirm && this != TitleMenuStatus.Confirming(change) && this != TitleMenuStatus.Failed(change) ->
        TitleMenuStatus.Confirming(change)
    else -> TitleMenuStatus.Sending(change)
}

/** The line under each choice: what it does, or where it is now. */
private fun line(change: TitleChange, status: TitleMenuStatus): String = when (status) {
    TitleMenuStatus.Confirming(change) -> "Press OK again to confirm."
    TitleMenuStatus.Sending(change) -> "Sending to Trakt."
    TitleMenuStatus.Failed(change) -> "Trakt did not confirm. Press OK to retry."
    TitleMenuStatus.NothingToRemove(change) -> "Trakt has no plays of this title to remove."
    else -> when (change) {
        TitleChange.MarkWatched -> "Adds a play and clears the progress."
        TitleChange.MarkUnwatched -> "Removes its plays from your history."
        TitleChange.ResetProgress -> "Clears the progress on Trakt."
        TitleChange.RemoveFromWatchlist -> "Removes it from your Trakt watchlist."
    }
}

/**
 * The Continue Watching menu (24 Oct 2026), over a dimmed Home: the title, Resume, Netflix
 * for a title on Netflix (25 Oct 2026), then one row for each change. As on the banner and
 * the details page, an app that is not installed has no row. Back closes it. A second OK after
 * 5 s starts again.
 *
 * The first row has focus. That is Resume, or Netflix without Stremio, or with neither app a
 * Close row that closes the menu (25 Oct 2026). None of them changes anything on Trakt, so
 * an OK that reaches the menu by mistake cannot add or remove anything. The long press that
 * opened the menu never chooses a row (okPress).
 *
 * Each row is its choice for this title, not a place in the list. When a row goes, such as Resume
 * once the apps are read and Stremio is not there, a press on it ends with no choice, and the
 * first row takes focus. A press never moves to another row.
 */
@Composable
fun TitleMenu(
    title: Title,
    changes: List<TitleChange>,
    status: TitleMenuStatus,
    onPlay: () -> Unit,
    onChoose: (TitleChange) -> Unit,
    onConfirmEnded: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNetflix: () -> Unit = {},
) {
    val type = LocalLauncherType.current
    val first = remember { FocusRequester() }
    val apps = TitleApp.forTitle(title, LocalTitleApps.current.installed).map { app ->
        when (app) {
            TitleApp.Stremio -> Choice(app, TitleText.appLabel(title, app), "Opens the title in Stremio.", onPlay)
            TitleApp.Netflix -> Choice(app, app.label, "Opens the title in Netflix.", onNetflix)
        }
    }
    val close = listOf(Choice(CloseChoice, "Close", "Closes the menu.", onBack)).takeIf { apps.isEmpty() }.orEmpty()
    val choices = apps + close + changes.map { change -> Choice(change, change.label, line(change, status)) { onChoose(change) } }
    val hasChoice = choices.isNotEmpty()
    LaunchedEffect(hasChoice) { if (hasChoice) first.requestFocus() }
    var focused by remember { mutableStateOf<Any?>(null) }
    val ids = choices.map { it.id }
    // Read here, in the composition that removes the row: by the effect, Compose has moved focus on.
    val focusedGone = hasChoice && focused != null && focused !in ids
    LaunchedEffect(ids) { if (focusedGone) first.requestFocus() }
    LaunchedEffect(status) {
        if (status is TitleMenuStatus.Confirming) {
            delay(5_000)
            onConfirmEnded()
        }
    }
    BackHandler(onBack = onBack)

    Box(modifier.fillMaxSize().background(Scrim)) {
        Column(
            Modifier
                .panelPlace()
                .width(370.dp)
                .background(LauncherColors.surfaceContainerLow, SquircleShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Label("Continue Watching", type.eyebrow, LauncherColors.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Label(title.title, type.focusedRowHeader, LauncherColors.onSurface, maxLines = 2)
                title.episode?.let { Label("Season ${it.season}, Episode ${it.number}", type.meta, LauncherColors.onSurfaceVariant) }
                Spacer(Modifier.height(8.dp))
            }
            choices.forEachIndexed { index, choice ->
                key(title.key ?: title.title, choice.id) {
                    MenuRow(
                        choice.label,
                        choice.line,
                        choice.onClick,
                        (if (index == 0) Modifier.focusRequester(first) else Modifier).onFocusChanged { if (it.isFocused) focused = choice.id },
                    )
                }
            }
        }
    }
}

/** One row of the menu: an app to open the title in, Close, or a change. [id] is the TitleApp, CloseChoice or the TitleChange. */
private class Choice(val id: Any, val label: String, val line: String, val onClick: () -> Unit)

/** The Close row, which the menu has only when neither app is installed. */
private data object CloseChoice

@Composable
private fun MenuRow(label: String, line: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val type = LocalLauncherType.current
    FocusSurface(
        onClick = onClick,
        restCorner = 14.dp,
        focusedCorner = 14.dp,
        tone = RowTone,
        focusedScale = 1.03f,
        description = label,
        modifier = modifier.size(338.dp, 54.dp),
    ) {
        Label(label, type.label, LauncherColors.onSurface, Modifier.offset(18.dp, 9.dp))
        Label(line, type.cardMeta, LauncherColors.onSurfaceVariant, Modifier.offset(18.dp, 30.dp))
    }
}

/**
 * The panel 106 dp from the top, as designed, unless its foot would then pass the TV's safe
 * margin: a two-line episode title with every change is taller than 540 dp allows.
 */
private fun Modifier.panelPlace() = layout { measurable, constraints ->
    val panel = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val top = minOf(106.dp.roundToPx(), constraints.maxHeight - panel.height - SafeMargin.roundToPx()).coerceAtLeast(0)
    layout(constraints.maxWidth, constraints.maxHeight) { panel.place(532.dp.roundToPx(), top) }
}

/** 5 % of the 540 dp screen, which some TVs crop. */
private val SafeMargin = 27.dp

private val RowTone = Tone(Color.Transparent, LauncherColors.surfaceContainerHighest)
private val Scrim = Color(0x99000000)
