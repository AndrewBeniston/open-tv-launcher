package app.opentvlauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.opentvlauncher.data.BannerSlide
import app.opentvlauncher.data.CardAction
import app.opentvlauncher.data.SlideKind
import app.opentvlauncher.system.LocalTitleApps
import app.opentvlauncher.system.TitleApp
import app.opentvlauncher.ui.art.ArtImage
import app.opentvlauncher.ui.art.ArtMask
import app.opentvlauncher.ui.art.PreloadArt
import app.opentvlauncher.ui.details.TitleAppButtons
import app.opentvlauncher.ui.focus.PillButton
import app.opentvlauncher.ui.theme.FlatCrossfade
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LauncherMotion
import app.opentvlauncher.ui.theme.LocalLauncherType
import app.opentvlauncher.ui.theme.fade

/**
 * The banner artwork across the top of the page. It crossfades between slides and drifts
 * from 100 % to 102 % over each slide, following [drift] (0 to 1). Its fade into the page is baked
 * into the bitmap (ArtMask.Hero), so the screen draws no scrims and no live blur. The
 * [neighbours] load in the background, so a new slide fades in with its picture.
 *
 * The drift is drawn, not a layer's scale. Compose counts a layer's scale as a move, and with an
 * accessibility service on, as the Home override is, each move made it check the whole
 * accessibility tree again: 5 times a second, about 0.7 MB of garbage a second at rest.
 */
@Composable
fun HeroArt(slide: BannerSlide, neighbours: List<BannerSlide>, drift: () -> Float, modifier: Modifier = Modifier) {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val size = DpSize(width, HomeLayout.heroHeight)
    neighbours.forEach { PreloadArt(it.art, size, ArtMask.Hero) }
    Box(modifier.fillMaxWidth().height(HomeLayout.heroHeight).clipToBounds()) {
        FlatCrossfade(targetState = slide, animationSpec = LauncherMotion.effectsSlow(), label = "hero") { shown ->
            ArtImage(
                art = shown.art,
                size = size,
                mask = ArtMask.Hero,
                placeholder = Color.Transparent,
                modifier = Modifier
                    .drawWithContent {
                        // A slide that is leaving keeps its full drift while it fades out.
                        val progress = if (shown == slide) drift() else 1f
                        val scale = 1f + (LauncherMotion.BANNER_DRIFT_SCALE - 1f) * progress
                        scale(scale, pivot = Offset(this.size.width * 0.72f, this.size.height * 0.4f)) { this@drawWithContent.drawContent() }
                    }
                    .testTag(BANNER_ART_TAG),
            )
        }
    }
}

/**
 * Banner text, buttons and dots. [expand] runs from 0 (focus below: title, meta and progress)
 * to 1 (focus in the banner: synopsis and buttons). The panel's height follows it, so the rows
 * below move down to make room. [onDotsFocused] says when the dots have focus.
 *
 * The text and the dots do not slide between the two states (24 Oct 2026). When [open]
 * changes they fade out completely, take their new place, and fade in ([BannerTextFade]). The
 * synopsis, the progress bar and the buttons belong to one state only, so they fade with them.
 *
 * Focus (24 and 25 Oct 2026): Right goes from the first button along the buttons to
 * Details, then onto the dot of the slide that shows. On the dots, Right shows the next slide and
 * wraps from the last to the first; Left goes back to the first button of the slide that shows,
 * never to the previous slide. Left on the first button stays there. A focused dot shows its
 * slide. [firstButton] focuses the first button, and [buttons] each button by what it opens.
 */
@Composable
fun BannerPanel(
    state: BannerState,
    open: Boolean,
    expand: () -> Float,
    showDetails: Boolean,
    timer: () -> Float,
    firstButton: FocusRequester,
    buttons: Map<BannerButton, FocusRequester>,
    onPlay: (CardAction) -> Unit,
    onDetails: (CardAction) -> Unit,
    modifier: Modifier = Modifier,
    onButtonFocused: (BannerButton) -> Unit = {},
    onDotsFocused: (Boolean) -> Unit = {},
) {
    val margin = HomeLayout.sideMargin
    val dots = remember(state.slides.size) { List(state.slides.size) { FocusRequester() } }

    fun Modifier.button(button: BannerButton) = focusRequester(buttons.getValue(button)).onFocusChanged { if (it.isFocused) onButtonFocused(button) }
    val textFade = remember { BannerTextFade(open) }
    LaunchedEffect(open) { textFade.follow(open) }
    Box(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val full = HomeLayout.bannerExpanded.roundToPx()
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = full))
                val height = lerp(HomeLayout.bannerCollapsed, HomeLayout.bannerExpanded, expand()).roundToPx()
                layout(placeable.width, height) { placeable.place(0, 0) }
            },
    ) {
        FlatCrossfade(targetState = state.current, animationSpec = LauncherMotion.effectsSlow(), label = "banner text") { slide ->
            SlideText(slide, textFade, showDetails)
        }
        if (showDetails) {
            val primary = if (state.current.kind == SlideKind.ContinueWatching) "Resume" else TitleApp.Stremio.label
            // A Trakt title gets its app buttons: Stremio, then Netflix for a title on Netflix (netflix-play).
            val title = (state.current.action as? CardAction.OpenTitle)?.title
            val icons = LocalTitleApps.current
            val apps = title?.let { TitleApp.forTitle(it, icons.installed) }
            Row(
                modifier = Modifier
                    .padding(start = margin)
                    .offset(y = ButtonsTop)
                    .fade { textFade.alphaIf(open = true) },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val first = Modifier
                    .focusRequester(firstButton)
                    // The start of the row.
                    .onDirection(Key.DirectionLeft) {}
                if (title == null || apps == null) {
                    // A program from Stremio's own channel: Stremio's button, with its icon once read.
                    val icon = icons.icons[TitleApp.Stremio]
                    PillButton(
                        label = primary,
                        showPlayIcon = icon == null,
                        icon = icon,
                        onClick = { state.current.action?.let(onPlay) },
                        modifier = first.button(BannerButton.Stremio),
                    )
                } else {
                    TitleAppButtons(
                        title,
                        apps,
                        icons,
                        onOpen = { app -> onPlay(if (app == TitleApp.Netflix) CardAction.OpenNetflix(title) else CardAction.OpenTitle(title)) },
                        modifier = first,
                        each = { app -> Modifier.button(BannerButton.of(app)) },
                    )
                }
                PillButton(
                    label = "Details",
                    onClick = { state.current.action?.let(onDetails) },
                    modifier = Modifier
                        // With no app button, Details is the first button.
                        .then(if (apps?.isEmpty() == true) first else Modifier)
                        .button(BannerButton.Details)
                        .onDirection(Key.DirectionRight) { dots[state.index].requestFocus() },
                )
            }
        }
        Dots(
            active = state.index,
            timer = timer,
            focus = dots,
            focusable = showDetails,
            onDotFocused = state::show,
            onLeft = { firstButton.requestFocus() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = margin)
                .offset { IntOffset(0, (if (textFade.shownOpen) 262.dp else 200.dp).roundToPx()) }
                .fade { textFade.alpha.value }
                .onFocusChanged { onDotsFocused(it.hasFocus) },
        )
    }
}

/**
 * A banner button, named by what it opens. Home remembers the one that had focus, so focus comes
 * back to it after the page or app it opened (25 Oct 2026).
 */
enum class BannerButton {
    Stremio,
    Netflix,
    Details,
    ;

    companion object {
        fun of(app: TitleApp) = when (app) {
            TitleApp.Stremio -> Stremio
            TitleApp.Netflix -> Netflix
        }

        /** The buttons [slide] shows, given the [installed] packages: as BannerPanel draws them. */
        fun on(slide: BannerSlide, installed: Set<String>?): List<BannerButton> {
            val title = (slide.action as? CardAction.OpenTitle)?.title
            val apps = title?.let { TitleApp.forTitle(it, installed) }?.map(::of) ?: listOf(Stremio)
            return apps + Details
        }
    }
}

private val ButtonsTop = 246.dp
private val TitleWidth = 844.dp

/**
 * Where the banner text is and how much of it shows, between the closed and open states (review
 * O10). On a change it fades out completely, takes its new place ([shownOpen]), then fades in.
 * The place changes only after the fade-out has finished, so no frame shows the text moving,
 * whatever the frame rate.
 */
@Stable
class BannerTextFade(open: Boolean) {
    var shownOpen by mutableStateOf(open)
        private set
    val alpha = Animatable(1f)

    /** The opacity of a part that only the open banner ([open] true) or the closed banner shows. */
    fun alphaIf(open: Boolean): Float = if (shownOpen == open) alpha.value else 0f

    suspend fun follow(open: Boolean) {
        if (open != shownOpen) {
            alpha.animateTo(0f, tween(FADE_OUT_MILLIS, easing = LinearEasing))
            shownOpen = open
        }
        alpha.animateTo(1f, tween(FADE_IN_MILLIS, easing = LinearOutSlowInEasing))
    }

    companion object {
        const val FADE_OUT_MILLIS = 100
        const val FADE_IN_MILLIS = 150
    }
}

/** Runs [action] for a key press in [direction] and keeps the key from Compose's own focus search. */
private fun Modifier.onDirection(direction: Key, action: () -> Unit) = onPreviewKeyEvent {
    if (it.key == direction && it.type == KeyEventType.KeyDown) {
        action()
        true
    } else {
        false
    }
}

@Composable
private fun SlideText(slide: BannerSlide, fade: BannerTextFade, showDetails: Boolean) {
    val type = LocalLauncherType.current
    val margin = HomeLayout.sideMargin
    // Collapsed tops from the design. The open banner shows the text 18 dp higher.
    fun top(collapsed: Dp) = Modifier.offset { IntOffset(margin.roundToPx(), (collapsed - if (fade.shownOpen) 18.dp else 0.dp).roundToPx()) }

    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fade { fade.alpha.value }) {
            Label(slide.eyebrow, type.eyebrow, LauncherColors.onSurfaceVariant, top(106.dp).testTag(BANNER_EYEBROW_TAG))
            Label(
                slide.title,
                type.bannerTitle,
                LauncherColors.onSurface,
                top(125.dp).width(TitleWidth).testTag(BANNER_TITLE_TAG),
            )
            // 4 dp below the design's 175: the 40 sp title is drawn 52 to 54 dp tall, not 44.
            Label(slide.meta, type.meta, LauncherColors.onSurfaceVariant, top(179.dp).testTag(BANNER_META_TAG))
        }
        slide.progress?.let { progress ->
            ProgressBar(
                progress,
                Modifier
                    .offset { IntOffset(margin.roundToPx(), 202.dp.roundToPx()) }
                    .size(160.dp, 4.dp)
                    .fade { fade.alphaIf(open = false) },
            )
        }
        if (showDetails) {
            Label(
                slide.synopsis,
                type.synopsis,
                LauncherColors.onSurfaceVariant,
                Modifier
                    .offset { IntOffset(margin.roundToPx(), 183.dp.roundToPx()) }
                    .width(430.dp)
                    .fade { fade.alphaIf(open = true) },
                maxLines = 2,
            )
        }
    }
}

const val BANNER_DOT_TAG = "banner-dot"
const val BANNER_ART_TAG = "banner-art"
const val BANNER_EYEBROW_TAG = "banner-eyebrow"
const val BANNER_TITLE_TAG = "banner-title"
const val BANNER_META_TAG = "banner-meta"

/**
 * Carousel dots, one per slide, evenly spaced (24 Oct 2026). The active dot widens and
 * the dot it leaves goes back to rest size, both on the focus spring. The active dot fills as the
 * 12 s timer runs. It shows full while focus is in the bar, because the timer stops then.
 *
 * While the banner is open ([focusable]) each dot takes focus in turn, from [focus]. Right goes
 * to the next dot and wraps from the last to the first. Left on any dot runs [onLeft].
 *
 * One dot only looks active (25 Oct 2026): with focus on the dots, the active dot has the
 * focus and grows to 36 × 12 dp. It is always fully bright, and the time that has run darkens it
 * from the left. When the timer moves to the next slide, focus moves with it to the new active dot.
 *
 * A dot is a circle or a pill, with no straight edge for squircle smoothing, so a plain round
 * shape is the same shape and clips more cheaply.
 */
@Composable
private fun Dots(
    active: Int,
    timer: () -> Float,
    focus: List<FocusRequester>,
    focusable: Boolean,
    onDotFocused: (Int) -> Unit,
    onLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusedDot by remember { mutableStateOf<Int?>(null) }
    val dotsFocused = focusedDot != null
    // The timer showed the next slide while a dot had focus: focus goes with it.
    LaunchedEffect(active) { if (focusedDot.let { it != null && it != active }) focus.getOrNull(active)?.requestFocus() }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        focus.forEachIndexed { index, requester ->
            val isActive = index == active
            val big = isActive && dotsFocused
            val width by animateDpAsState(
                when {
                    big -> 36.dp
                    isActive -> 28.dp
                    else -> 8.dp
                },
                LauncherMotion.focus(),
                label = "dot width",
            )
            val height by animateDpAsState(if (big) 12.dp else 8.dp, LauncherMotion.focus(), label = "dot height")
            Spacer(
                Modifier
                    .testTag("$BANNER_DOT_TAG-$index")
                    .size(width, height)
                    .focusRequester(requester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            focusedDot = index
                            onDotFocused(index)
                        } else if (focusedDot == index) {
                            focusedDot = null
                        }
                    }
                    .onDirection(Key.DirectionLeft, onLeft)
                    .onDirection(Key.DirectionRight) { focus[(index + 1) % focus.size].requestFocus() }
                    .focusable(enabled = focusable)
                    .clip(RoundedCornerShape(percent = 50))
                    .drawBehind {
                        if (big) {
                            drawRect(LauncherColors.onSurface)
                            drawRect(LauncherColors.dotFocusedFill, size = Size(size.width * timer(), size.height))
                        } else {
                            drawRect(LauncherColors.dot)
                            if (isActive) drawRect(LauncherColors.dotFill, size = Size(size.width * timer(), size.height))
                        }
                    },
            )
        }
    }
}
