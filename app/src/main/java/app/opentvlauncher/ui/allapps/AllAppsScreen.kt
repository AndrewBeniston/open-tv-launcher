package app.opentvlauncher.ui.allapps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentvlauncher.data.AppTile
import app.opentvlauncher.system.InstalledApp
import app.opentvlauncher.system.InstalledApps
import app.opentvlauncher.ui.focus.FocusSurface
import app.opentvlauncher.ui.focus.RowFocusMemory
import app.opentvlauncher.ui.focus.StartPivotRowScrolling
import app.opentvlauncher.ui.focus.rememberSavedRowFocusMemory
import app.opentvlauncher.ui.focus.rememberedFocusGroup
import app.opentvlauncher.ui.focus.rememberedFocusItem
import app.opentvlauncher.ui.home.AppMark
import app.opentvlauncher.ui.home.Clock
import app.opentvlauncher.ui.home.HomeLayout
import app.opentvlauncher.ui.home.rowEnds
import app.opentvlauncher.ui.home.tone
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LauncherShapes
import app.opentvlauncher.ui.theme.LocalLauncherType
import app.opentvlauncher.ui.theme.TileTones
import app.opentvlauncher.ui.theme.Tone
import kotlin.math.roundToInt

private val SmallTileSize = DpSize(124.dp, 70.dp)

/** Under the top bar: the bar's bottom plus the gap the page title had. */
private val UnderBarTop = 104.dp

/**
 * All Apps (design, "All Apps"): the apps on Home, the TV's other apps, and the System group.
 * The System group is the way back to the TV's own home screen and to system Settings, and the
 * Open TV Launcher tile opens the launcher's own Settings. The screen opens with focus on
 * YouTube, or on the Open TV Launcher tile after Back from Settings ([startOnLauncher]).
 * Back runs [onBack], except while not [active], as while the page fades out after another tab
 * was chosen. [installed] is null until the package list has loaded.
 *
 * As the Apps page under the top bar ([underBar]) it has no title of its own, and it takes focus
 * only when asked: at the start when [takeFocus], and each time [entryRequests] goes up after the
 * page was built, on the group and tile that had focus last. The page leaves composition while
 * another tab shows, so it keeps that group and tile as saved state. Up from the first group
 * runs [onUpFromTop].
 */
@Composable
fun AllAppsScreen(
    apps: List<AppTile>,
    installed: InstalledApps.Groups?,
    onOpenApp: (AppTile) -> Unit,
    onOpenInstalled: (InstalledApp) -> Unit,
    onOpenStockHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onBack: () -> Unit,
    startOnLauncher: Boolean = false,
    underBar: Boolean = false,
    takeFocus: Boolean = true,
    entryRequests: Int = 0,
    onUpFromTop: (() -> Unit)? = null,
    active: Boolean = true,
) {
    val type = LocalLauncherType.current
    val other = installed?.other.orEmpty()
    val playStore = installed?.playStore
    // The TV's own home app, by its own name (Google TV Home on a Chromecast). The design's name
    // until the TV's apps are read.
    val homeLabel = installed?.homeLabel ?: "Google Launcher"
    val onHome = rememberSavedRowFocusMemory(apps.size)
    val otherFocus = rememberSavedRowFocusMemory(other.size)
    // Settings, the Play Store when the TV has it, the TV's own home app, Open TV Launcher.
    val systemCount = if (playStore != null) 4 else 3
    val system = rememberSavedRowFocusMemory(systemCount)
    val stockHomeIndex = systemCount - 2
    var stockHomeFocused by remember { mutableStateOf(false) }
    // The group that had focus last: 0 On Home, 1 Other Apps, 2 System.
    var lastGroup by rememberSaveable { mutableIntStateOf(if (startOnLauncher) 2 else 0) }
    val groups = listOf(onHome, otherFocus, system)
    // With none of Home's apps installed, the On Home group is left out and the next group is first.
    val first = when {
        apps.isNotEmpty() -> onHome
        other.isNotEmpty() -> otherFocus
        else -> system
    }
    LaunchedEffect(Unit) {
        if (!takeFocus) return@LaunchedEffect
        if (startOnLauncher) system.focus(systemCount - 1) else first.focus(0)
    }
    // A request made before the page was built is not one for this page: returning to the tab
    // shows the page and leaves focus in the bar.
    val firstEntries = remember { entryRequests }
    LaunchedEffect(entryRequests) {
        if (entryRequests == firstEntries) return@LaunchedEffect
        if (!groups[lastGroup].focusRemembered()) first.focus(0)
    }
    BackHandler(enabled = active, onBack = onBack)
    val margin = Modifier.padding(start = HomeLayout.sideMargin)
    fun Modifier.group(memory: RowFocusMemory) = onFocusChanged { if (it.hasFocus) lastGroup = groups.indexOf(memory) }

    // Up from the first group goes to the bar.
    fun Modifier.upToBar(memory: RowFocusMemory) = if (memory != first) {
        this
    } else {
        onPreviewKeyEvent {
            val up = it.key == Key.DirectionUp && it.type == KeyEventType.KeyDown
            if (up) onUpFromTop?.invoke()
            up && onUpFromTop != null
        }
    }

    // The window paints the surface colour (themes.xml).
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(top = if (underBar) UnderBarTop else 32.dp)) {
            if (!underBar) {
                Label("All Apps", type.pageTitle, LauncherColors.onSurface, margin)
                Spacer(Modifier.height(28.dp))
            }
            if (apps.isNotEmpty()) {
                GroupLabel("On Home")
                TileRow(onHome, margin.group(onHome).upToBar(onHome)) {
                    apps.forEachIndexed { index, app ->
                        SmallTile(
                            label = app.label,
                            tone = app.kind.tone,
                            onClick = { onOpenApp(app) },
                            modifier = Modifier
                                .rememberedFocusItem(onHome, index)
                                .rowEnds(isFirst = index == 0, isLast = index == apps.lastIndex),
                        ) {
                            AppMark(
                                app.kind,
                                Modifier.graphicsLayer {
                                    scaleX = 0.8f
                                    scaleY = 0.8f
                                    transformOrigin = TransformOrigin(0f, 0f)
                                },
                            )
                        }
                    }
                }
            }
            if (other.isNotEmpty()) {
                if (apps.isNotEmpty()) Spacer(Modifier.height(28.dp))
                GroupLabel("Other Apps")
                StartPivotRowScrolling(HomeLayout.sideMargin) {
                    LazyRow(
                        modifier = Modifier.group(otherFocus).upToBar(otherFocus).rememberedFocusGroup(otherFocus),
                        contentPadding = PaddingValues(horizontal = HomeLayout.sideMargin),
                        horizontalArrangement = Arrangement.spacedBy(HomeLayout.gutter),
                    ) {
                        itemsIndexed(other, key = { _, app -> app.packageName }) { index, app ->
                            SmallTile(
                                label = app.label,
                                tone = TileTones.other,
                                onClick = { onOpenInstalled(app) },
                                modifier = Modifier
                                    .rememberedFocusItem(otherFocus, index)
                                    .rowEnds(isFirst = index == 0, isLast = index == other.lastIndex),
                            ) { AppIcon(app) }
                        }
                    }
                }
            }
            if (first != system) Spacer(Modifier.height(28.dp))
            GroupLabel("System")
            TileRow(system, margin.group(system).upToBar(system)) {
                SmallTile(
                    label = "Settings",
                    tone = TileTones.other,
                    onClick = onOpenSettings,
                    modifier = Modifier.rememberedFocusItem(system, 0).rowEnds(isFirst = true, isLast = false),
                ) { SettingsGlyph() }
                if (playStore != null) {
                    SmallTile(
                        label = "Play Store",
                        tone = TileTones.other,
                        onClick = { onOpenInstalled(playStore) },
                        modifier = Modifier.rememberedFocusItem(system, 1).rowEnds(isFirst = false, isLast = false),
                    ) { PlayGlyph() }
                }
                SmallTile(
                    label = homeLabel,
                    tone = TileTones.other,
                    onClick = onOpenStockHome,
                    onFocusChanged = { stockHomeFocused = it },
                    twoLines = true,
                    modifier = Modifier.rememberedFocusItem(system, stockHomeIndex).rowEnds(isFirst = false, isLast = false),
                ) { HomeGlyph() }
                SmallTile(
                    label = "Open TV\nLauncher",
                    tone = TileTones.other,
                    onClick = onOpenLauncherSettings,
                    twoLines = true,
                    modifier = Modifier.rememberedFocusItem(system, systemCount - 1).rowEnds(isFirst = false, isLast = true),
                ) { SlidersGlyph() }
            }
            Spacer(Modifier.height(12.dp))
            // Under the home app's tile, as in the design.
            Label(
                "Opens $homeLabel.",
                type.cardMeta,
                LauncherColors.onSurfaceVariant,
                Modifier
                    .padding(start = HomeLayout.sideMargin + (SmallTileSize.width + HomeLayout.gutter) * stockHomeIndex)
                    .graphicsLayer { alpha = if (stockHomeFocused) 1f else 0f },
            )
        }
        // Under the bar, the bar's clock shows.
        if (!underBar) Clock(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = HomeLayout.sideMargin))
    }
}

/** The app's own icon at the size of the design's marks. */
@Composable
private fun AppIcon(app: InstalledApp) {
    val icon = app.icon
    Spacer(
        Modifier
            .size(22.dp)
            .drawBehind {
                if (icon != null) {
                    drawImage(icon, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Medium)
                }
            },
    )
}

/** The design's Play Store mark: a triangle. */
@Composable
private fun PlayGlyph() {
    Spacer(
        Modifier
            .size(22.dp)
            .drawBehind {
                val u = size.minDimension / 24f
                val path = Path().apply {
                    moveTo(5f * u, 3.5f * u)
                    lineTo(5f * u, 20.5f * u)
                    lineTo(20f * u, 12f * u)
                    close()
                }
                drawPath(path, LauncherColors.onSurfaceVariant)
            },
    )
}

@Composable
private fun GroupLabel(text: String) {
    Label(text, LocalLauncherType.current.eyebrow, LauncherColors.onSurfaceVariant, Modifier.padding(start = HomeLayout.sideMargin))
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun TileRow(memory: RowFocusMemory, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.rememberedFocusGroup(memory), horizontalArrangement = Arrangement.spacedBy(HomeLayout.gutter)) {
        content()
    }
}

/**
 * A small tile from the design's All Apps screen: 124 x 70 dp, corner 12 growing to 16. A
 * [twoLines] label is smaller and sits lower, and its mark moves up (design, `.tile.sm.two`).
 */
@Composable
private fun SmallTile(
    label: String,
    tone: Tone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    twoLines: Boolean = false,
    mark: @Composable () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        restCorner = LauncherShapes.medium,
        focusedCorner = LauncherShapes.large,
        tone = tone,
        onFocusChanged = onFocusChanged,
        modifier = modifier.size(SmallTileSize),
    ) {
        val type = LocalLauncherType.current
        Box(Modifier.padding(start = 12.dp, top = if (twoLines) 8.dp else 11.dp)) { mark() }
        Label(
            label,
            if (twoLines) type.cardTitle.copy(fontSize = 13.sp, lineHeight = 15.sp) else type.cardTitle,
            LauncherColors.onSurface,
            Modifier
                .align(Alignment.BottomStart)
                // The same margin both sides, so no title reaches the card's edge.
                .padding(start = 12.dp, end = 12.dp, bottom = if (twoLines) 6.dp else 9.dp),
            maxLines = if (twoLines) 2 else 1,
        )
    }
}

/** The design's Settings mark: a dashed ring round a solid ring. */
@Composable
private fun SettingsGlyph() {
    Spacer(
        Modifier
            .size(22.dp)
            .drawBehind {
                val unit = size.minDimension / 24f
                val color = LauncherColors.onSurfaceVariant
                drawCircle(
                    color,
                    radius = 8.2f * unit,
                    style = Stroke(width = 3.4f * unit, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.2f * unit, 3.24f * unit))),
                )
                drawCircle(color, radius = 5.6f * unit, style = Stroke(width = 2.6f * unit))
            },
    )
}

/** The design's mark for the launcher's own Settings: three sliders. */
@Composable
private fun SlidersGlyph() {
    Spacer(
        Modifier
            .size(18.dp)
            .drawBehind {
                val u = size.minDimension / 24f
                val color = LauncherColors.onSurfaceVariant
                listOf(6f to 8f, 12f to 16f, 18f to 10f).forEach { (y, knob) ->
                    drawRoundRect(color, Offset(3f * u, (y - 1f) * u), Size(18f * u, 2f * u), CornerRadius(u))
                    drawCircle(color, 2.6f * u, Offset(knob * u, y * u))
                }
            },
    )
}

/** The design's Home mark: a house. */
@Composable
private fun HomeGlyph() {
    Spacer(
        Modifier
            .size(22.dp)
            .drawBehind {
                val u = size.minDimension / 24f
                val path = Path().apply {
                    moveTo(3f * u, 11.2f * u)
                    lineTo(12f * u, 4f * u)
                    lineTo(21f * u, 11.2f * u)
                    lineTo(21f * u, 20f * u)
                    lineTo(15f * u, 20f * u)
                    lineTo(15f * u, 14f * u)
                    lineTo(9f * u, 14f * u)
                    lineTo(9f * u, 20f * u)
                    lineTo(3f * u, 20f * u)
                    close()
                }
                drawPath(path, LauncherColors.onSurfaceVariant)
            },
    )
}
