package app.opentvlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentvlauncher.data.AppKind
import app.opentvlauncher.data.AppTile
import app.opentvlauncher.system.netflixN
import app.opentvlauncher.ui.focus.FocusGlow
import app.opentvlauncher.ui.focus.FocusSurface
import app.opentvlauncher.ui.focus.RowFocusMemory
import app.opentvlauncher.ui.focus.rememberedFocusGroup
import app.opentvlauncher.ui.focus.rememberedFocusItem
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LauncherShapes
import app.opentvlauncher.ui.theme.LocalLauncherType
import app.opentvlauncher.ui.theme.SquircleShape
import app.opentvlauncher.ui.theme.TileTones

/**
 * Big tiles for YouTube, Stremio, Netflix and BBC iPlayer, then a narrow All tile for All Apps.
 * Home opens with focus on YouTube. The tiles use dark brand tones, because the apps row never
 * moves and a bright tile risks burn-in. Light from above gives each tile depth, with no outline.
 */
@Composable
fun AppsRow(
    apps: List<AppTile>,
    rowFocused: Boolean,
    focusMemory: RowFocusMemory,
    onOpen: (AppTile) -> Unit,
    onTileFocused: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .rememberedFocusGroup(focusMemory)
            .padding(horizontal = HomeLayout.sideMargin),
        horizontalArrangement = Arrangement.spacedBy(HomeLayout.gutter),
    ) {
        apps.forEachIndexed { index, app ->
            val narrow = app.kind == AppKind.AllApps
            FocusSurface(
                onClick = { onOpen(app) },
                restCorner = LauncherShapes.largeIncreased,
                focusedCorner = LauncherShapes.extraLarge,
                tone = app.kind.tone,
                restAlpha = if (rowFocused) 1f else 0.6f,
                // The narrow tile shows "All"; it reads as All Apps.
                description = if (narrow) app.label else null,
                depth = true,
                glow = { FocusGlow.colour(app.kind.tone.focused) },
                onFocusChanged = { if (it) onTileFocused(index) },
                modifier = Modifier
                    .size(if (narrow) HomeLayout.narrowTile else HomeLayout.tile)
                    .rememberedFocusItem(focusMemory, index)
                    .rowEnds(isFirst = index == 0, isLast = index == apps.lastIndex),
            ) {
                if (narrow) {
                    AppMark(app.kind, Modifier.align(Alignment.TopCenter).padding(top = 17.dp))
                    Label(
                        "All",
                        LocalLauncherType.current.label,
                        LauncherColors.onSurfaceVariant,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 15.dp),
                    )
                } else {
                    AppMark(app.kind, Modifier.padding(start = 18.dp, top = 17.dp))
                    Label(
                        app.label,
                        LocalLauncherType.current.label,
                        LauncherColors.onSurface,
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 18.dp, bottom = 15.dp),
                    )
                }
            }
        }
    }
}

/** Left and Right stop at the row ends. They do not wrap and they do not jump to another row. */
fun Modifier.rowEnds(isFirst: Boolean, isLast: Boolean): Modifier = focusProperties {
    if (isFirst) left = FocusRequester.Cancel
    if (isLast) right = FocusRequester.Cancel
}

internal val AppKind.tone
    get() = when (this) {
        AppKind.YouTube -> TileTones.youTube
        AppKind.Stremio -> TileTones.stremio
        AppKind.Netflix -> TileTones.netflix
        AppKind.IPlayer -> TileTones.iPlayer
        AppKind.AllApps -> TileTones.allApps
    }

/** Simple marks drawn in code. The real app icons come from the package manager in a later step. */
@Composable
internal fun AppMark(kind: AppKind, modifier: Modifier = Modifier) {
    when (kind) {
        AppKind.YouTube -> Box(
            modifier
                .size(40.dp, 28.dp)
                .background(Color(0xFFD3272D), SquircleShape(8.dp))
                .drawBehind { triangle(Offset(15.5.dp.toPx(), 8.dp.toPx()), 11.dp.toPx(), 12.dp.toPx()) },
        )
        AppKind.Stremio -> Box(modifier.size(28.dp)) {
            Spacer(
                Modifier
                    .padding(2.5.dp)
                    .size(23.dp)
                    .rotate(45f)
                    .background(Brush.linearGradient(listOf(Color(0xFF8C6DFF), Color(0xFF5639D0))), SquircleShape(6.dp)),
            )
            Spacer(Modifier.size(28.dp).drawBehind { triangle(Offset(10.5.dp.toPx(), 8.dp.toPx()), 10.dp.toPx(), 12.dp.toPx()) })
        }
        AppKind.Netflix -> Spacer(modifier.size(18.dp, 28.dp).drawBehind { netflixN() })
        AppKind.IPlayer -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { i -> BbcBlock("BBC"[i].toString()) }
        }
        AppKind.AllApps -> Box(modifier.size(26.dp)) {
            listOf(0.dp to 0.dp, 15.dp to 0.dp, 0.dp to 15.dp, 15.dp to 15.dp).forEach { (x, y) -> GridSquare(x, y) }
        }
    }
}

@Composable
private fun BbcBlock(letter: String) {
    Box(
        Modifier
            .size(20.dp)
            .background(Color(0xFFE9E6E0), SquircleShape(1.5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Label(
            letter,
            LocalLauncherType.current.label.copy(fontSize = 13.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            Color(0xFF111111),
        )
    }
}

@Composable
private fun GridSquare(x: Dp, y: Dp) {
    Spacer(
        Modifier
            .padding(start = x, top = y)
            .size(11.dp)
            .background(LauncherColors.onSurfaceVariant, SquircleShape(3.5.dp)),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.triangle(topLeft: Offset, width: Float, height: Float) {
    val path = Path().apply {
        moveTo(topLeft.x, topLeft.y)
        lineTo(topLeft.x + width, topLeft.y + height / 2f)
        lineTo(topLeft.x, topLeft.y + height)
        close()
    }
    drawPath(path, Color.White)
}
