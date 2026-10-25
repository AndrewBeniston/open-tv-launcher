package app.opentvlauncher.ui.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.opentvlauncher.data.ContentRow
import app.opentvlauncher.data.PosterRow
import app.opentvlauncher.data.WideRow
import kotlin.math.ceil

/** The part of the home screen that has focus. The page scrolls by section. */
sealed interface HomeSection {
    data object Banner : HomeSection
    data object Apps : HomeSection
    data class Row(val index: Int) : HomeSection

    companion object {
        /** Saves a section as a number: -2 the banner, -1 the apps row, or the row's index. */
        val Saver = Saver<MutableState<HomeSection>, Int>(
            save = {
                when (val section = it.value) {
                    Banner -> -2
                    Apps -> -1
                    is Row -> section.index
                }
            },
            restore = {
                mutableStateOf(
                    when (it) {
                        -2 -> Banner
                        -1 -> Apps
                        else -> Row(it)
                    },
                )
            },
        )
    }
}

/**
 * Home screen geometry in dp, from the design's "Spacing and grid" and the wireframes
 * (1 dp is 2 px at 1080p; the screen is 960 dp wide).
 *
 * Home at rest, top edges: eyebrow 106, title 125, meta 179, progress 202, app tiles 244,
 * row header 382, cards 421. With focus in the banner the app tiles move down to 338.
 * The cards sit lower than the design's 410, to keep [textClearance] round a focused card.
 */
object HomeLayout {
    val sideMargin = 58.dp
    val gutter = 20.dp

    /** Between the paused cards and the watchlist titles in a short Continue Watching row. */
    val fillGap = 40.dp

    val heroHeight = 420.dp
    val bannerCollapsed = 244.dp
    val bannerExpanded = 338.dp

    /**
     * The apps row: four large tiles, then the narrow All tile, about 0.3 of a large tile's
     * width (24 Oct 2026). With the gutters they fill the 844 dp between the margins
     * exactly, so the row never slides.
     */
    val tile = DpSize(178.dp, 110.dp)
    val narrowTile = DpSize(52.dp, 110.dp)
    val wideCard = DpSize(196.dp, 110.dp)
    val poster = DpSize(124.dp, 186.dp)

    /** A focused tile, card or poster grows to this scale (FocusSurface). */
    const val FOCUS_SCALE = 1.1f

    /**
     * The least space between a focused, scaled item and any text: the row header above, its
     * own title below, the banner text. It was asked for on 24 Oct 2026, when a focused poster
     * almost touched its row header. HomeClearanceTest checks it on every row.
     */
    val textClearance = 12.dp

    val headerTextTop = 12.dp

    /** The header's 16 sp text is drawn 21 dp tall: Compose keeps a line at least as tall as the font. */
    private val headerLine = 21.dp

    /** How far a focused item grows above and below its card at [FOCUS_SCALE]. */
    fun focusGrowth(row: ContentRow): Dp = cardHeight(row) * ((FOCUS_SCALE - 1f) / 2f)

    /**
     * Space above each row's cards: the header text, then [textClearance] plus the focus growth,
     * rounded up to whole dp. Wide rows 51 dp, poster rows 55 dp.
     */
    fun headerSlot(row: ContentRow): Dp = ceil(headerTextTop + headerLine + textClearance + focusGrowth(row))

    /** Space between the cards and their titles: [textClearance] plus the focus growth. Wide 18 dp, posters 22 dp. */
    fun titleGap(row: ContentRow): Dp = ceil(textClearance + focusGrowth(row))

    /** The title lines and the meta line under a card, plus 4 dp below. */
    private fun titleLines(row: ContentRow): Dp = when (row) {
        is WideRow -> 18.dp * 2 + 2.dp + 16.dp + 4.dp
        is PosterRow -> 18.dp + 2.dp + 16.dp + 4.dp
    }

    /** Space between a row's cards and the next row's header slot. */
    val rowGap = 16.dp

    /** Where a focused content row's header slot sits after the page scrolls (Content rows screen). */
    val focusedRowTop = 53.dp

    fun cardHeight(row: ContentRow): Dp = when (row) {
        is WideRow -> wideCard.height
        is PosterRow -> poster.height
    }

    /** Space below cards for the title and meta. Only the focused row opens it. */
    fun titleArea(row: ContentRow): Dp = titleGap(row) + titleLines(row)

    /** A content row's height while it does not have focus. */
    fun restHeight(row: ContentRow): Dp = headerSlot(row) + cardHeight(row) + rowGap

    /** Top of the first content row's header slot, with the banner collapsed. */
    val firstRowTop: Dp = bannerCollapsed + tile.height + rowGap

    /**
     * How far the page scrolls up so that [section] is in view. On a page with no apps row
     * ([hasApps] false), the first row takes the apps row's place under the banner and does not
     * scroll.
     */
    fun pageOffset(section: HomeSection, rows: List<ContentRow>, hasApps: Boolean = true): Dp = when {
        section !is HomeSection.Row -> 0.dp
        !hasApps && section.index == 0 -> 0.dp
        else -> {
            val first = if (hasApps) firstRowTop else bannerCollapsed
            val rowTop = rows.take(section.index).fold(first) { top, row -> top + restHeight(row) }
            rowTop - focusedRowTop
        }
    }
}

private fun ceil(value: Dp): Dp = Dp(ceil(value.value - 0.001f))
