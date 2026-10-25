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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.opentvlauncher.data.Art
import app.opentvlauncher.data.PosterCard
import app.opentvlauncher.data.PosterRow
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.data.WideCard
import app.opentvlauncher.data.WideRow
import app.opentvlauncher.ui.art.SampleArt
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
 * 25 Oct 2026: when new cards, such as up next, arrive in Continue Watching ahead of the
 * card it remembers, Down lands on the first card. Otherwise the row remembers its card as
 * before, and Back from the details page always returns to the card that was opened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinueFocusTest {
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

    /** Changes the test state and tells Compose at once, as a refresh does. */
    private fun change(block: () -> Unit) {
        compose.runOnUiThread {
            block()
            Snapshot.sendApplyNotifications()
        }
        settle()
    }

    private fun assertFocusOn(name: String) = compose.onNode(isFocused()).assert(hasContentDescription(name))

    private fun focusedLeft() = compose.onNode(isFocused()).getBoundsInRoot().left.value

    private fun upNext(title: String) = WideCard(title, "S1 E1 · Up next", Art(SampleArt.Fern))

    /** Continue Watching with [change] applied to its cards. */
    private fun continueWatching(change: (List<WideCard>) -> List<WideCard>) = change {
        val row = content.rows[0] as WideRow
        content = content.copy(rows = listOf(row.copy(cards = change(row.cards))) + content.rows.drop(1))
    }

    /** Down to Continue Watching, then Right to Kitchen Council, the third card. */
    private fun onKitchenCouncil() {
        down()
        right(2)
        assertFocusOn("Kitchen Council")
    }

    @Test
    fun newCardsAheadOfTheRememberedCardSendDownToTheFirstCard() {
        onKitchenCouncil()
        up()
        continueWatching { listOf(upNext("The Long Field"), upNext("Max Tally")) + it }
        down()
        assertFocusOn("The Long Field")
        // The row is back at its start: the first card sits at the side margin, as on a fresh row.
        assertEquals(HomeLayout.sideMargin.value, focusedLeft(), 12f)
    }

    /** A new card after the remembered one, or a card that only moved ahead, is not new ahead of it. */
    @Test
    fun withNoNewCardAheadDownReturnsToTheRememberedCard() {
        onKitchenCouncil()
        up()
        continueWatching { cards -> listOf(cards.last()) + cards.dropLast(1) + upNext("Max Tally") }
        down()
        assertFocusOn("Kitchen Council")
    }

    @Test
    fun backFromDetailsReturnsToTheOpenedCardEvenWithNewCardsAhead() {
        onKitchenCouncil()
        change { covered = true }
        compose.onNodeWithTag(PAGE).assertIsFocused()
        continueWatching { listOf(upNext("The Long Field")) + it }
        change {
            covered = false
            returns++
        }
        assertFocusOn("Kitchen Council")
    }

    /**
     * After an install the row has no card to remember, and the cards that arrive with the first
     * refresh go ahead of the first card on screen. Down still lands on the first card, which the
     * row scrolls back to.
     */
    @Test
    fun cardsThatArriveBeforeTheRowHadFocusStartItOnTheFirstCard() {
        continueWatching { listOf(upNext("Kitchen Council S1E5"), upNext("The Long Field"), upNext("Files")) + it }
        down()
        assertFocusOn("Kitchen Council S1E5")
        assertEquals(HomeLayout.sideMargin.value, focusedLeft(), 12f)
    }

    /** Only Continue Watching starts again on its first card. Top Picks keeps its card. */
    @Test
    fun otherRowsKeepTheirRememberedCard() {
        down(3)
        right(2)
        assertFocusOn("Hollowby")
        up()
        change {
            val index = content.rows.indexOfFirst { it.title == "Top Picks for You" }
            val picks = content.rows[index] as PosterRow
            val more = listOf(PosterCard("New Pick", "2026 · Drama", Art(SampleArt.Fern))) + picks.posters
            content = content.copy(rows = content.rows.toMutableList().also { it[index] = picks.copy(posters = more) })
        }
        down()
        assertFocusOn("Hollowby")
    }

    private companion object {
        const val PAGE = "page"
    }
}
