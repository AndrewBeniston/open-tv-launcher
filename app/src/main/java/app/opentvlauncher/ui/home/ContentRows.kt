package app.opentvlauncher.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.opentvlauncher.data.Art
import app.opentvlauncher.data.CardAction
import app.opentvlauncher.data.ContentRow
import app.opentvlauncher.data.PosterCard
import app.opentvlauncher.data.PosterRow
import app.opentvlauncher.data.WideCard
import app.opentvlauncher.data.WideRow
import app.opentvlauncher.ui.art.ArtCache
import app.opentvlauncher.ui.art.ArtImage
import app.opentvlauncher.ui.art.ArtMask
import app.opentvlauncher.ui.art.PosterLettering
import app.opentvlauncher.ui.art.ThumbnailLettering
import app.opentvlauncher.ui.focus.FocusGlow
import app.opentvlauncher.ui.focus.FocusSurface
import app.opentvlauncher.ui.focus.RowFocusMemory
import app.opentvlauncher.ui.focus.StartPivotRowScrolling
import app.opentvlauncher.ui.focus.rememberedFocusGroup
import app.opentvlauncher.ui.focus.rememberedFocusItem
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LauncherMotion
import app.opentvlauncher.ui.theme.LauncherShapes
import app.opentvlauncher.ui.theme.LocalLauncherType
import app.opentvlauncher.ui.theme.SquircleShape
import app.opentvlauncher.ui.theme.fade
import kotlinx.coroutines.launch

/**
 * One content row: header, cards, and titles under the cards when the row has focus.
 * [focusMemory] remembers the row's last focused card; D-pad moves into the row go back to it.
 * Cards are keyed by the title or video they show. If a refresh removes the focused card while
 * Home is [active], focus goes to the card now at its place.
 *
 * Row with focus: 100 %, a 24 dp header (a 1.5x layer scale of the 16 dp header, so no text
 * is measured again), titles and meta under each card. Row without focus: 60 %, no titles.
 */
@Composable
fun ContentRowSection(
    row: ContentRow,
    rowFocused: Boolean,
    onItemFocused: (Art) -> Unit,
    onOpen: (CardAction) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    focusMemory: RowFocusMemory = remember { RowFocusMemory() },
    active: Boolean = true,
    onLongPress: ((CardAction) -> Unit)? = null,
) {
    val reveal = animateFloatAsState(if (rowFocused) 1f else 0f, LauncherMotion.focus(), label = "row reveal")
    val keys = remember(row) {
        uniqueKeys(
            when (row) {
                is WideRow -> row.cards.map { it.key }
                is PosterRow -> row.posters.map { it.key }
            },
        )
    }
    val count = keys.size
    focusMemory.keys = keys
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    focusMemory.scrollTo = { index, then ->
        scope.launch {
            listState.scrollToItem(index)
            withFrameNanos { } // The card at index is composed during this layout.
            then()
        }
    }
    LaunchedEffect(keys) {
        if (rowFocused && active && !focusMemory.hasRemembered) {
            withFrameNanos { } // The lazy row composes its new cards during layout.
            focusMemory.focusRemembered()
        }
    }

    Column(modifier.fillMaxWidth()) {
        RowHeader(row.title, HomeLayout.headerSlot(row), rowFocused, reveal)
        val cardHeight = HomeLayout.cardHeight(row)
        val titleArea = HomeLayout.titleArea(row)
        val titleGap = HomeLayout.titleGap(row)
        StartPivotRowScrolling(HomeLayout.sideMargin) {
            LazyRow(
                state = listState,
                // Made once, as the cards' modifiers below: a new modifier made the row measure again.
                modifier = remember(cardHeight, titleArea, reveal, focusRequester, focusMemory) {
                    Modifier
                        .revealTitles(cardHeight, titleArea, reveal)
                        .glowRoom(GlowRoom)
                        // Focus asked of the row goes to the card it remembers.
                        .focusRequester(focusRequester)
                        .rememberedFocusGroup(focusMemory)
                },
                contentPadding = PaddingValues(horizontal = HomeLayout.sideMargin, vertical = GlowRoom),
                horizontalArrangement = Arrangement.spacedBy(HomeLayout.gutter),
            ) {
                when (row) {
                    is WideRow -> itemsIndexed(row.cards, key = { index, _ -> keys[index] }) { index, card ->
                        WideCardItem(
                            card = card,
                            leadingGap = if (index == row.fillStart) HomeLayout.fillGap - HomeLayout.gutter else 0.dp,
                            titleGap = titleGap,
                            onOpen = onOpen,
                            onLongPress = card.action?.takeIf { onLongPress != null }?.let { action -> { onLongPress?.invoke(action) } },
                            rowFocused = rowFocused,
                            reveal = reveal,
                            onFocused = { onItemFocused(card.art) },
                            modifier = remember(focusMemory, keys[index], index, count) {
                                Modifier
                                    .rememberedFocusItem(focusMemory, keys[index], index)
                                    .rowEnds(isFirst = index == 0, isLast = index == count - 1)
                            },
                        )
                    }
                    is PosterRow -> itemsIndexed(row.posters, key = { index, _ -> keys[index] }) { index, poster ->
                        PosterItem(
                            poster = poster,
                            titleGap = titleGap,
                            onOpen = onOpen,
                            rowFocused = rowFocused,
                            reveal = reveal,
                            onFocused = { onItemFocused(poster.art) },
                            modifier = remember(focusMemory, keys[index], index, count) {
                                Modifier
                                    .rememberedFocusItem(focusMemory, keys[index], index)
                                    .rowEnds(isFirst = index == 0, isLast = index == count - 1)
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(HomeLayout.rowGap))
    }
}

/** Room above and below the cards for the focus glow's light to fade out. */
private val GlowRoom = if (FocusGlow.ENABLED) FocusGlow.reach + FocusGlow.drop else 0.dp

/**
 * A lazy row clips its content to its bounds, and only 15 dp past them across the row, which
 * would cut the focus glow off in a hard line. The row is measured [room] taller at the top and
 * the bottom, its cards are padded down by [room], and it is placed [room] higher: it takes the
 * same space, and the light has room to fade out.
 */
private fun Modifier.glowRoom(room: Dp) = layout { measurable, constraints ->
    val extra = room.roundToPx()
    val placeable = measurable.measure(constraints.copy(minHeight = constraints.minHeight + 2 * extra, maxHeight = constraints.maxHeight + 2 * extra))
    layout(placeable.width, placeable.height - 2 * extra) { placeable.place(0, -extra) }
}

/** The keys as they are, with "#2", "#3" added to repeats: a lazy row needs each key once. */
private fun uniqueKeys(keys: List<String>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return keys.map { key ->
        val n = (seen[key] ?: 0) + 1
        seen[key] = n
        if (n == 1) key else "$key#$n"
    }
}

/**
 * Lays the row out as tall as its cards plus [reveal] of the title area. The cards and titles
 * are always measured at full height, so an animating row only moves the rows below it.
 */
private fun Modifier.revealTitles(cardHeight: Dp, titleArea: Dp, reveal: State<Float>) =
    layout { measurable, constraints ->
        val full = (cardHeight + titleArea).roundToPx()
        val placeable = measurable.measure(constraints.copy(minHeight = full, maxHeight = full))
        val height = (cardHeight + titleArea * reveal.value.coerceIn(0f, 1f)).roundToPx()
        layout(placeable.width, height) { placeable.place(0, 0) }
    }

@Composable
private fun RowHeader(title: String, slot: Dp, rowFocused: Boolean, reveal: State<Float>) {
    val color = animateColorAsState(
        if (rowFocused) LauncherColors.onSurface else LauncherColors.onSurfaceVariant,
        LauncherMotion.effectsFast(),
        label = "header colour",
    )
    // Over the row below it, so the focus glow's light never covers the header text.
    Box(Modifier.fillMaxWidth().height(slot).zIndex(1f)) {
        Label(
            title,
            LocalLauncherType.current.rowHeader,
            color = { color.value },
            modifier = Modifier
                .padding(start = HomeLayout.sideMargin, top = HomeLayout.headerTextTop)
                .graphicsLayer {
                    val s = 1f + 0.5f * reveal.value
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 1f)
                },
        )
    }
}

@Composable
private fun WideCardItem(
    card: WideCard,
    onOpen: (CardAction) -> Unit,
    rowFocused: Boolean,
    reveal: State<Float>,
    onFocused: () -> Unit,
    titleGap: Dp,
    modifier: Modifier = Modifier,
    leadingGap: Dp = 0.dp,
    onLongPress: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = leadingGap).width(HomeLayout.wideCard.width)) {
        FocusSurface(
            onClick = { card.action?.let(onOpen) },
            onLongPress = onLongPress,
            glow = { FocusGlow.colour(ArtCache.tint(card.art)) },
            restCorner = LauncherShapes.large,
            focusedCorner = LauncherShapes.largeIncreased,
            restAlpha = if (rowFocused) 0.86f else 0.6f,
            description = card.title,
            onFocusChanged = {
                focused = it
                if (it) onFocused()
            },
            modifier = modifier.size(HomeLayout.wideCard),
        ) {
            ArtImage(
                card.art,
                HomeLayout.wideCard,
                mask = if (card.progress != null) ArtMask.BottomShade else ArtMask.None,
                corner = corner,
            )
            ThumbnailLettering(card.art.painted)
            card.progress?.let { progress ->
                ProgressBar(
                    progress,
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                        .fillMaxWidth()
                        .height(4.dp),
                )
            }
            card.duration?.let { DurationBadge(it, Modifier.align(Alignment.BottomEnd).padding(8.dp)) }
        }
        CardTitles(card.title, card.meta, titleLines = 2, focused = focused, reveal = reveal, gap = titleGap, shown = rowFocused)
    }
}

@Composable
private fun PosterItem(
    poster: PosterCard,
    onOpen: (CardAction) -> Unit,
    rowFocused: Boolean,
    reveal: State<Float>,
    onFocused: () -> Unit,
    titleGap: Dp,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(HomeLayout.poster.width)) {
        FocusSurface(
            onClick = { poster.action?.let(onOpen) },
            glow = { FocusGlow.colour(ArtCache.tint(poster.art)) },
            restCorner = LauncherShapes.medium,
            focusedCorner = LauncherShapes.large,
            restAlpha = if (rowFocused) 0.86f else 0.6f,
            description = poster.title,
            onFocusChanged = {
                focused = it
                if (it) onFocused()
            },
            modifier = modifier.size(HomeLayout.poster),
        ) {
            ArtImage(poster.art, HomeLayout.poster, corner = corner)
            PosterLettering(poster.art.painted, poster.title)
        }
        CardTitles(poster.title, poster.meta, titleLines = 1, focused = focused, reveal = reveal, gap = titleGap, shown = rowFocused)
    }
}

const val CARD_TITLES_TAG = "card-titles"

/** Title and meta under a card. Only the focused row shows them; the focused card's title turns on-surface. */
@Composable
private fun CardTitles(title: String, meta: String?, titleLines: Int, focused: Boolean, reveal: State<Float>, gap: Dp, shown: Boolean) {
    val type = LocalLauncherType.current
    val titleColor = animateColorAsState(
        if (focused) LauncherColors.onSurface else LauncherColors.onSurfaceVariant,
        LauncherMotion.effectsFast(),
        label = "title colour",
    )
    val metaColor = animateColorAsState(
        if (focused) LauncherColors.onSurfaceVariant else LauncherColors.onSurfaceFaint,
        LauncherMotion.effectsFast(),
        label = "meta colour",
    )
    Column(
        Modifier
            .padding(top = gap)
            .fade { reveal.value }
            // The card reads its own title to TalkBack. The tag lets HomeClearanceTest find the
            // titles of the focused row, the only row that shows them.
            .clearAndSetSemantics { if (shown) testTag = CARD_TITLES_TAG },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Label(title, type.cardTitle, color = { titleColor.value }, maxLines = titleLines)
        if (meta != null) Label(meta, type.cardMeta, color = { metaColor.value })
    }
}

@Composable
private fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color(0xBD000000), SquircleShape(LauncherShapes.extraSmall))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Label(text, LocalLauncherType.current.chip.copy(fontFeatureSettings = "tnum"), Color.White)
    }
}
