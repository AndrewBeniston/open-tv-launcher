package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.opentvlauncher.data.CONTINUE_WATCHING_ROW
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.data.SlideKind
import app.opentvlauncher.ui.sendTvKey
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 24 Oct 2026: after Back from the details page, focus went to the first card on
 * screen, not the card he left. A page over Home, then its close, must leave focus on the same
 * item at the same place, in every row, the apps row and the banner. The page over Home here
 * stands in for the details page and the Stremio hand-off, as MainActivity draws them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeReturnFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager
    private var covered by mutableStateOf(false)
    private var returns by mutableIntStateOf(0)
    private var content by mutableStateOf(SampleHome.content)

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            focusManager = LocalFocusManager.current
            LauncherTheme {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        content,
                        homeRequests = 0,
                        onOpenApp = {},
                        focusReturns = returns,
                        active = !covered,
                        modifier = Modifier.graphicsLayer { alpha = if (covered) 0f else 1f },
                    )
                    if (covered) {
                        val page = remember { FocusRequester() }
                        Box(Modifier.fillMaxSize().testTag(PAGE).focusRequester(page).focusable())
                        LaunchedEffect(Unit) { page.requestFocus() }
                    }
                }
            }
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        compose.sendTvKey(keyCode, focusManager)
        settle()
    }

    private fun up() = press(KeyEvent.KEYCODE_DPAD_UP)
    private fun down(times: Int = 1) = press(KeyEvent.KEYCODE_DPAD_DOWN, times)
    private fun right(times: Int = 1) = press(KeyEvent.KEYCODE_DPAD_RIGHT, times)
    private fun left(times: Int = 1) = press(KeyEvent.KEYCODE_DPAD_LEFT, times)

    /** The focused item's name and its left edge in dp, without its focus scale. */
    private fun focused(): Pair<String, Float> {
        val node = compose.onNode(isFocused()).fetchSemanticsNode()
        val name = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty()
        val coordinates = node.layoutInfo.coordinates
        val box = coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
        val d = compose.density.density
        return name to (box.center.x / d - node.layoutInfo.width / d / 2)
    }

    /**
     * Changes the test state and tells Compose at once. Without the notice, a change waited for
     * other work, such as the banner timer or the clock's minute tick: with the banner focused
     * nothing else runs. The banner tests then never covered Home, and they failed when unrelated
     * work applied the cover but not the close.
     */
    private fun change(block: () -> Unit) {
        compose.runOnUiThread {
            block()
            Snapshot.sendApplyNotifications()
        }
        settle()
    }

    /** Opens the page over Home and checks that it really has focus. */
    private fun cover() {
        change { covered = true }
        compose.onNodeWithTag(PAGE).assertIsFocused()
    }

    /** Closes the page, as Back from the details page does. */
    private fun uncover() = change {
        covered = false
        returns++
    }

    /** Opens a page over Home, closes it, and checks that focus is where it was. */
    private fun assertFocusComesBack() {
        val before = focused()
        cover()
        uncover()
        val after = focused()
        assertEquals(before.first, after.first)
        assertEquals(before.second, after.second, 0.5f)
    }

    @Test
    fun aPosterNearTheEndOfTopPicks() {
        down(2)
        right(7)
        left(2)
        assertFocusComesBack()
    }

    @Test
    fun aPosterInTheMiddleOfTopPicks() {
        down(2)
        right(3)
        assertFocusComesBack()
    }

    @Test
    fun aCardNearTheEndOfContinueWatching() {
        down()
        right(4)
        left()
        assertFocusComesBack()
    }

    @Test
    fun aPosterNearTheEndOfTheWatchlistRow() {
        down(3)
        right(7)
        left(2)
        assertFocusComesBack()
    }

    @Test
    fun aPosterInTheMiddleOfTheWatchlistRow() {
        down(3)
        right(3)
        assertFocusComesBack()
    }

    @Test
    fun aCardNearTheEndOfTheYouTubeRow() {
        down(4)
        right(4)
        left()
        assertFocusComesBack()
    }

    @Test
    fun anAppTile() {
        right(2)
        assertFocusComesBack()
    }

    @Test
    fun theBannersPlayButton() {
        up()
        assertFocusComesBack()
    }

    @Test
    fun theBannersDetailsButton() {
        up()
        right()
        assertFocusComesBack()
    }

    /** After Stremio, a refresh brings new progress. The banner keeps the slide it showed. */
    @Test
    fun theBannerKeepsItsSlideWhenARefreshChangesTheSlides() {
        up()
        right(3) // Details, the dot of this slide, then the next slide.
        val shown = content.slides[1].title
        cover()
        change { content = content.copy(slides = content.slides.map { it.copy(meta = it.meta + " · updated") }) }
        uncover()
        assertEquals("Details", focused().first)
        compose.onNodeWithText(shown).assertExists()
    }

    /**
     * Details opened from the banner's only title, the title removed from the watchlist, then
     * Back. The banner has gone, so focus goes to the apps row, on the tile it remembers.
     */
    @Test
    fun whenTheBannerHasGoneUnderThePageFocusGoesToTheAppsRow() {
        right(2)
        up()
        right()
        assertEquals("Details", focused().first)
        cover()
        change { content = content.copy(slides = emptyList(), rows = content.rows.takeLast(1)) }
        uncover()
        assertEquals("Netflix", focused().first)
    }

    /** The same while focus is on the banner itself: a refresh that removes every slide. */
    @Test
    fun whenTheBannerGoesWhileFocusedFocusGoesToTheAppsRow() {
        up()
        assertEquals("Resume", focused().first)
        change { content = content.copy(slides = emptyList()) }
        assertEquals("YouTube", focused().first)
        down()
        assertEquals("Tales of the Quiet Coast", focused().first)
    }

    private companion object {
        const val PAGE = "page over home"
    }

    /** Every Continue Watching card cleared from its menu: the row goes, and focus comes back to the next row. */
    @Test
    fun whenContinueWatchingEmptiesUnderThePageFocusGoesToTheNextRow() {
        down()
        assertEquals("Tales of the Quiet Coast", focused().first)
        cover()
        change {
            content = content.copy(
                rows = content.rows.filterNot { it.key == CONTINUE_WATCHING_ROW },
                slides = content.slides.filterNot { it.kind == SlideKind.ContinueWatching },
            )
        }
        uncover()
        assertEquals("Shot Review 12", focused().first)
    }
}
