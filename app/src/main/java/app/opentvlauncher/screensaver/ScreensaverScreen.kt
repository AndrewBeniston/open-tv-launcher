package app.opentvlauncher.screensaver

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentvlauncher.home.HomeMapper
import app.opentvlauncher.home.HomeState
import app.opentvlauncher.home.Metahub
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.backdropArt
import app.opentvlauncher.home.hasBackdrop
import app.opentvlauncher.home.logoArt
import app.opentvlauncher.ui.art.ArtCache
import app.opentvlauncher.ui.art.ArtImage
import app.opentvlauncher.ui.art.ArtMask
import app.opentvlauncher.ui.art.LocalArtLoader
import app.opentvlauncher.ui.art.LogoImage
import app.opentvlauncher.ui.art.PreloadArt
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LauncherMotion
import app.opentvlauncher.ui.theme.LocalLauncherType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.random.Random

/** The logo and year take a new corner on each slide, so nothing bright stays in one place. */
private val corners = listOf(Alignment.BottomStart, Alignment.TopEnd, Alignment.BottomEnd, Alignment.TopStart)

private val LogoBox = DpSize(400.dp, 120.dp)

private val SynopsisWidth = 520.dp

private const val FIRST_ART_WAIT_MILLIS = 3_000L

/**
 * The titles the screensaver shows: Top Picks for You, the same list as the row, from the saved
 * HomeState, so it works offline (24 Oct 2026). Without Top Picks it uses the watchlist,
 * then Continue Watching. The order changes once a day, so it does not always open on one title.
 * A title with no backdrop is left out.
 */
fun screensaverTitles(state: HomeState, day: Long): List<Title> {
    val trakt = state.trakt ?: return emptyList()
    fun withArt(titles: List<Title>) = titles.filter { it.hasBackdrop() }
    val titles = withArt(trakt.picks).ifEmpty { withArt(trakt.watchlist) }.ifEmpty { withArt(trakt.continueWatching) }
    return titles.distinctBy { it.key ?: it.imdb ?: it.title }.shuffled(Random(day))
}

/**
 * The art's pan and zoom over one slide, at progress 0 to 1. The pan moves the art
 * [LauncherMotion.SCREENSAVER_PAN] of the screen width about its centre, so at most half that
 * from the middle. A centred picture at scale s reaches (s − 1) / 2 of the width past each side,
 * so the art starts at 1 + 2 × that offset, plus a margin, and only grows from there: the screen
 * never shows past the edge of the picture.
 */
object ScreensaverMotion {
    private const val MAX_OFFSET = LauncherMotion.SCREENSAVER_PAN / 2
    private const val EDGE_MARGIN = 0.01f
    const val START_SCALE = 1f + 2 * MAX_OFFSET + EDGE_MARGIN

    fun scale(progress: Float) = START_SCALE + LauncherMotion.SCREENSAVER_ZOOM * progress

    /** The horizontal offset, as a fraction of the screen width. [direction] is 1 or −1. */
    fun offset(progress: Float, direction: Float) = direction * LauncherMotion.SCREENSAVER_PAN * (progress - 0.5f)
}

/** Under the logo: "Action film from 2002", as the banner's line (25 Oct 2026). */
fun Title.screensaverMeta(): String = HomeMapper.kindLine(this)

/** About 4 lines at the synopsis width. */
const val SYNOPSIS_MAX_CHARS = 200

/**
 * Under the year: the first one or two sentences of the Trakt summary, at most
 * [SYNOPSIS_MAX_CHARS] (24 Oct 2026). It always ends at a sentence end, so a first
 * sentence that is too long gives no synopsis.
 */
fun Title.screensaverSynopsis(): String? {
    val sentences = sentences(overview ?: return null)
    val first = sentences.firstOrNull()?.takeIf { it.length <= SYNOPSIS_MAX_CHARS } ?: return null
    return sentences.getOrNull(1)?.let { "$first $it" }?.takeIf { it.length <= SYNOPSIS_MAX_CHARS } ?: first
}

/** A full stop, question or exclamation mark, a closing quote or bracket, then a capital or digit. */
private val sentenceEnd = Regex("""(?<=[.!?]["'\u201D\u2019)]?)\s+(?=["'\u201C\u2018(]?[A-Z0-9])""")

/** A word that ends in a full stop but does not end a sentence: an initial or a title. */
private val abbreviation = Regex("""[A-Z]|[A-Z]\.[A-Z]|Mr|Mrs|Ms|Dr|St|Jr|Sr|Prof|Lt|Col|Gen|Capt|Sgt|Mt|vs""")

private fun sentences(text: String): List<String> {
    val clean = text.replace(Regex("""\s+"""), " ").trim()
    val found = mutableListOf<String>()
    var start = 0
    for (end in sentenceEnd.findAll(clean)) {
        val sentence = clean.substring(start, end.range.first)
        val lastWord = sentence.substringAfterLast(' ').trimStart('"', '\'', '\u201C', '\u2018', '(')
        if (sentence.endsWith('.') && abbreviation.matches(lastWord.dropLast(1))) continue
        found += sentence
        start = end.range.last + 1
    }
    if (start < clean.length) found += clean.substring(start)
    return found
}

/**
 * Title art with the Trakt logo, or the title as text, the year and genre, and a short synopsis
 * (design, "Screensaver"). A new slide every 40 s with a 2 s crossfade. The art pans 3 % and zooms 6 %
 * during each slide (ScreensaverMotion), at 45 % brightness. There is no clock: a clock would be the one thing that
 * never moves. With no titles the screen stays dark.
 *
 * The slides count on with one title too, so its art keeps moving and its logo and year keep
 * changing corner on an OLED screen.
 *
 * The first slide waits up to 3 s for its art and logo, then fades in from black as one, so no
 * part of it pops in (24 Oct 2026).
 */
@Composable
fun ScreensaverScreen(titles: List<Title>) {
    var slide by remember(titles) { mutableIntStateOf(0) }
    LaunchedEffect(titles) {
        if (titles.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(LauncherMotion.SCREENSAVER_SLIDE_MILLIS.toLong())
            slide++
        }
    }
    val density = LocalDensity.current
    val size = with(density) {
        LocalWindowInfo.current.containerSize.let { DpSize(it.width.toDp(), it.height.toDp()) }
    }
    val vignette = remember { vignette() }
    val loader = LocalArtLoader.current
    val currentSize by rememberUpdatedState(size)
    // Not keyed on the titles: a refresh while the screensaver shows does not fade it out again.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(titles.isNotEmpty()) {
        if (titles.isEmpty()) return@LaunchedEffect
        val first = titles[0]
        val screen = snapshotFlow { currentSize }.first { it.width > 0.dp && it.height > 0.dp }
        withTimeoutOrNull(FIRST_ART_WAIT_MILLIS) {
            listOfNotNull(
                ArtCache.key(first.backdropArt(Metahub.MEDIUM), screen, density, ArtMask.Screensaver),
                ArtCache.key(first.logoArt(), LogoBox, density, ArtMask.Silhouette, fit = true),
            ).forEach { ArtCache.load(it, loader) }
        }
        reveal.animateTo(1f, tween(LauncherMotion.SCREENSAVER_CROSSFADE_MILLIS))
    }

    Box(Modifier.fillMaxSize().background(LauncherColors.surfaceContainerLowest)) {
        if (titles.isEmpty()) return@Box
        // The next title loads during this one, so it fades in with its picture and logo.
        val next = titles[(slide + 1) % titles.size]
        PreloadArt(next.backdropArt(Metahub.MEDIUM), size, ArtMask.Screensaver)
        PreloadArt(next.logoArt(), LogoBox, ArtMask.Silhouette, fit = true)

        Crossfade(
            targetState = slide,
            modifier = Modifier.graphicsLayer { alpha = reveal.value },
            animationSpec = tween(LauncherMotion.SCREENSAVER_CROSSFADE_MILLIS),
            label = "screensaver",
        ) { shown ->
            val title = titles[shown % titles.size]
            val motion = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                motion.animateTo(1f, tween(LauncherMotion.SCREENSAVER_SLIDE_MILLIS + LauncherMotion.SCREENSAVER_CROSSFADE_MILLIS, easing = LinearEasing))
            }
            val direction = if (shown % 2 == 0) 1f else -1f
            val corner = corners[shown % corners.size]
            val end = corner == Alignment.TopEnd || corner == Alignment.BottomEnd
            Box(Modifier.fillMaxSize().clipToBounds()) {
                ArtImage(
                    art = remember(title) { title.backdropArt(Metahub.MEDIUM) },
                    size = size,
                    mask = ArtMask.Screensaver,
                    placeholder = Color.Transparent,
                    modifier = Modifier.graphicsLayer {
                        val zoom = ScreensaverMotion.scale(motion.value)
                        scaleX = zoom
                        scaleY = zoom
                        translationX = ScreensaverMotion.offset(motion.value, direction) * this.size.width
                    },
                )
                // The vignette is a small bitmap drawn to the full screen: a live radial
                // gradient across the screen is slow on the Chromecast.
                Spacer(
                    Modifier.fillMaxSize().drawBehind {
                        drawImage(
                            vignette,
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                            filterQuality = FilterQuality.Low,
                        )
                    },
                )
                Column(
                    Modifier.align(corner).padding(horizontal = 82.dp, vertical = 64.dp),
                    horizontalAlignment = if (end) Alignment.End else Alignment.Start,
                ) {
                    val type = LocalLauncherType.current
                    LogoImage(
                        art = remember(title) { title.logoArt() },
                        box = LogoBox,
                        alignment = if (end) Alignment.BottomEnd else Alignment.BottomStart,
                        mask = ArtMask.Silhouette,
                        // A small logo stays sharp at its own size (24 Oct 2026).
                        enlarge = false,
                    ) {
                        Label(title.title, type.placeholderLogo.copy(fontSize = 64.sp, lineHeight = 64.sp), Color(0xB3F3EFE6))
                    }
                    val meta = remember(title) { title.screensaverMeta() }
                    if (meta.isNotEmpty()) {
                        Label(meta, type.clock.copy(fontSize = 15.sp, lineHeight = 18.sp), LauncherColors.onSurfaceVariant.copy(alpha = 0.62f))
                    }
                    val synopsis = remember(title) { title.screensaverSynopsis() }
                    if (synopsis != null) {
                        Label(
                            synopsis,
                            type.synopsis.copy(textAlign = if (end) TextAlign.End else TextAlign.Start),
                            LauncherColors.onSurfaceVariant.copy(alpha = 0.62f),
                            Modifier.padding(top = 8.dp).width(SynopsisWidth),
                            maxLines = 4,
                        )
                    }
                }
            }
        }
    }
}

/** The design's wash: `radial-gradient(120% 90% at 60% 40%, transparent 40%, rgba(5,5,6,.85) 100%)`. */
private fun vignette(): ImageBitmap {
    val width = 192
    val height = 108
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        val center = Offset(0.6f * width, 0.4f * height)
        val rx = 1.2f * width
        val ry = 0.9f * height
        scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
            drawRect(
                Brush.radialGradient(0.4f to Color.Transparent, 1f to Color(0xD9050506), center = center, radius = rx),
                topLeft = Offset(0f, center.y - center.y * rx / ry),
                size = Size(width.toFloat(), height * rx / ry),
            )
        }
    }
    bitmap.prepareToDraw()
    return bitmap
}
