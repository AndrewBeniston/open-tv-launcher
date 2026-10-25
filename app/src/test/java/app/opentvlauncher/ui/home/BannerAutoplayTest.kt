package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.ui.sendTvKey
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The banner's autoplay (25 Oct 2026). 12 s a slide below the banner and on its buttons,
 * but on the banner a slide changes no sooner than 5 s after the last key. On the dots the clock
 * waits, and starts again 5 s after the last move. Down and Up never leave it stopped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BannerAutoplayTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager
    private val slides = SampleHome.content.slides.map { it.title }

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            focusManager = LocalFocusManager.current
            LauncherTheme { HomeScreen(SampleHome.content, homeRequests = 0, onOpenApp = {}) }
        }
        compose.waitForIdle()
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    /** A key, then a short wait for focus to move: 500 ms in all. */
    private fun press(keyCode: Int) {
        compose.sendTvKey(keyCode, focusManager)
        advance(500)
    }

    private fun assertFocusOn(name: String) = compose.onNode(isFocused()).assert(
        SemanticsMatcher("named $name") { hasText(name).matches(it) || hasContentDescription(name).matches(it) },
    )

    /** The slide the banner shows, once its crossfade has finished. */
    private fun assertShows(slide: Int) = assertEquals(
        listOf(slides[slide]),
        compose.onAllNodesWithTag(BANNER_TITLE_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString() },
    )

    @Test
    fun belowTheBannerEachSlideStaysTwelveSeconds() {
        advance(11_000)
        assertShows(0)
        advance(2_500)
        assertShows(1)
    }

    /** Up from the apps row is a key on the banner too; 12 s is long after it. */
    @Test
    fun onTheButtonsAutoplayRuns() {
        press(KeyEvent.KEYCODE_DPAD_UP)
        assertFocusOn("Resume")
        advance(10_500)
        assertShows(0)
        advance(2_500)
        assertShows(1)
    }

    @Test
    fun onTheButtonsASlideWaitsFiveSecondsAfterAKey() {
        press(KeyEvent.KEYCODE_DPAD_UP)
        advance(9_500)
        // 10.5 s into the slide.
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertFocusOn("Details")
        // At 12 s the slide's time is up, but the key was 1.5 s ago.
        advance(3_500)
        assertShows(0)
        // 5 s after the key.
        advance(2_500)
        assertShows(1)
    }

    @Test
    fun onTheDotsTheClockWaitsAndStartsAgainFiveSecondsAfterTheLastMove() {
        press(KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        // The next dot shows its slide, and its clock waits.
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(hasTestTag("$BANNER_DOT_TAG-1"))
        assertShows(1)
        // 5 s of waiting and 12 s of the slide: 17 s after the move.
        advance(16_000)
        assertShows(1)
        advance(2_500)
        assertShows(2)
    }

    /**
     * 25 Oct 2026: only one dot looks active. When the clock moves on while a dot has
     * focus, focus goes to the new active dot, which alone is wide, and its fill runs. The focused
     * dot is always fully bright, and the time that has run darkens it from the left.
     */
    @Test
    fun onTheDotsFocusMovesWithTheSlideAndItsDotFills() {
        press(KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(hasTestTag("$BANNER_DOT_TAG-1"))
        // The new slide's time has not started: the focused dot is fully bright.
        assertEquals(0f, darkened(1), 0.02f)
        advance(16_000)
        assertShows(1)
        advance(2_500)
        assertShows(2)
        compose.onNode(isFocused()).assert(hasTestTag("$BANNER_DOT_TAG-2"))
        assertEquals(listOf(2), slides.indices.filter { dotWidth(it) > 8.5f })
        assertEquals(36f, dotWidth(2), 0.5f)
        // The fill runs on the focused dot with no key to wait for.
        val early = darkened(2)
        advance(4_000)
        val late = darkened(2)
        assertTrue("the fill went from $early to $late", late > early + 0.15f && late < 1f)
        compose.onNode(isFocused()).assert(hasTestTag("$BANNER_DOT_TAG-2"))
    }

    private fun dotWidth(index: Int) = compose.onNodeWithTag("$BANNER_DOT_TAG-$index").getUnclippedBoundsInRoot().let { (it.right - it.left).value }

    /**
     * How much of focused dot [index] the run time has darkened, along its middle line. The rest
     * of the dot is fully bright (green over 200); the darkened part is the bright dot under the
     * 40 % surface colour (green about 146).
     */
    private fun darkened(index: Int): Float {
        val dot = compose.onNodeWithTag("$BANNER_DOT_TAG-$index").getUnclippedBoundsInRoot()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val d = compose.density.density
        val y = ((dot.top.value + dot.bottom.value) / 2 * d).toInt()
        val xs = (dot.left.value * d).toInt() + 2 until (dot.right.value * d).toInt() - 2
        val green = xs.map { (image.getPixel(it, y) shr 8) and 0xFF }
        assertTrue("the dot has a pixel that is neither bright nor darkened: $green", green.all { it > 200 || it in 120..170 })
        return green.count { it <= 200 }.toFloat() / green.size
    }

    /** A report from the TV: after the dots, Down and then Up to the first button left autoplay stopped. */
    @Test
    fun downThenUpAfterTheDotsLeavesAutoplayRunning() {
        press(KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertShows(1)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_UP)
        assertFocusOn("Resume")
        // The slide's clock waited 0.5 s on the dots, then ran from Down: it ends 11 s from here.
        advance(10_000)
        assertShows(1)
        advance(2_500)
        assertShows(2)
    }
}
