package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.opentvlauncher.data.AppTile
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.ui.sendTvKey
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The D-pad focus map from the design (section 3), checked on the JVM with Robolectric.
 * The screen is a 1080p TV: 960 x 540 dp at 2x. Native graphics draws the sample art for real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager
    private var homeRequests by mutableIntStateOf(0)
    private val opened = mutableListOf<AppTile>()

    @Before
    fun setUp() {
        // A TV has no touchscreen, so its window is never in touch mode. Robolectric starts in it.
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        // The banner timer and the clock never stop, so the test moves time itself.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            focusManager = LocalFocusManager.current
            LauncherTheme {
                HomeScreen(SampleHome.content, homeRequests, onOpenApp = { opened += it })
            }
        }
        settle()
    }

    private fun settle(millis: Long = 32) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun press(keyCode: Int) {
        compose.sendTvKey(keyCode, focusManager)
        settle()
    }

    private fun up() = press(KeyEvent.KEYCODE_DPAD_UP)
    private fun down() = press(KeyEvent.KEYCODE_DPAD_DOWN)
    private fun left() = press(KeyEvent.KEYCODE_DPAD_LEFT)
    private fun right() = press(KeyEvent.KEYCODE_DPAD_RIGHT)

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun focused() = compose.onNode(isFocused())

    private fun assertFocusOn(name: String) {
        focused().assert(SemanticsMatcher("named $name") { node ->
            hasText(name).matches(node) || hasContentDescription(name).matches(node)
        })
    }

    @Test
    fun homeOpensWithFocusOnYouTube() {
        assertFocusOn("YouTube")
    }

    @Test
    fun rightWalksTheAppsRowAndStopsAtAllApps() {
        right()
        assertFocusOn("Stremio")
        right()
        assertFocusOn("Netflix")
        right()
        assertFocusOn("BBC iPlayer")
        right()
        assertFocusOn("All Apps")
        right()
        assertFocusOn("All Apps")
    }

    @Test
    fun leftStopsAtYouTube() {
        left()
        assertFocusOn("YouTube")
    }

    @Test
    fun downMovesOneRowAtATime() {
        down()
        assertFocusOn("Tales of the Quiet Coast")
        down()
        assertFocusOn("Shot Review 12")
        down()
        assertFocusOn("Ember")
        down()
        assertFocusOn("River Hours")
        down()
        assertFocusOn("River Hours")
    }

    @Test
    fun aRowStopsAtItsLastCard() {
        down()
        repeat(8) { right() }
        assertFocusOn("Fern")
    }

    @Test
    fun eachRowRemembersItsLastFocusedItem() {
        right()
        right()
        down()
        assertFocusOn("Tales of the Quiet Coast")
        right()
        right()
        assertFocusOn("Kitchen Council")
        down()
        assertFocusOn("Shot Review 12")
        right()
        up()
        assertFocusOn("Kitchen Council")
        down()
        assertFocusOn("Blue Screen Compositing")
        down()
        right()
        right()
        assertFocusOn("Hollowby")
        up()
        assertFocusOn("Blue Screen Compositing")
        down()
        assertFocusOn("Hollowby")
        up()
        up()
        up()
        assertFocusOn("Netflix")
    }

    @Test
    fun upFromTheAppsRowOpensTheBannerOnItsFirstButton() {
        up()
        assertFocusOn("Resume")
        compose.onNodeWithText(SampleHome.content.slides.first().synopsis).assertExists()
        right()
        assertFocusOn("Details")
        down()
        assertFocusOn("YouTube")
    }

    @Test
    fun backReturnsToYouTube() {
        down()
        down()
        right()
        back()
        assertFocusOn("YouTube")
        up()
        back()
        assertFocusOn("YouTube")
    }

    @Test
    fun backReturnsToYouTubeWhenTheAppsRowRemembersAnotherTile() {
        repeat(3) { right() }
        down()
        back()
        assertFocusOn("YouTube")
    }

    @Test
    fun backOnYouTubeDoesNothing() {
        back()
        assertFocusOn("YouTube")
        assertFalse(compose.activity.isFinishing)
    }

    @Test
    fun theHomeButtonReturnsToYouTube() {
        down()
        down()
        homeRequests++
        settle()
        assertFocusOn("YouTube")
    }

    /** 24 Oct 2026: a narrow All tile at the end of the row, inside the right margin. */
    @Test
    fun theNarrowAllTileEndsTheRowAndOpensAllApps() {
        repeat(4) { right() }
        assertFocusOn("All Apps")
        val tile = compose.onNodeWithContentDescription("All Apps").getBoundsInRoot()
        assertEquals(HomeLayout.narrowTile.width.value, (tile.right - tile.left).value, 0.5f)
        assertEquals(960f - HomeLayout.sideMargin.value, tile.right.value, 0.5f)
        compose.onNodeWithText("All").assertExists()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("All Apps"), opened.map { it.label })
    }

    @Test
    fun okOnAnAppTileOpensThatApp() {
        right()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("Stremio"), opened.map { it.label })
    }

    @Test
    fun aFocusedRowScrollsUpNearTheTopOfTheScreen() {
        repeat(3) { down() }
        settle(3_000)
        // Design, "Content rows": the focused header shows at 55 dp. The posters start below the
        // header slot, which keeps the text clearance: 53 + 55 = 108 dp.
        val header = compose.onNodeWithText("Top Picks for You").getBoundsInRoot()
        assertEquals(55f, header.top.value, 1f)
        val poster = focused().getBoundsInRoot()
        assertEquals((HomeLayout.focusedRowTop + HomeLayout.headerSlot(SampleHome.content.rows[2])).value, poster.top.value, 1f)
        // The semantics bounds leave out the focus scale (FocusSurface); HomeClearanceTest measures it.
        assertEquals(124f, (poster.right - poster.left).value, 1f)
    }
}
