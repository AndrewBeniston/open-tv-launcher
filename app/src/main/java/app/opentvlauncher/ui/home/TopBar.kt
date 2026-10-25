package app.opentvlauncher.ui.home

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.ui.focus.FocusSurface
import app.opentvlauncher.ui.focus.activePage
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LauncherMotion
import app.opentvlauncher.ui.theme.LocalLauncherType
import app.opentvlauncher.ui.theme.Tone
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The top bar's pages (Up Next exploration, "The top bar"). Movies and Shows list one [kind] of title. */
enum class Tab(val label: String, val kind: TitleKind? = null) {
    Home("Home"),
    Movies("Movies", TitleKind.Movie),
    Shows("Shows", TitleKind.Show),
    Apps("Apps"),
}

/** The buttons right of the tabs. Assistant shows only when the TV answers ACTION_ASSIST. */
enum class BarButton(val label: String) {
    Assistant("Assistant"),
    Screensaver("Start Screensaver"),
    Settings("Settings"),
}

/** A tab or a button in the bar. */
sealed interface BarItem {
    data class OfTab(val tab: Tab) : BarItem

    data class OfButton(val button: BarButton) : BarItem
}

/** Asks the bar to take focus on [target]. A request with a new [id] asks again. */
data class BarRequest(val target: BarItem, val id: Int)

/** How far the page under the bar has scrolled, read as the bar is placed, so the bar moves away with the banner. */
class PageScroll {
    var offset: () -> Float = { 0f }
}

/**
 * Where the bar's two pills sit, the tabs' and the buttons', in the bar's own coordinates. A page
 * draws what is behind each pill, blurred and clipped to its shape ([BarBlur]). The bar and the
 * page scroll together, so these do not change as the page scrolls.
 */
@Stable
class BarShapes {
    var pills by mutableStateOf(emptyList<Rect>())
        internal set

    /** The clock's left edge, so Home's status chip can sit beside it. */
    var clockStart by mutableStateOf<Float?>(null)
        internal set
}

/**
 * The page under a quiet top bar: the tabs (Home, Movies, Shows, Apps), then the Assistant,
 * Screensaver and Settings buttons. The tabs and the buttons each sit on a pill, faint while focus is below the
 * bar and full when focus reaches it, and the focused item takes a light fill, as on Google TV.
 * Only focus is white. Every other tab label is light grey, the page on screen's too. The tabs'
 * pill starts on the page's content line. It scrolls away with the banner ([scroll]).
 *
 * Focus: Left and Right move along the tabs and buttons and stop at the ends. Focus on a tab
 * shows its page ([onTab]) once focus has rested there for [TAB_SETTLE_MILLIS]; focus stays in
 * the bar. A page takes a few frames to build on the Chromecast, so building each page that
 * focus passed made the bar slow to move along. Down, or OK on a tab, shows the focused tab's
 * page at once and runs [onDown], which moves focus into the page. [barRequest] moves focus into
 * the bar. While not [active], as under the details page, the bar takes no focus.
 */
@Composable
fun LauncherTabs(
    tabs: List<Tab>,
    tab: Tab,
    buttons: List<BarButton>,
    barRequest: BarRequest?,
    scroll: PageScroll,
    active: Boolean,
    onTab: (Tab) -> Unit,
    onButton: (BarButton) -> Unit,
    onDown: () -> Unit,
    onBarFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shapes: BarShapes = remember { BarShapes() },
    page: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        page()
        TopBar(
            tabs = tabs,
            tab = tab,
            buttons = buttons,
            barRequest = barRequest,
            onTab = onTab,
            onButton = onButton,
            onDown = onDown,
            onBarFocus = onBarFocus,
            shapes = shapes,
            modifier = Modifier
                .offset { IntOffset(0, -scroll.offset().roundToInt()) }
                .activePage(active),
        )
        // One clock for every tab, in the bar, centred on the pills' centre line, in white: it has
        // no background (24 and 25 Oct 2026).
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, -scroll.offset().roundToInt()) }
                .padding(top = TOP_BAR_TOP.dp, end = HomeLayout.sideMargin)
                .height(TOP_BAR_HEIGHT.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Clock(Modifier.onPlaced { shapes.clockStart = it.positionInRoot().x }.testTag(CLOCK_TAG), color = LauncherColors.onSurface)
        }
    }
}

const val TABS_PILL_TAG = "bar-tabs-pill"
const val BUTTONS_PILL_TAG = "bar-buttons-pill"
const val BAR_BLUR_TAG = "bar-blur"
const val CLOCK_TAG = "clock"

/**
 * What is behind the bar's pills, blurred (24 Oct 2026): one blurred layer for each pill,
 * clipped exactly to that pill's shape, so no blur shows outside them. A page draws it over its
 * own art, in its own scroll and fade, with [backdrop] drawing the page's background as it is on
 * screen, opaque. Each layer blurs only its pill and a margin of one blur radius round it, so
 * the pill's edge takes the art beyond it into the blur.
 */
@Composable
fun BarBlur(shapes: BarShapes, modifier: Modifier = Modifier, backdrop: @Composable () -> Unit) {
    val density = LocalDensity.current
    val screen = with(density) { LocalWindowInfo.current.containerSize.toSize().toDpSize() }
    val radius = with(density) { BarBlurRadius.toPx() }
    shapes.pills.forEachIndexed { index, pill ->
        val margin = radius.roundToInt()
        Box(
            modifier
                .offset { pill.topLeft.round() }
                .size(with(density) { pill.size.toDpSize() })
                .clip(RoundedCornerShape(percent = 50))
                .testTag("$BAR_BLUR_TAG-$index"),
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .offset { IntOffset(-margin, -margin) }
                    .requiredSize(with(density) { DpSize(pill.width.toDp() + BarBlurRadius * 2, pill.height.toDp() + BarBlurRadius * 2) })
                    .graphicsLayer {
                        renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                        clip = true
                    },
            ) {
                Box(
                    Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset { IntOffset(margin - pill.left.roundToInt(), margin - pill.top.roundToInt()) }
                        .requiredSize(screen),
                ) { backdrop() }
            }
        }
    }
}

/** The blur chosen on the TV: 40 px there. */
private val BarBlurRadius = 20.dp

const val TOP_BAR_TOP = 32
const val TOP_BAR_HEIGHT = 44
const val TAB_SETTLE_MILLIS = 250L

@Composable
private fun TopBar(
    tabs: List<Tab>,
    tab: Tab,
    buttons: List<BarButton>,
    barRequest: BarRequest?,
    onTab: (Tab) -> Unit,
    onButton: (BarButton) -> Unit,
    onDown: () -> Unit,
    onBarFocus: (Boolean) -> Unit,
    shapes: BarShapes,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    // The two pills' places, for the blur behind them.
    val pills = remember { arrayOfNulls<LayoutCoordinates>(2) }
    val fill by animateFloatAsState(if (focused) 1f else 0f, LauncherMotion.effectsFast(), label = "bar fill")
    val requesters = remember { mutableMapOf<BarItem, FocusRequester>() }
    fun requester(item: BarItem) = requesters.getOrPut(item) { FocusRequester() }
    LaunchedEffect(barRequest) {
        val request = barRequest ?: return@LaunchedEffect
        // After the page's own first focus request, so the bar has the last word.
        withFrameNanos { }
        requester(request.target).requestFocus()
    }
    val items = tabs.map(BarItem::OfTab) + buttons.map(BarItem::OfButton)
    var focusedItem by remember { mutableStateOf<BarItem?>(null) }
    // The focused item has gone, such as Assistant when the TV no longer answers it: the first
    // item left in its pill takes focus. Read in the composition that removes it, before its focus goes.
    val gone = focusedItem?.takeIf { focused && it !in items }
    val stand = gone?.let { item -> items.firstOrNull { it::class == item::class } ?: items.firstOrNull() }
    LaunchedEffect(items) { stand?.let { requester(it).requestFocus() } }
    // The tab that has focus, until its page shows.
    var settling by remember { mutableStateOf<Tab?>(null) }
    LaunchedEffect(settling) {
        val next = settling ?: return@LaunchedEffect
        delay(TAB_SETTLE_MILLIS)
        onTab(next)
    }
    // The tab whose page Down or OK is waiting to enter.
    var entering by remember { mutableStateOf<Tab?>(null) }
    val shownTab by rememberUpdatedState(tab)
    LaunchedEffect(entering) {
        val target = entering ?: return@LaunchedEffect
        // Two frames: the page is built, then its first effects run.
        withFrameNanos { }
        withFrameNanos { }
        entering = null
        // Back, Left or Right since then has moved focus or changed the tab: that place wins.
        if (shownTab == target && focusedItem == BarItem.OfTab(target)) onDown()
    }

    /**
     * Into the page. A tab whose page is not showing yet shows it first, and focus goes in once
     * it is built ([entering]), unless focus or the tab changes before that.
     */
    fun goDown() {
        val next = settling
        if (next == null || next == tab) {
            entering = null
            onDown()
            return
        }
        settling = null
        onTab(next)
        entering = next
    }

    fun onItemFocus(item: BarItem, has: Boolean) {
        if (has) {
            focusedItem = item
        } else if (focusedItem == item) {
            focusedItem = null
            entering = null
        }
    }

    Row(
        modifier
            .onGloballyPositioned { bar ->
                shapes.pills = pills.mapNotNull { pill -> pill?.takeIf { it.isAttached }?.let { bar.localBoundingBoxOf(it, clipBounds = false) } }
            }
            // The tabs' pill starts on the content line below.
            .padding(start = HomeLayout.sideMargin, top = TOP_BAR_TOP.dp)
            .onFocusChanged {
                focused = it.hasFocus
                onBarFocus(it.hasFocus)
            }
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.DirectionDown -> {
                        goDown()
                        true
                    }
                    // Nothing is above the bar.
                    Key.DirectionUp -> true
                    else -> false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.onPlaced { pills[0] = it }.testTag(TABS_PILL_TAG).barPill { fill }, verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t ->
                val item = BarItem.OfTab(t)
                // Keyed by the item, so when another item goes this one keeps its focus, and a press
                // on an item that goes ends with it, never on the item that takes its place.
                key(item) {
                    TabLabel(
                        label = t.label,
                        onClick = ::goDown,
                        onFocusChanged = { has ->
                            onItemFocus(item, has)
                            if (has) settling = t else if (settling == t) settling = null
                        },
                        modifier = Modifier
                            .focusRequester(requester(item))
                            .rowEnds(isFirst = item == items.first(), isLast = item == items.last()),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Row(Modifier.onPlaced { pills[1] = it }.testTag(BUTTONS_PILL_TAG).barPill { fill }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            buttons.forEach { b ->
                val item = BarItem.OfButton(b)
                key(item) {
                    BarIconButton(
                        button = b,
                        barFocused = focused,
                        onClick = { onButton(b) },
                        onFocusChanged = { has -> onItemFocus(item, has) },
                        modifier = Modifier
                            .focusRequester(requester(item))
                            .rowEnds(isFirst = false, isLast = item == items.last()),
                    )
                }
            }
        }
    }
}

/**
 * The focused item's light fill. No outline and no scale: the bar stays still. It fades from a
 * clear version of the same colour, not from clear black, so part-way it is a lighter pill, never
 * a darker one.
 */
private val ItemTone = Tone(LauncherColors.onSurface.copy(alpha = 0f), LauncherColors.onSurface)

/**
 * A label's colour over the item's light fill, from the same value that draws the fill: [light]
 * on the pill, and dark once the fill is bright enough that dark reads better (24 Oct
 * 2026: two states only). The change comes where the two have the same contrast, about 2.8:1 for
 * the one frame that passes it, so no frame shows dark text on the dark pill or light text on
 * the light fill.
 */
private fun onHighlight(fill: Float, light: Color) = if (fill > 0.27f) LauncherColors.surface else light

/**
 * The pill under the tabs, and the one under the buttons: faint while focus is below the bar, so
 * the bar never floats on nothing, and full as [fill] goes to 1 with focus in the bar.
 */
private fun Modifier.barPill(fill: () -> Float) = this
    .height(TOP_BAR_HEIGHT.dp)
    .drawBehind { drawRoundRect(lerp(PillRest, PillFocused, fill()), cornerRadius = CornerRadius(size.height / 2)) }
    .padding(PillPadding)

// Translucent tints over the blurred art ([BarBlur]), at Google TV's level (25 Oct 2026):
// 12.9 % with focus in the bar, measured on Google's own screenshots, and 8.1 % at rest, the same
// 5 : 8 ratio as before.
private val PillRest = LauncherColors.onSurface.copy(alpha = 0.081f)
private val PillFocused = LauncherColors.onSurface.copy(alpha = 0.129f)
private val PillPadding = 4.dp
private val TabLabelPadding = 18.dp

/** A tab or a button inside its pill. */
private val PillItemHeight = TOP_BAR_HEIGHT.dp - PillPadding * 2

@Composable
private fun TabLabel(
    label: String,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusSurface(
        onClick = onClick,
        restCorner = PillItemHeight / 2,
        focusedCorner = PillItemHeight / 2,
        tone = ItemTone,
        focusedScale = 1f,
        elevation = 0.dp,
        onFocusChanged = onFocusChanged,
        modifier = modifier.height(PillItemHeight),
    ) {
        // Light grey on every tab, the page on screen too, and dark only on focus's light fill.
        Label(
            label,
            LocalLauncherType.current.label,
            Modifier.align(Alignment.Center).padding(horizontal = TabLabelPadding),
            color = { onHighlight(fill(), LauncherColors.onSurfaceVariant) },
        )
    }
}

@Composable
private fun BarIconButton(
    button: BarButton,
    barFocused: Boolean,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusSurface(
        onClick = onClick,
        restCorner = PillItemHeight / 2,
        focusedCorner = PillItemHeight / 2,
        tone = ItemTone,
        focusedScale = 1f,
        elevation = 0.dp,
        onFocusChanged = onFocusChanged,
        description = button.label,
        modifier = modifier.size(PillItemHeight),
    ) {
        val light = if (barFocused) LauncherColors.onSurfaceVariant else LauncherColors.onSurfaceVariant.copy(alpha = 0.7f)
        val context = LocalContext.current
        val spark = remember(button) { if (button == BarButton.Assistant) geminiSpark(context.packageManager) else null }
        Spacer(
            Modifier
                .align(Alignment.Center)
                .size(20.dp)
                .drawBehind {
                    val color = onHighlight(fill(), light)
                    when (button) {
                        BarButton.Assistant -> if (spark != null) drawDrawable(spark) else drawGlyph(Sparkle, color)
                        BarButton.Screensaver -> drawSymbol(WallArt, color)
                        BarButton.Settings -> drawGear(color)
                    }
                },
        )
    }
}

/**
 * Gemini's spark in its own colours, as the TV's assistant app draws it (24 Oct 2026), or
 * null when that app has none. The app's icon is the Google "G", not the spark, so the spark is
 * found by its resource name. An update that renames it brings back the plain [Sparkle].
 */
@SuppressLint("DiscouragedApi") // Another app's resource has no R field here, only its name.
private fun geminiSpark(packageManager: PackageManager): Drawable? {
    val app = packageManager.resolveActivity(Intent(Intent.ACTION_ASSIST), 0)?.activityInfo?.packageName ?: return null
    return try {
        val resources = packageManager.getResourcesForApplication(app)
        val id = resources.getIdentifier("gemini_blue_spark", "drawable", app)
        if (id == 0) null else resources.getDrawable(id, null).mutate()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Resources.NotFoundException) {
        null
    }
}

/** [drawable] filling the whole area. */
private fun DrawScope.drawDrawable(drawable: Drawable) = drawIntoCanvas { canvas ->
    drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
    drawable.draw(canvas.nativeCanvas)
}

/** A path on a 24-unit grid, filled. */
private fun DrawScope.drawGlyph(path: Path, color: Color) {
    val unit = size.minDimension / 24f
    scale(unit, unit, pivot = Offset.Zero) { drawPath(path, color) }
}

/** A gear: eight teeth round a ring. */
private fun DrawScope.drawGear(color: Color) {
    val unit = size.minDimension / 24f
    repeat(8) { i ->
        rotate(i * 45f) {
            drawRoundRect(color, Offset(10.2f * unit, 1.5f * unit), Size(3.6f * unit, 4.5f * unit), CornerRadius(unit))
        }
    }
    drawCircle(color, radius = 6.8f * unit, style = Stroke(width = 3.2f * unit))
}

/** A four-point sparkle: the usual sign for an assistant. */
private val Sparkle = PathParser().parsePathString("M12 2C12.6 7.4 16.6 11.4 22 12C16.6 12.6 12.6 16.6 12 22C11.4 16.6 7.4 12.6 2 12C7.4 11.4 11.4 7.4 12 2Z").toPath()

/** A Material Symbols path, filled, on its 960-unit grid that runs from y = -960 to 0. */
private fun DrawScope.drawSymbol(path: Path, color: Color) {
    val unit = size.minDimension / 960f
    scale(unit, unit, pivot = Offset.Zero) { translate(top = 960f) { drawPath(path, color) } }
}

/** Material Symbols "wall_art", outlined: a framed picture on a hook, as Google TV's ambient button (25 Oct 2026). */
private val WallArt = PathParser().parsePathString(
    "M160-80q-33 0-56.5-23.5T80-160v-480q0-33 23.5-56.5T160-720h160l160-160 160 160h160q33 0 56.5 23.5T880-640v480q0 " +
        "33-23.5 56.5T800-80H160Zm0-80h640v-480H160v480Zm80-80h480L570-440 450-280l-90-120-120 160Zm460-200q25 0 42.5-17.5T760-500q0-25-17.5-42.5T700-560q-25 " +
        "0-42.5 17.5T640-500q0 25 17.5 42.5T700-440ZM404-720h152l-76-76-76 76ZM160-160v-480 480Z",
).toPath()
