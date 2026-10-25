package app.opentvlauncher.ui.home

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import app.opentvlauncher.FakeTraktApp
import app.opentvlauncher.MainActivity
import app.opentvlauncher.home.HomeRepository
import app.opentvlauncher.home.HomeState
import app.opentvlauncher.ui.activityFocusManager
import app.opentvlauncher.ui.holdTvKey
import app.opentvlauncher.ui.releaseTvKey
import app.opentvlauncher.ui.repeatTvKey
import app.opentvlauncher.ui.sendTvKey
import app.opentvlauncher.ui.theme.LauncherColors
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The quiet top bar in the real activity (Up Next exploration, "How focus moves"): the Home,
 * Movies and Apps tabs, then the Screensaver and Settings buttons, and Assistant when the TV
 * answers it. The fake Trakt has films only, so there is no Shows tab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = FakeTraktApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TopBarRouteTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Harbour Lights").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.waitForIdle()
        // The window takes focus after Home asked for YouTube, so ask again, as the Home button does.
        compose.onAllNodesWithText("YouTube")[0].requestFocus()
        compose.waitForIdle()
    }

    @After
    fun tearDown() = (compose.activity.application as FakeTraktApp).server.close()

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        compose.sendTvKey(keyCode, compose.activityFocusManager)
        compose.waitForIdle()
    }

    private fun up(times: Int = 1) = press(KeyEvent.KEYCODE_DPAD_UP, times)
    private fun down(times: Int = 1) = press(KeyEvent.KEYCODE_DPAD_DOWN, times)
    private fun right(times: Int = 1) = press(KeyEvent.KEYCODE_DPAD_RIGHT, times)
    private fun left(times: Int = 1) = press(KeyEvent.KEYCODE_DPAD_LEFT, times)
    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** Lets focus rest on a tab long enough for its page to show. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(TAB_SETTLE_MILLIS + 50)
        compose.waitForIdle()
    }

    private fun assertFocusOn(name: String) = compose.onNode(isFocused()).assert(
        SemanticsMatcher("named $name") { hasText(name).matches(it) || hasContentDescription(name).matches(it) },
    )

    /** Design note: the tabs' pill, always shown now, starts on the content line, where "Continue Watching" starts. */
    @Test
    fun theTabPillLinesUpWithTheContent() {
        // The Home tab sits 4 dp inside the pill.
        val pillLeft = compose.onNodeWithText("Home").getUnclippedBoundsInRoot().left.value - 4f
        // The banner's eyebrow and the row's header both say it, at the same left edge.
        compose.onAllNodesWithText("Continue Watching", useUnmergedTree = true).fetchSemanticsNodes().forEach {
            assertEquals(it.boundsInRoot.left / compose.density.density, pillLeft, 0.5f)
        }
    }

    /**
     * A report from the TV: as focus moved along the tabs, a label turned dark before the light fill arrived,
     * and the tab of the page still on screen stayed white. Labels now have two states only. In
     * every frame of a move, each label is light grey or dark and nothing between, grey while
     * what is behind it is the dark pill and dark once it is the light fill, and at most one tab
     * is white. While Home's page waits to change, Home's label is grey.
     */
    @Test
    fun tabLabelsAreGreyOnThePillOrDarkOnTheFillInEveryFrame() {
        up(2)
        assertFocusOn("Home")
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_000)
        compose.sendTvKey(KeyEvent.KEYCODE_DPAD_RIGHT, compose.activityFocusManager)
        val grey = luminance(LauncherColors.onSurfaceVariant.toArgb())
        val dark = luminance(LauncherColors.surface.toArgb())
        repeat(24) { frame ->
            compose.mainClock.advanceTimeByFrame()
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val white = listOf("Home", "Movies", "Apps").count { label ->
                val tab = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
                var bright = 0
                for (x in tab.left.toInt() until tab.right.toInt()) {
                    for (y in tab.top.toInt() until tab.bottom.toInt()) if (luminance(image.getPixel(x, y)) > 0.5) bright++
                }
                bright > tab.width * tab.height * 0.5f
            }
            assertTrue("frame ${frame + 1}: $white white tabs", white <= 1)
            listOf("Home", "Movies", "Apps").forEach { label ->
                val box = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                // Behind the label: the item's own padding, left of the text.
                val behind = luminance(image.getPixel(box.left.toInt() - 8, box.center.y.toInt()))
                // The label's colour: its pixel furthest from what is behind it.
                var text = behind
                for (x in box.left.toInt() until box.right.toInt()) {
                    for (y in box.top.toInt() until box.bottom.toInt()) {
                        val l = luminance(image.getPixel(x, y))
                        if (Math.abs(l - behind) > Math.abs(text - behind)) text = l
                    }
                }
                val at = "$label, frame ${frame + 1}: text $text on $behind"
                val isGrey = Math.abs(text - grey) < 0.03
                assertTrue("$at is neither grey nor dark", isGrey || Math.abs(text - dark) < 0.01)
                // The two have the same contrast on a background of luminance 0.10.
                if (behind < 0.07) assertTrue("$at: dark on the pill", isGrey)
                if (behind > 0.13) assertTrue("$at: grey on the fill", !isGrey)
                val contrast = (maxOf(text, behind) + 0.05) / (minOf(text, behind) + 0.05)
                assertTrue("$at: contrast $contrast", contrast >= 2.6)
                // The light fill has moved on; Home's page shows until the settle time ends.
                if (label == "Home" && frame + 1 in 10 until (TAB_SETTLE_MILLIS / 16).toInt()) assertTrue("$at: not grey", isGrey)
            }
        }
        compose.mainClock.autoAdvance = true
    }

    /** WCAG relative luminance of an ARGB pixel. */
    private fun luminance(pixel: Int): Double {
        fun channel(c: Int) = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * channel((pixel shr 16) and 0xFF) + 0.7152 * channel((pixel shr 8) and 0xFF) + 0.0722 * channel(pixel and 0xFF)
    }

    /** Review C8-1: a refresh with no film rows takes Movies away. Focus on Apps stays on Apps, with its page. */
    @Test
    fun aTabThatGoesLeavesFocusOnTheTabThatHadIt() {
        up(2)
        right(2)
        assertFocusOn("Apps")
        settle()
        compose.onNodeWithText("Movies").assertExists()
        val app = compose.activity.application as FakeTraktApp
        @Suppress("UNCHECKED_CAST")
        val state = HomeRepository::class.java.getDeclaredField("mutable").apply { isAccessible = true }.get(app.home) as MutableStateFlow<HomeState>
        compose.runOnUiThread {
            val rows = state.value.trakt!!
            state.value = state.value.copy(trakt = rows.copy(continueWatching = emptyList(), picks = emptyList(), watchlistRow = emptyList()))
        }
        settle()
        compose.onNodeWithText("Movies").assertDoesNotExist()
        assertFocusOn("Apps")
        compose.onNodeWithText("On Home").assertExists()
    }

    @Test
    fun upFromTheBannerReachesTheBarAndDownComesBack() {
        up()
        assertFocusOn("Resume")
        up()
        assertFocusOn("Home")
        // Down goes back to where focus was on the page.
        down()
        assertFocusOn("Resume")
    }

    @Test
    fun leftAndRightWalkTheTabsAndButtonsAndStopAtTheEnds() {
        up(2)
        left()
        assertFocusOn("Home")
        right(2)
        assertFocusOn("Apps")
        // Focus on Apps shows All Apps below. Focus stays in the bar.
        settle()
        compose.onNodeWithText("On Home").assertExists()
        right()
        assertFocusOn("Start Screensaver")
        right()
        assertFocusOn("Settings")
        right()
        assertFocusOn("Settings")
        left(2)
        assertFocusOn("Apps")
        left(2)
        assertFocusOn("Home")
        settle()
        compose.onNodeWithText("On Home").assertDoesNotExist()
        compose.onNodeWithContentDescription("Harbour Lights").assertExists()
    }

    @Test
    fun downFromAppsEntersAllAppsAndBackReturnsToTheBarThenHome() {
        up(2)
        right(2)
        down()
        assertFocusOn("YouTube")
        compose.onNodeWithText("On Home").assertExists()
        up()
        assertFocusOn("Apps")
        down()
        right()
        assertFocusOn("Stremio")
        back()
        assertFocusOn("Apps")
        back()
        assertFocusOn("Home")
        compose.onNodeWithText("On Home").assertDoesNotExist()
        back()
        assertFocusOn("YouTube")
    }

    /** Review 1cf623b: Back while All Apps fades out after Home was chosen belongs to Home, not to the page fading out. */
    @Test
    fun backDuringTheFadeFromAppsGoesToHome() {
        up(2)
        right(2)
        settle()
        compose.onNodeWithText("On Home").assertExists()
        compose.mainClock.autoAdvance = false
        left(2)
        assertFocusOn("Home")
        compose.mainClock.advanceTimeBy(TAB_SETTLE_MILLIS + 50)
        // Mid-fade: All Apps is still composed under Home.
        compose.mainClock.advanceTimeBy(48)
        back()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // Back on the Home tab goes to YouTube, not back to the Apps tab.
        assertFocusOn("YouTube")
        compose.onNodeWithText("On Home").assertDoesNotExist()
    }

    @Test
    fun backOnTheHomeTabGoesToYouTube() {
        up(2)
        back()
        assertFocusOn("YouTube")
    }

    /** The narrow All tile opens the Apps tab with focus on its first tile. Home keeps the All tile. */
    @Test
    fun theAllTileOpensTheAppsTab() {
        right(4)
        assertFocusOn("All Apps")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertFocusOn("YouTube")
        compose.onNodeWithText("On Home").assertExists()
        back()
        back()
        assertFocusOn("Home")
        down()
        assertFocusOn("All Apps")
        back()
        assertFocusOn("YouTube")
    }

    @Test
    fun theScreensaverButtonOpensTheScreensaverAndAnyKeyClosesIt() {
        up(2)
        right(3)
        assertFocusOn("Start Screensaver")
        // The screensaver's animations never end, so the clock moves by hand while it shows.
        compose.mainClock.autoAdvance = false
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onAllNodesWithContentDescription("Harbour Lights").assertCountEquals(0)
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Harbour Lights").assertExists()
        assertFocusOn("Start Screensaver")
    }

    /** OK held to close the screensaver: its repeats and release reach the Screensaver button, and must not open it again. */
    @Test
    fun okHeldToCloseTheScreensaverDoesNotOpenItAgain() {
        up(2)
        right(3)
        compose.mainClock.autoAdvance = false
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onAllNodesWithContentDescription("Harbour Lights").assertCountEquals(0)
        compose.holdTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.mainClock.advanceTimeBy(1_000)
        compose.repeatTvKey(KeyEvent.KEYCODE_DPAD_CENTER, times = 5)
        compose.mainClock.advanceTimeBy(100)
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Harbour Lights").assertExists()
        assertFocusOn("Start Screensaver")
    }

    @Test
    fun theSettingsButtonOpensSettingsAndBackReturnsToIt() {
        up(2)
        right(4)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertFocusOn("Selecting a Title")
        back()
        compose.onAllNodesWithText("Selecting a Title").assertCountEquals(0)
        assertFocusOn("Settings")
    }

    /** The bar shows Assistant only when the TV answers ACTION_ASSIST, and OK sends it. */
    @Test
    fun assistantShowsWhenTheTvAnswersIt() {
        compose.onNodeWithContentDescription("Assistant").assertDoesNotExist()
        val assist = ComponentName("com.google.android.katniss", "com.google.android.katniss.Assist")
        shadowOf(compose.activity.packageManager).apply {
            addActivityIfNotPresent(assist)
            addIntentFilterForActivity(assist, IntentFilter(Intent.ACTION_ASSIST).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitForIdle()
        up(2)
        right(3)
        assertFocusOn("Assistant")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(Intent.ACTION_ASSIST, shadowOf(compose.activity).nextStartedActivity.action)
    }

    /** The bar scrolls away with the banner when focus goes down into the rows. */
    @Test
    fun theBarScrollsAwayWithTheBanner() {
        up(2)
        val atTop = compose.onNodeWithText("Home").getBoundsInRoot().top
        assertEquals(TOP_BAR_TOP.toFloat(), atTop.value, 6f)
        down(3)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertTrue(compose.onNodeWithText("Home").getBoundsInRoot().bottom.value < 0f)
    }

    /** FakeTraktApp has films only, so the bar has Movies and no Shows. */
    @Test
    fun moviesListsTheFilmsAndShowsHasNoTab() {
        up(2)
        compose.onAllNodesWithText("Shows").assertCountEquals(0)
        right()
        assertFocusOn("Movies")
        // Focus stays in the bar. The page below has no apps row.
        settle()
        compose.onAllNodesWithText("YouTube").assertCountEquals(0)
        compose.onNodeWithText("Top Picks for You").assertExists()
        right()
        assertFocusOn("Apps")
    }

    /** Movies' first row takes the apps row's place: Down enters it, and Up from it opens the banner. */
    @Test
    fun downFromMoviesEntersItsFirstRowAndUpOpensItsBanner() {
        up(2)
        right()
        down()
        assertFocusOn("Harbour Lights")
        down()
        assertFocusOn("Hollowby")
        up()
        assertFocusOn("Harbour Lights")
        up()
        assertFocusOn("Resume")
        up()
        assertFocusOn("Movies")
    }

    @Test
    fun backOnMoviesGoesToTheBarThenTheHomeTab() {
        up(2)
        right()
        down()
        right()
        assertFocusOn("Tidewater")
        back()
        assertFocusOn("Movies")
        back()
        assertFocusOn("Home")
        compose.onNodeWithText("YouTube").assertExists()
    }

    /** Each tab keeps its own last focus while another tab shows. */
    @Test
    fun moviesKeepsItsPlaceWhileHomeShows() {
        up(2)
        right()
        down()
        right()
        back()
        left()
        down()
        assertFocusOn("Resume")
        up()
        right()
        down()
        assertFocusOn("Tidewater")
    }

    /**
     * A report from the TV: Tidewater in the banner of Home and of Movies sat at different heights, with its art
     * at a different size, so the crossfade between the tabs showed a jump. Harbour Lights leads
     * both banners here. While Movies fades in over Home, and after, the art, eyebrow, title and
     * meta of the two pages have the same bounds.
     */
    @Test
    fun theSameTitleSitsStillThroughATabChange() {
        // Up through Home's banner, which opens, to the bar.
        up(2)
        assertFocusOn("Home")
        right()
        settle()
        repeat(2) {
            listOf(BANNER_ART_TAG, BANNER_EYEBROW_TAG, BANNER_TITLE_TAG, BANNER_META_TAG).forEach { tag ->
                // The page's own art, not its blurred copies behind the bar.
                val inBlur = SemanticsMatcher("in the bar's blur") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(BAR_BLUR_TAG) == true }
                val parts = compose.onAllNodes(hasTestTag(tag) and !hasAnyAncestor(inBlur), useUnmergedTree = true)
                val nodes = parts.fetchSemanticsNodes()
                // One on Home and one on Movies.
                assertEquals(tag, 2, nodes.size)
                assertEquals(tag, nodes[0].boundsInRoot, nodes[1].boundsInRoot)
                assertEquals(tag, parts[0].getUnclippedBoundsInRoot(), parts[1].getUnclippedBoundsInRoot())
            }
            assertEquals(2, compose.onAllNodesWithText("Harbour Lights", useUnmergedTree = true).filter(hasTestTag(BANNER_TITLE_TAG)).fetchSemanticsNodes().size)
            // Well after the fade, and after a whole slide's time with focus in the bar.
            compose.mainClock.advanceTimeBy(13_000)
            compose.waitForIdle()
        }
    }

    /**
     * A report from the TV: the blur showed outside the bar's backgrounds, right of the Settings button. The
     * bar has two pills, the tabs' and the buttons'. Each has its own blurred layer with exactly
     * its bounds, at rest and in every frame while the bar scrolls away with the banner.
     */
    @Test
    fun eachPillHasItsOwnBlurWithExactlyItsBounds() {
        fun check(at: String) = listOf(TABS_PILL_TAG, BUTTONS_PILL_TAG).forEachIndexed { index, pill ->
            val shape = compose.onNodeWithTag(pill, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val blurs = compose.onAllNodesWithTag("$BAR_BLUR_TAG-$index", useUnmergedTree = true)
            assertEquals("$at: blurs under $pill", 1, blurs.fetchSemanticsNodes().size)
            val blur = blurs[0].getUnclippedBoundsInRoot()
            listOf(shape.left to blur.left, shape.top to blur.top, shape.right to blur.right, shape.bottom to blur.bottom).forEach { (want, got) ->
                assertEquals("$at: $pill $shape, blur $blur", want.value, got.value, 0.01f)
            }
        }
        check("at rest")
        up(2)
        check("focus in the bar")
        down(2)
        compose.mainClock.autoAdvance = false
        compose.sendTvKey(KeyEvent.KEYCODE_DPAD_DOWN, compose.activityFocusManager)
        repeat(30) { frame ->
            compose.mainClock.advanceTimeByFrame()
            check("scrolling, frame ${frame + 1}")
        }
        compose.mainClock.autoAdvance = true
    }

    /**
     * A report from the TV: the clock sat in one place on Home and Movies but moved on Apps. There is one clock,
     * in the bar: the same bounds on every tab, centred on the pills' centre line. The fake Trakt
     * has films only, so there is no Shows tab here.
     */
    @Test
    fun theClockSitsInOnePlaceOnEveryTabOnTheBarsCentreLine() {
        up(2)
        val places = listOf("Home", "Movies", "Apps").mapIndexed { index, tab ->
            if (index > 0) {
                right()
                settle()
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitForIdle()
            }
            assertFocusOn(tab)
            val clocks = compose.onAllNodesWithTag(CLOCK_TAG, useUnmergedTree = true)
            assertEquals("clocks on $tab", 1, clocks.fetchSemanticsNodes().size)
            clocks[0].getUnclippedBoundsInRoot()
        }
        assertEquals(places.toString(), 1, places.distinct().size)
        val pill = compose.onNodeWithTag(TABS_PILL_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((pill.top + pill.bottom).value / 2, (places[0].top + places[0].bottom).value / 2, 0.5f)
    }

    /** Focus only passing a tab does not build its page, so the bar moves quickly. */
    @Test
    fun aTabShowsItsPageOnlyWhenFocusRestsOnIt() {
        up(2)
        compose.mainClock.autoAdvance = false
        right()
        assertFocusOn("Movies")
        compose.onNodeWithText("YouTube").assertExists()
        right()
        assertFocusOn("Apps")
        right()
        assertFocusOn("Start Screensaver")
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // Home still shows. Movies and All Apps were never built.
        compose.onNodeWithText("YouTube").assertExists()
        compose.onAllNodesWithText("On Home").assertCountEquals(0)

        compose.mainClock.autoAdvance = false
        left()
        assertFocusOn("Apps")
        compose.mainClock.advanceTimeBy(TAB_SETTLE_MILLIS - 50)
        compose.onAllNodesWithText("On Home").assertCountEquals(0)
        compose.mainClock.advanceTimeBy(100)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("On Home").assertExists()
    }

    /** Down straight after moving onto a tab shows its page and goes into it. */
    @Test
    fun downRightAfterMovingOntoATabEntersItsPage() {
        up(2)
        compose.mainClock.autoAdvance = false
        right()
        down()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertFocusOn("Harbour Lights")
        compose.onAllNodesWithText("YouTube").assertCountEquals(0)
    }

    /** Review O7: Back after Down on a tab whose page was still being built leaves focus on the Home tab. */
    @Test
    fun backCancelsAPendingEntry() {
        up(2)
        compose.mainClock.autoAdvance = false
        right()
        down()
        compose.mainClock.advanceTimeByFrame()
        back()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertFocusOn("Home")
        compose.onNodeWithText("YouTube").assertExists()
    }

    @Test
    fun backCancelsAPendingEntryFromOk() {
        up(2)
        compose.mainClock.autoAdvance = false
        right()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.mainClock.advanceTimeByFrame()
        back()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertFocusOn("Home")
        compose.onNodeWithText("YouTube").assertExists()
    }

    /** Review O7: Right after Down on Movies leaves focus on Apps, which shows its page. */
    @Test
    fun rightCancelsAPendingEntry() {
        up(2)
        compose.mainClock.autoAdvance = false
        right()
        down()
        compose.mainClock.advanceTimeByFrame()
        right()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        settle()
        assertFocusOn("Apps")
        compose.onNodeWithText("On Home").assertExists()
    }

    /** Review O8: going back to Apps shows its page with focus still in the bar. Down then returns to the same tile. */
    @Test
    fun returningToAppsDoesNotEnterItsPage() {
        up(2)
        right(2)
        down()
        assertFocusOn("YouTube")
        right()
        assertFocusOn("Stremio")
        back()
        assertFocusOn("Apps")
        left(2)
        settle()
        assertFocusOn("Home")
        right(2)
        settle()
        assertFocusOn("Apps")
        compose.onNodeWithText("On Home").assertExists()
        down()
        assertFocusOn("Stremio")
    }
}
