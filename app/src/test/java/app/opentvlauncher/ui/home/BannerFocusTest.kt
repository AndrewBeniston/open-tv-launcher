package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.ui.sendTvKey
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 24 and 25 Oct 2026: Right goes along the banner's buttons and onto the dots, and along
 * the dots with a wrap from the last to the first. Left on any dot goes to the first button of the
 * slide that shows. Focus never jumps or sticks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BannerFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager
    private val slides = SampleHome.content.slides

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            focusManager = LocalFocusManager.current
            LauncherTheme { HomeScreen(SampleHome.content, homeRequests = 0, onOpenApp = {}) }
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        compose.sendTvKey(keyCode, focusManager)
        settle()
    }

    private fun up() = press(KeyEvent.KEYCODE_DPAD_UP)
    private fun down() = press(KeyEvent.KEYCODE_DPAD_DOWN)
    private fun left(times: Int = 1) = press(KeyEvent.KEYCODE_DPAD_LEFT, times)
    private fun right(times: Int = 1) = press(KeyEvent.KEYCODE_DPAD_RIGHT, times)

    private fun assertFocusOn(name: String) = compose.onNode(isFocused()).assert(
        SemanticsMatcher("named $name") { hasText(name).matches(it) || hasContentDescription(name).matches(it) },
    )

    private fun assertFocusOnDot(index: Int) = compose.onNode(isFocused()).assert(hasTestTag("$BANNER_DOT_TAG-$index"))

    private fun assertShows(slide: Int) = compose.onNodeWithText(slides[slide].title).assertExists()

    @Test
    fun rightGoesAlongTheButtonsThenTheDots() {
        up()
        assertFocusOn("Resume")
        right()
        assertFocusOn("Details")
        // Onto the dot of the slide that shows: the slide does not change.
        right()
        assertFocusOnDot(0)
        assertShows(0)
        right()
        assertFocusOnDot(1)
        assertShows(1)
        right(slides.size - 2)
        assertFocusOnDot(slides.lastIndex)
        assertShows(slides.lastIndex)
        // Right on the last dot wraps to the first.
        right()
        assertFocusOnDot(0)
        assertShows(0)
    }

    /** Left on a dot never shows the previous slide: it goes to the first button of the slide that shows. */
    @Test
    fun leftFromAnyDotGoesToTheFirstButtonOfTheSlideThatShows() {
        // Slides 0 to 2 start with Resume, slide 3 with Stremio.
        val first = listOf("Resume", "Resume", "Resume", "Stremio")
        first.indices.forEach { dot ->
            toDot(dot)
            left()
            assertFocusOn(first[dot])
            assertShows(dot)
            down()
        }
        // Left on the first button stays there: no slide change, no jump.
        toDot(0)
        left(3)
        assertFocusOn("Resume")
        assertShows(0)
    }

    /** Up to the banner, onto the dot of the slide that shows (autoplay moves it on), then along to [dot]. */
    private fun toDot(dot: Int) {
        up()
        right(2)
        val at = compose.onNode(isFocused()).fetchSemanticsNode().config[SemanticsProperties.TestTag].substringAfterLast('-').toInt()
        right((dot - at + slides.size) % slides.size)
        assertFocusOnDot(dot)
    }

    /** A focused dot keeps its place when the slide's button changes from Resume to Stremio. */
    @Test
    fun aSlideOfAnotherKindKeepsFocusOnTheDot() {
        up()
        right(2 + 3)
        assertFocusOnDot(3)
        assertShows(3)
        // The slide's button and the Stremio app tile.
        compose.onAllNodesWithText("Stremio").assertCountEquals(2)
        compose.onNodeWithText("Resume").assertDoesNotExist()
        right()
        assertFocusOnDot(4)
    }

    @Test
    fun downThenUpStartsAgainOnTheFirstButton() {
        up()
        right(3)
        assertFocusOnDot(1)
        down()
        assertFocusOn("YouTube")
        up()
        assertFocusOn("Resume")
        assertShows(1)
    }

    @Test
    fun theDotsTakeNoFocusWhileTheBannerIsClosed() {
        down()
        up()
        assertFocusOn("YouTube")
        right(4)
        assertFocusOn("All Apps")
        right()
        assertFocusOn("All Apps")
    }
}
