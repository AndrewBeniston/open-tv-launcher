package app.opentvlauncher.ui.details

import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import app.opentvlauncher.FakeTraktApp
import app.opentvlauncher.MainActivity
import app.opentvlauncher.ui.sendTvKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** OK on a poster opens the details page; Back returns to the same poster (design, outcome A). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = FakeTraktApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailsRouteTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Ember").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.waitForIdle()
    }

    @After
    fun tearDown() = (compose.activity.application as FakeTraktApp).server.close()

    @Test
    fun backFromTheDetailsPageReturnsToTheSamePoster() {
        compose.onNodeWithContentDescription("Ember").requestFocus()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Ember").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Remove from Watchlist").assertExists()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Remove from Watchlist").assertDoesNotExist()
        compose.onNode(isFocused()).assert(hasContentDescription("Ember"))
    }

    /**
     * 24 Oct 2026: Back went to the first card on screen. River Hours is the second poster
     * in From Your Watchlist.
     */
    @Test
    fun backFromTheDetailsPageReturnsToASecondPoster() {
        // A request from outside the row would go to the card it remembers, so move there as a TV does.
        compose.onNodeWithContentDescription("Ember").requestFocus()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(hasContentDescription("River Hours"))
        compose.onNode(isFocused()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Remove from Watchlist").assertExists()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasContentDescription("River Hours"))
    }

    /** A D-pad key as the TV sends it. Compose moves focus when no key handler uses the key. */
    private fun press(keyCode: Int) {
        compose.sendTvKey(keyCode)
        compose.waitForIdle()
    }

    /** The watchlist button changes its label after each OK. Each fresh OK on it works: remove, undo, then remove for good after 10 s. */
    @Test
    fun removeAndUndoOnTheDetailsPageEachTakeOneOk() {
        val app = compose.activity.application as FakeTraktApp
        compose.onNodeWithContentDescription("Ember").requestFocus()
        compose.onNodeWithContentDescription("Ember").performClick()
        compose.waitForIdle()
        app.writes.clear()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(hasText("Remove from Watchlist"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNode(isFocused()).assert(hasText("Add Back to Watchlist"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNode(isFocused()).assert(hasText("Remove from Watchlist"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10_500))
        compose.waitUntil(5_000) { app.writes.isNotEmpty() }
        assertEquals(listOf("POST /sync/watchlist/remove"), app.writes.toList())
    }

    /** Home under the details page took focus from Up on Play. */
    @Test
    fun theDpadCannotReachHomeUnderTheDetailsPage() {
        compose.onNodeWithContentDescription("Ember").requestFocus()
        compose.onNodeWithContentDescription("Ember").performClick()
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Stremio"))

        listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN).forEach { key ->
            press(key)
            compose.onNode(isFocused()).assert(hasText("Stremio"))
        }
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(hasText("Remove from Watchlist"))
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNode(isFocused()).assert(hasText("Remove from Watchlist"))

        // Home's cards are out of the semantics tree too, so TalkBack cannot reach them.
        assertEquals(0, compose.onAllNodesWithContentDescription("Ember").fetchSemanticsNodes().size)
    }

    @Test
    fun aRecommendedTitleOffersToAddItToTheWatchlist() {
        compose.onNodeWithContentDescription("Hollowby").requestFocus()
        compose.onNodeWithContentDescription("Hollowby").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Add to Watchlist").assertExists()
        compose.onNodeWithText("Stremio").assertExists()
    }
}
