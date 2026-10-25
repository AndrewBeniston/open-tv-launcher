package app.opentvlauncher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.opentvlauncher.data.AppTile
import app.opentvlauncher.data.Art
import app.opentvlauncher.data.CONTINUE_WATCHING_ROW
import app.opentvlauncher.data.CardAction
import app.opentvlauncher.data.HomeContent
import app.opentvlauncher.system.LocalTitleApps
import app.opentvlauncher.ui.art.ArtCache
import app.opentvlauncher.ui.focus.RowFocusMemory
import app.opentvlauncher.ui.focus.activePage
import app.opentvlauncher.ui.focus.rememberRowFocusMemory
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LauncherMotion
import app.opentvlauncher.ui.theme.fade
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import androidx.compose.animation.Animatable as ColorAnimatable

/**
 * The home screen. Focus map (design, section 3):
 * - Home opens with focus on YouTube. The Home button and Back return there.
 * - Up and Down move exactly one row, to the item that row remembers, or its first item. No row
 *   is skipped, as on Google TV. Compose's own search took the nearest item in line, so Down from
 *   All Apps skipped a Continue Watching row whose one card sits on the left.
 * - Left and Right stop at the row ends.
 * - Up from the apps row opens the banner: synopsis and buttons.
 *
 * [homeRequests] goes up by one each time the Home button or the launcher icon brings Home back.
 * [startApp] is the app tile that gets focus when Home opens: YouTube, or All Apps after Back
 * from the All Apps screen.
 *
 * [onOpenCard] runs for OK on a card and for "Details" in the banner. [onPlay] runs for the
 * banner's app buttons: "Stremio" or "Resume", and "Netflix". With no slides, the banner area
 * stays empty.
 * [onCardMenu] runs for the menu key, or OK held, on a Continue Watching card.
 *
 * Under the top bar (LauncherTabs): Up from the banner, or from the apps row when there is no
 * banner, runs [onUpFromTop], which moves focus to the bar; false leaves the key. [scroll]
 * tells the bar how far the page has scrolled.
 *
 * The Movies and Shows tabs use the same screen with no apps row. Their first row takes the apps
 * row's place: it does not scroll, Up from it opens the banner, and it is where focus starts.
 * Back is left to the caller. With [takeFocus] false, the screen takes focus only when asked
 * ([homeRequests] above 0, or [focusReturns]), so focus on a tab stays in the bar.
 *
 * [focusReturns] goes up by one when a page over Home closes, such as the details page. Focus
 * then goes back where it was: the remembered card of the same row, the same app tile, or the
 * banner button that had it, such as Netflix after the Netflix hand-off. A banner button that has
 * gone since gives focus to the banner's first button.
 *
 * Rows are keyed by their role. A refresh that moves rows keeps focus on the same row. If it
 * removes the focused row, focus goes to the row now at its place, or to YouTube.
 *
 * [active] is false while another page covers Home. Home then takes no focus, keys or actions.
 *
 * [barFocused] is true while focus is in the top bar. Every tab's banner then shows closed, with
 * its timer stopped, so the same title on two tabs sits in the same place and at the same size, and
 * a change of tab crossfades it without a jump (24 Oct 2026). Before, Home's banner
 * stayed open under the bar while a new Movies page showed closed, its art drifting from 100 %.
 * Down from the bar opens the banner again on the button it had.
 *
 * With [barShapes], the page draws its background behind the bar's pills, blurred ([BarBlur]).
 *
 * Each row's remembered card, its scroll position and the banner slide are saved state. They
 * stay when Home leaves the screen for All Apps or Settings, inside a SaveableStateProvider, and
 * when Android recreates the activity with its saved state. The manifest sets stateNotNeeded, as
 * home apps do, so after Android ends the launcher's process they may start again.
 */
@Composable
fun HomeScreen(
    content: HomeContent,
    homeRequests: Int,
    onOpenApp: (AppTile) -> Unit,
    modifier: Modifier = Modifier,
    startApp: Int = 0,
    onOpenCard: (CardAction) -> Unit = {},
    onPlay: (CardAction) -> Unit = {},
    focusReturns: Int = 0,
    active: Boolean = true,
    onCardMenu: (CardAction) -> Unit = {},
    onUpFromTop: (() -> Boolean)? = null,
    scroll: PageScroll? = null,
    takeFocus: Boolean = true,
    barFocused: Boolean = false,
    barShapes: BarShapes? = null,
) {
    // A refresh that changes the slides, such as new progress after Stremio, keeps the slide shown.
    val shownSlide = rememberSaveable(saver = ShownSlide.Saver) { ShownSlide() }
    val banner = remember(content.slides) {
        content.slides.takeIf { it.isNotEmpty() }?.let { slides ->
            BannerState(slides, start = slides.indexOfFirst { it.key == shownSlide.key }.coerceAtLeast(0))
        }
    }
    SideEffect { shownSlide.key = banner?.current?.key }
    // The banner button that had focus last. Null: none yet, so the first. Saved, so Movies and
    // Shows keep it after their page leaves the screen (merge gate V3).
    var bannerButton by rememberSaveable { mutableStateOf<BannerButton?>(null) }
    val hasApps = content.apps.isNotEmpty()
    // The row under the banner: the apps row, or the first row on a page with no apps row.
    val top = if (hasApps) HomeSection.Apps else HomeSection.Row(0)
    // Saved, so a tab that leaves the screen comes back to the same row.
    var section by rememberSaveable(saver = HomeSection.Saver) { mutableStateOf(top) }
    var focusedApp by remember { mutableIntStateOf(0) }
    var focusedArt by remember { mutableStateOf<Art?>(null) }
    var bannerRequested by remember { mutableStateOf(false) }
    var bannerAsks by remember { mutableIntStateOf(0) }
    var bannerAsksButton by remember { mutableStateOf<BannerButton?>(null) }
    val appsFocus = rememberRowFocusMemory(content.apps.size)
    val firstButton = remember { FocusRequester() }
    val buttons = remember { BannerButton.entries.associateWith { FocusRequester() } }
    val installed = LocalTitleApps.current.installed
    val rowFocus = remember { mutableMapOf<String, RowFocus>() }
    var focusedRowKey by rememberSaveable { mutableStateOf<String?>(null) }
    val atTop = section == HomeSection.Banner || section == top

    /** Focus on the top row, on the item it remembers. */
    fun focusTop(): Boolean = if (hasApps) appsFocus.focusRemembered() else content.rows.firstOrNull()?.let { rowFocus[it.key] }?.enter() == true

    /** Focus where Home starts: the app tile [app], YouTube by default, or on a page with no apps row, the top row. */
    fun focusStart(app: Int = 0) {
        if (hasApps) appsFocus.focus(app) else focusTop()
    }

    // Banner: open while it has focus, or while Up from the apps row waits for its buttons. Focus
    // in the bar leaves section at the banner, for Down to come back to.
    val bannerFocused = section == HomeSection.Banner && !barFocused
    val expanded = bannerFocused || bannerRequested
    val expand = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) { expand.animateTo(if (expanded) 1f else 0f, LauncherMotion.focus()) }
    val showDetails = expanded || expand.isRunning
    LaunchedEffect(bannerAsks) {
        if (!bannerRequested) return@LaunchedEffect
        // The button asked for, if the slide shows it, or the first.
        val shown = banner?.current?.let { BannerButton.on(it, installed) }.orEmpty()
        val button = bannerAsksButton?.takeIf { it in shown }?.let(buttons::getValue) ?: firstButton
        // If the buttons cannot take focus, close the banner again rather than leave it open, and
        // keep focus on the top row.
        if (!button.requestFocus(FocusDirection.Enter)) {
            bannerRequested = false
            if (!focusTop()) focusStart()
        }
    }

    /** Opens the banner, then focuses its first button, or [button] when the slide shows it. */
    fun askBanner(button: BannerButton? = null) {
        bannerRequested = true
        bannerAsksButton = button
        bannerAsks++
    }

    // Banner clock (25 Oct 2026): 12 s a slide while Home is in front with no page such
    // as Details over it, and focus is below the banner or on its buttons. On the buttons a slide
    // changes no sooner than 5 s after the last key on the banner, so the title never changes as
    // he presses. On the dots the clock waits, and starts again 5 s after the last move. It
    // stops while focus is in the bar, with the slide's dot full and its art at full drift.
    // It counts wall-clock time, so the TV's animation scale cannot speed up or skip slides.
    // slideProgress drives the dot fill and the art drift; see BANNER_TICK_MILLIS.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val inFront = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val bannerVisible by rememberUpdatedState(atTop)
    var dotsFocused by remember { mutableStateOf(false) }
    // The focused button has gone, such as Netflix when its answer stops counting or the next
    // slide is not on Netflix: the first button takes focus, rather than what Android picks.
    // Read in the composition that removes the button, before its focus goes.
    val shownButtons = banner?.current?.let { BannerButton.on(it, installed) }.orEmpty()
    val focusedButtonGone = bannerFocused && !dotsFocused && bannerButton != null && bannerButton !in shownButtons
    LaunchedEffect(shownButtons) { if (focusedButtonGone) firstButton.requestFocus() }
    // One more for each key on the banner, and when focus arrives there.
    var bannerKeys by remember { mutableIntStateOf(0) }
    var bannerKeyRecent by remember { mutableStateOf(false) }
    LaunchedEffect(bannerFocused) { if (bannerFocused) bannerKeys++ }
    LaunchedEffect(bannerKeys) {
        if (bannerKeys == 0) return@LaunchedEffect
        bannerKeyRecent = true
        delay(LauncherMotion.BANNER_KEY_WAIT_MILLIS.toLong())
        bannerKeyRecent = false
    }
    val keyRecent by rememberUpdatedState(bannerFocused && bannerKeyRecent)
    val dotsWaiting by rememberUpdatedState(bannerFocused && dotsFocused && bannerKeyRecent)
    val clockStopped = barFocused || !inFront || !active
    var slideProgress by remember { mutableFloatStateOf(if (clockStopped) 1f else 0f) }
    LaunchedEffect(banner, banner?.index, clockStopped) {
        if (banner == null) return@LaunchedEffect
        if (clockStopped) {
            slideProgress = 1f
            return@LaunchedEffect
        }
        val motion = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
        val ticks = LauncherMotion.BANNER_SLIDE_MILLIS / LauncherMotion.BANNER_TICK_MILLIS
        slideProgress = if (motion) 0f else 1f
        var tick = 0
        while (tick < ticks) {
            delay(LauncherMotion.BANNER_TICK_MILLIS.toLong())
            if (dotsWaiting) continue
            tick++
            // No updates while the rows hide the banner, and none with animations switched off.
            if (motion && bannerVisible) slideProgress = tick.toFloat() / ticks
        }
        snapshotFlow { keyRecent }.first { !it }
        banner.next()
    }

    // Page scroll: a focused content row moves up near the top of the screen.
    val density = LocalDensity.current
    val pageOffset = remember { Animatable(0f) }
    val targetOffset = with(density) { HomeLayout.pageOffset(section, content.rows, hasApps).toPx() }
    LaunchedEffect(targetOffset) { pageOffset.animateTo(targetOffset, LauncherMotion.scroll()) }
    val scrolled = Modifier.offset { IntOffset(0, -pageOffset.value.roundToInt()) }
    SideEffect { scroll?.offset = { pageOffset.value } }

    // The page tint follows the focused card's art below the top, or the banner's slide. Its target
    // is read in a snapshot flow, not in the composition: there, each focus move along a row
    // recomposed the whole page and measured every row again (speed check, perf-1).
    val tintTop by rememberUpdatedState(top)
    val tintBanner by rememberUpdatedState(banner)
    fun tintTarget(): Color {
        val art = (if (section == HomeSection.Banner || section == tintTop) null else focusedArt) ?: tintBanner?.current?.art
        return art?.let(ArtCache::tint) ?: Art.NeutralTint
    }
    val tint = remember { ColorAnimatable(Snapshot.withoutReadObservation { tintTarget() }) }
    LaunchedEffect(Unit) { snapshotFlow { tintTarget() }.collectLatest { tint.animateTo(it, LauncherMotion.effectsSlow()) } }
    val clockAlpha = animateFloatAsState(if (atTop) 1f else 0f, LauncherMotion.effectsFast(), label = "clock")

    // When the page over Home closes, its focused item goes, and Android gives focus to the first
    // item on screen before Home can put it back. That moved focus to the wrong card and changed
    // what Home remembered. So Home refuses focus from the moment it is covered until it has
    // asked for focus itself.
    val returnGate = remember { ReturnGate() }
    SideEffect { if (!active) returnGate.closed = true }
    // A tab page that is composed after focus has returned before must not take focus at once.
    val firstReturns = remember { focusReturns }
    LaunchedEffect(focusReturns) {
        if (focusReturns == 0 || (!takeFocus && focusReturns == firstReturns)) return@LaunchedEffect
        returnGate.closed = false
        val back = when (val shown = section) {
            HomeSection.Apps -> appsFocus.focusRemembered()
            // The banner may be closed, after focus in the bar: it opens, then its button takes focus.
            HomeSection.Banner -> (banner != null).also { if (it) askBanner(bannerButton) }
            is HomeSection.Row -> content.rows.getOrNull(shown.index)?.let { rowFocus[it.key] }?.focusRemembered() == true
        }
        // The control has gone, such as the banner after its last title left the watchlist:
        // the top row takes focus, on the item it remembers, or where Home starts.
        if (!back && !focusTop()) focusStart()
    }
    // The banner has gone while it had focus, or while a page over Home covered it. Its place to
    // return to is the top row.
    val hasBanner = banner != null
    LaunchedEffect(hasBanner) {
        if (hasBanner || section != HomeSection.Banner) return@LaunchedEffect
        section = top
        bannerRequested = false
        if (active) {
            withFrameNanos { }
            if (!focusTop()) focusStart()
        }
    }
    val rowKeys = content.rows.map { it.key }
    LaunchedEffect(rowKeys) {
        val shown = section as? HomeSection.Row ?: return@LaunchedEffect
        // No row has had focus yet: a tab page starts on its first row before it takes focus.
        val focusedKey = focusedRowKey ?: return@LaunchedEffect
        val now = rowKeys.indexOf(focusedKey)
        if (now >= 0) {
            if (now != shown.index) section = HomeSection.Row(now)
            return@LaunchedEffect
        }
        // The focused row has gone. Under another page, only the place to return to changes.
        if (rowKeys.isEmpty()) {
            section = top
            if (active) focusStart()
            return@LaunchedEffect
        }
        val next = shown.index.coerceAtMost(rowKeys.lastIndex)
        section = HomeSection.Row(next)
        focusedRowKey = rowKeys[next]
        if (active) {
            withFrameNanos { }
            rowFocus[rowKeys[next]]?.focusRemembered()
        }
    }
    LaunchedEffect(homeRequests) {
        if (homeRequests == 0 && !takeFocus) return@LaunchedEffect
        returnGate.closed = false
        bannerRequested = false
        focusStart(startApp)
    }
    LaunchedEffect(active) {
        // A safety net: a page that closes without either request above must not leave Home shut.
        if (!active) return@LaunchedEffect
        withFrameNanos { }
        returnGate.closed = false
    }
    BackHandler(enabled = active && hasApps) {
        // Back from anywhere returns to YouTube. Back on YouTube does nothing.
        if (section != HomeSection.Apps || focusedApp != 0) {
            bannerRequested = false
            appsFocus.focus(0)
        }
    }

    /** Up from the top row: the banner opens and then takes focus on its first button. With no banner, the bar. */
    fun upFromTop(): Boolean {
        if (banner == null) return onUpFromTop?.invoke() == true
        askBanner()
        return true
    }

    /** Up or Down from the focused section. False leaves the key to Compose: there is nothing that way. */
    fun step(down: Boolean): Boolean {
        val rows = content.rows
        return when (val shown = section) {
            HomeSection.Banner -> if (down) focusTop() else onUpFromTop?.invoke() == true
            HomeSection.Apps -> when {
                down -> {
                    val first = rows.firstOrNull()?.let { rowFocus[it.key] } ?: return false
                    first.enter()
                    true
                }
                else -> upFromTop()
            }
            is HomeSection.Row -> {
                val next = shown.index + if (down) 1 else -1
                when {
                    next < 0 && hasApps -> appsFocus.focusRemembered()
                    next < 0 -> upFromTop()
                    next <= rows.lastIndex -> rowFocus[rows[next].key]?.enter()
                }
                // Down on the last row stays there.
                true
            }
        }
    }

    // One modifier for each section, made once: a new one at each recomposition, as when section
    // changes on Up or Down, made every row recompose and measure again (speed check, perf-1).
    val sectionModifiers = remember { mutableMapOf<HomeSection, Modifier>() }
    fun sectionTracker(value: HomeSection) = sectionModifiers.getOrPut(value) {
        Modifier.onFocusChanged {
            if (it.hasFocus) {
                section = value
                if (value == HomeSection.Banner) bannerRequested = false
            }
        }
    }

    // The window paints the surface colour (themes.xml), so the page paints no background.
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .activePage(active, accepts = { !returnGate.closed })
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (bannerFocused) bannerKeys++
                when (it.key) {
                    Key.DirectionDown -> step(down = true)
                    Key.DirectionUp -> step(down = false)
                    else -> false
                }
            },
    ) {
        TintWash(tint = { tint.value }, modifier = Modifier.fillMaxSize())
        if (banner != null) HeroArt(banner.current, banner.neighbours, drift = { slideProgress }, modifier = scrolled)
        if (barShapes != null) {
            // Not drawn once the page has scrolled the pills off the screen: a blurred layer is made
            // again whenever its content changes, as the tint does at each row move, seen or not.
            val blurShown = Modifier.drawWithContent {
                if (pageOffset.value < (barShapes.pills.maxOfOrNull { it.bottom } ?: 0f)) drawContent()
            }
            BarBlur(barShapes, scrolled.then(blurShown)) {
                // The page's background under the bar as it is on screen, opaque, so no sharp art shows through.
                Box(Modifier.fillMaxSize().background(LauncherColors.surface))
                TintWash(tint = { tint.value }, modifier = Modifier.fillMaxSize())
                if (banner != null) HeroArt(banner.current, emptyList(), drift = { slideProgress })
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .then(scrolled),
        ) {
            if (banner != null) {
                BannerPanel(
                    state = banner,
                    open = expanded,
                    expand = { expand.value },
                    showDetails = showDetails,
                    timer = { slideProgress },
                    firstButton = firstButton,
                    buttons = buttons,
                    onButtonFocused = { bannerButton = it },
                    onDotsFocused = { dotsFocused = it },
                    onPlay = onPlay,
                    onDetails = onOpenCard,
                    modifier = sectionTracker(HomeSection.Banner),
                )
            } else {
                Spacer(Modifier.height(HomeLayout.bannerCollapsed))
            }
            if (hasApps) {
                AppsRow(
                    apps = content.apps,
                    rowFocused = section == HomeSection.Apps,
                    focusMemory = appsFocus,
                    onOpen = onOpenApp,
                    onTileFocused = { focusedApp = it },
                    modifier = sectionTracker(HomeSection.Apps),
                )
                Spacer(Modifier.height(HomeLayout.rowGap))
            }
            content.rows.forEachIndexed { index, row ->
                key(row.key) {
                    val requester = remember { FocusRequester() }
                    val memory = rememberSaveable(saver = RowFocusMemory.Saver) { RowFocusMemory() }
                    memory.firstWhenNewAhead = row.key == CONTINUE_WATCHING_ROW
                    DisposableEffect(requester, memory) {
                        rowFocus[row.key] = RowFocus(requester, memory)
                        onDispose { rowFocus.remove(row.key) }
                    }
                    ContentRowSection(
                        row = row,
                        rowFocused = section == HomeSection.Row(index),
                        onItemFocused = { focusedArt = it },
                        onOpen = onOpenCard,
                        modifier = remember(index) {
                            sectionTracker(HomeSection.Row(index)).onFocusChanged { if (it.hasFocus) focusedRowKey = row.key }
                        },
                        focusRequester = requester,
                        focusMemory = memory,
                        active = active,
                        onLongPress = if (row.key == CONTINUE_WATCHING_ROW) onCardMenu else null,
                    )
                }
            }
        }

        // Left of the bar's clock, 28 dp from it, on the bar's centre line.
        val clockStart = barShapes?.clockStart
        content.status?.let { status ->
            StatusChip(
                status,
                Modifier
                    .then(scrolled)
                    .layout { measurable, constraints ->
                        val chip = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(chip.width, chip.height) {
                            val end = clockStart ?: (constraints.maxWidth - HomeLayout.sideMargin.toPx())
                            val centre = (TOP_BAR_TOP + TOP_BAR_HEIGHT / 2f).dp.toPx()
                            chip.place((end - 28.dp.toPx() - chip.width).roundToInt(), (centre - chip.height / 2f).roundToInt())
                        }
                    }
                    .fade { clockAlpha.value },
            )
        }
    }
}

/** A content row's focus: the row as a group, and the card it remembers. */
private class RowFocus(val group: FocusRequester, val memory: RowFocusMemory) {
    /**
     * Focuses the remembered card itself (RowFocusMemory.focusRemembered). The row's onEnter runs
     * for this request too, and goes to the same card. The row as a group is asked only when the
     * card cannot take focus.
     */
    fun focusRemembered(): Boolean = memory.focusRemembered() || group.requestFocus(FocusDirection.Enter)

    /** Focus enters the row with the D-pad (RowFocusMemory.enter). */
    fun enter(): Boolean = memory.enter() || group.requestFocus(FocusDirection.Enter)
}

/** The banner slide on screen, kept across a change of the slides without a recomposition. */
private class ShownSlide {
    var key: String? = null

    companion object {
        val Saver = Saver<ShownSlide, String>(save = { it.key }, restore = { ShownSlide().apply { key = it } })
    }
}

/** Shut while a page covers Home, and until Home asks for focus again. Read during focus moves only. */
private class ReturnGate {
    var closed = false
}
