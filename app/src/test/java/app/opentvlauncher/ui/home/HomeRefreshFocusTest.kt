package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.opentvlauncher.data.CONTINUE_WATCHING_ROW
import app.opentvlauncher.data.HomeContent
import app.opentvlauncher.data.PosterRow
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.data.SlideKind
import app.opentvlauncher.data.WideRow
import app.opentvlauncher.ui.sendTvKey
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A refresh replaces the rows while one of them has focus. Focus stays on the same title, or
 * goes to the item now at its place; it is never lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeRefreshFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager
    private var content by mutableStateOf(SampleHome.content)

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            focusManager = LocalFocusManager.current
            LauncherTheme { HomeScreen(content, homeRequests = 0, onOpenApp = {}) }
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(keyCode: Int) {
        compose.sendTvKey(keyCode, focusManager)
        settle()
    }

    private fun down() = press(KeyEvent.KEYCODE_DPAD_DOWN)
    private fun right() = press(KeyEvent.KEYCODE_DPAD_RIGHT)

    /** A refresh: the new content reaches Compose at once, not when other work next runs. */
    private fun replace(change: (HomeContent) -> HomeContent) {
        compose.runOnUiThread {
            content = change(content)
            Snapshot.sendApplyNotifications()
        }
        settle()
    }

    private fun assertFocusOn(name: String) = compose.onNode(isFocused()).assert(hasContentDescription(name))

    /** The left edge of the card named [name]. */
    private fun left(name: String) = compose.onNodeWithContentDescription(name).getBoundsInRoot().left

    @Test
    fun aReorderedRowKeepsFocusOnTheSameTitle() {
        down()
        assertFocusOn("Tales of the Quiet Coast")
        assertTrue(left("Harbour Lights") > left("Tales of the Quiet Coast"))
        replace { home ->
            val continueWatching = home.rows[0] as WideRow
            home.copy(rows = listOf(continueWatching.copy(cards = continueWatching.cards.reversed())) + home.rows.drop(1))
        }
        // The reversed row reached the screen: Harbour Lights is now before Tales of the Quiet Coast.
        assertTrue(left("Harbour Lights") < left("Tales of the Quiet Coast"))
        assertFocusOn("Tales of the Quiet Coast")
    }

    @Test
    fun whenTheFocusedCardGoesTheCardNowAtItsPlaceTakesFocus() {
        repeat(3) { down() }
        right()
        right()
        assertFocusOn("Hollowby")
        replace { home ->
            val picks = home.rows[2] as PosterRow
            home.copy(rows = home.rows.toMutableList().also { it[2] = picks.copy(posters = picks.posters.filterNot { p -> p.title == "Hollowby" }) })
        }
        assertFocusOn("Redcap")
    }

    @Test
    fun whenTheFocusedRowGoesTheRowNowAtItsPlaceTakesFocus() {
        repeat(4) { down() }
        assertFocusOn("River Hours")
        replace { home -> home.copy(rows = home.rows.dropLast(1)) }
        assertFocusOn("Ember")
    }

    @Test
    fun whenTheYouTubeRowGoesTheTopPicksRowTakesItsPlace() {
        repeat(2) { down() }
        right()
        assertFocusOn("Blue Screen Compositing")
        replace { home -> home.copy(rows = home.rows.filterNot { it.title == "Recommended on YouTube" }) }
        assertFocusOn("Ember")
    }

    /** A refresh that adds Recommended on YouTube above Top Picks keeps focus on the same card. */
    @Test
    fun aRowAddedAboveKeepsFocusOnTheSameCard() {
        val youtube = content.rows[1]
        replace { home -> home.copy(rows = home.rows - youtube) }
        repeat(2) { down() }
        right()
        assertFocusOn("Go and Fetch")
        replace { home -> home.copy(rows = home.rows.take(1) + youtube + home.rows.drop(1)) }
        assertFocusOn("Go and Fetch")
    }

    @Test
    fun whenEveryRowGoesYouTubeTakesFocus() {
        down()
        replace { home -> home.copy(rows = emptyList()) }
        compose.onNode(isFocused()).assert(androidx.compose.ui.test.hasText("YouTube"))
    }

    /** Continue Watching with nothing left in it, as after clearing every card from its menu. */
    private fun withoutContinueWatching(home: HomeContent) = home.copy(
        rows = home.rows.filterNot { it.key == CONTINUE_WATCHING_ROW },
        slides = home.slides.filterNot { it.kind == SlideKind.ContinueWatching },
    )

    /** 24 Oct 2026: an empty row goes, and focus moves to the row now in its place. */
    @Test
    fun whenContinueWatchingEmptiesFocusGoesToTheNextRow() {
        down()
        right()
        assertFocusOn("Harbour Lights")
        replace(::withoutContinueWatching)
        compose.onNodeWithText("Continue Watching").assertDoesNotExist()
        assertFocusOn("Shot Review 12")
    }

    /** The banner shows no Continue Watching slide once its titles have gone. */
    @Test
    fun theBannerDropsContinueWatchingSlidesThatHaveGone() {
        val paused = content.slides.filter { it.kind == SlideKind.ContinueWatching }.map { it.title }
        compose.onNodeWithText(paused.first()).assertExists()
        replace(::withoutContinueWatching)
        paused.forEach { compose.onAllNodesWithText(it).assertCountEquals(0) }
        compose.onNodeWithText(content.slides.first().title).assertExists()
    }
}
