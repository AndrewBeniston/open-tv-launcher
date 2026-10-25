package app.opentvlauncher.ui.home

import androidx.compose.ui.unit.dp
import app.opentvlauncher.data.SampleHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    private val rows = SampleHome.content.rows

    private val wide = rows[0]
    private val posters = rows[2]

    @Test
    fun homeAtRestMatchesTheDesign() {
        // Design, "Spacing and grid": app tiles 244 to 354, row header 382. The cards sit at 421 to
        // 531, 11 dp lower than the design's 410, for the text clearance round a focused card.
        assertEquals(244.dp, HomeLayout.bannerCollapsed)
        assertEquals(354.dp, HomeLayout.bannerCollapsed + HomeLayout.tile.height)
        assertEquals(382.dp, HomeLayout.firstRowTop + HomeLayout.headerTextTop)
        assertEquals(421.dp, HomeLayout.firstRowTop + HomeLayout.headerSlot(wide))
        assertEquals(531.dp, HomeLayout.firstRowTop + HomeLayout.headerSlot(wide) + HomeLayout.wideCard.height)
    }

    @Test
    fun aFocusedCardKeepsTwelveDpFromItsHeaderAndItsTitles() {
        listOf(wide, posters).forEach { row ->
            val growth = HomeLayout.focusGrowth(row)
            val headerBottom = HomeLayout.headerTextTop + 21.dp
            assert(HomeLayout.headerSlot(row) - headerBottom - growth >= HomeLayout.textClearance) { row.title }
            assert(HomeLayout.titleGap(row) - growth >= HomeLayout.textClearance) { row.title }
        }
        assertEquals(51.dp, HomeLayout.headerSlot(wide))
        assertEquals(55.dp, HomeLayout.headerSlot(posters))
        assertEquals(18.dp, HomeLayout.titleGap(wide))
        assertEquals(22.dp, HomeLayout.titleGap(posters))
    }

    @Test
    fun bannerAndAppsDoNotScroll() {
        assertEquals(0.dp, HomeLayout.pageOffset(HomeSection.Banner, rows))
        assertEquals(0.dp, HomeLayout.pageOffset(HomeSection.Apps, rows))
    }

    @Test
    fun aFocusedRowMovesUpToTheSameScreenPosition() {
        rows.indices.forEach { index ->
            val rowTop = rows.take(index).fold(HomeLayout.firstRowTop) { top, row -> top + HomeLayout.restHeight(row) }
            val onScreen = rowTop - HomeLayout.pageOffset(HomeSection.Row(index), rows)
            assertEquals("row $index", HomeLayout.focusedRowTop, onScreen)
        }
    }

    @Test
    fun pageOffsetsForTheSampleRows() {
        // Continue Watching: 370 - 53. Recommended on YouTube: + 51 + 110 + 16. Top Picks: the same
        // again. From Your Watchlist: + 55 + 186 + 16.
        assertEquals(317.dp, HomeLayout.pageOffset(HomeSection.Row(0), rows))
        assertEquals(494.dp, HomeLayout.pageOffset(HomeSection.Row(1), rows))
        assertEquals(671.dp, HomeLayout.pageOffset(HomeSection.Row(2), rows))
        assertEquals(928.dp, HomeLayout.pageOffset(HomeSection.Row(3), rows))
    }

    /** Movies and Shows: the first row sits in the apps row's place and does not scroll. */
    @Test
    fun withNoAppsRowTheFirstRowStaysAndTheNextRowsMoveUpToTheSamePlace() {
        assertEquals(0.dp, HomeLayout.pageOffset(HomeSection.Row(0), rows, hasApps = false))
        (1..rows.lastIndex).forEach { index ->
            val rowTop = rows.take(index).fold(HomeLayout.bannerCollapsed) { top, row -> top + HomeLayout.restHeight(row) }
            assertEquals("row $index", HomeLayout.focusedRowTop, rowTop - HomeLayout.pageOffset(HomeSection.Row(index), rows, hasApps = false))
        }
    }

    @Test
    fun appsRowFillsTheGridExactly() {
        // Four 178 dp tiles, the 52 dp All tile and four 20 dp gutters between 58 dp margins on a
        // 960 dp screen: the row fits without sliding.
        val width = HomeLayout.sideMargin * 2 + HomeLayout.tile.width * 4 + HomeLayout.narrowTile.width + HomeLayout.gutter * 4
        assertEquals(960.dp, width)
        // 24 Oct 2026: the All tile is a quarter to a third of a large tile's width.
        assertTrue(HomeLayout.narrowTile.width / HomeLayout.tile.width in 0.25f..0.34f)
        assertEquals(HomeLayout.tile.height, HomeLayout.narrowTile.height)
    }
}
