package app.opentvlauncher.ui.menu

import android.view.KeyEvent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import app.opentvlauncher.FakeTraktApp
import app.opentvlauncher.MainActivity
import app.opentvlauncher.ui.downTvKey
import app.opentvlauncher.ui.holdTvKey
import app.opentvlauncher.ui.releaseTvKey
import app.opentvlauncher.ui.repeatTvKey
import app.opentvlauncher.ui.sendTvKey
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Continue Watching menu in the real activity, against a fake Trakt: OK held on a card opens
 * it on Resume, each choice sends its change, destructive ones ask for a second OK, and Back
 * closes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = FakeTraktApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleMenuRouteTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = compose.activity.application as FakeTraktApp

    @Before
    fun setUp() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Harbour Lights").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.waitForIdle()
        // Harbour Lights is the first card of Continue Watching, so a request from outside the row lands on it.
        compose.onNodeWithContentDescription("Harbour Lights").requestFocus()
        compose.waitForIdle()
        app.writes.clear()
        compose.holdTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
    }

    @After
    fun tearDown() = app.server.close()

    private fun press(keyCode: Int) {
        compose.sendTvKey(keyCode)
        compose.waitForIdle()
    }

    private fun waitForMenuToClose() = compose.waitUntil(5_000) {
        compose.onAllNodesWithText("Mark as Unwatched").fetchSemanticsNodes().isEmpty()
    }

    @Test
    fun okHeldOpensTheMenuOnResumeWithItsFourChoices() {
        listOf("Resume", "Mark as Watched", "Mark as Unwatched", "Reset Progress", "Remove from Watchlist").forEach {
            compose.onNodeWithContentDescription(it).assertExists()
        }
        // Resume changes nothing on Trakt, so it has focus.
        compose.onNode(isFocused()).assert(hasContentDescription("Resume"))
        // The release of the long press chose nothing.
        assertEquals(emptyList<String>(), app.writes.toList())
    }

    /** A report from the TV: OK held on the card, then released inside the menu. Its repeats and release choose nothing. */
    @Test
    fun aLongPressReleasedInsideTheMenuChoosesNothing() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Harbour Lights").requestFocus()
        compose.waitForIdle()
        compose.holdTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasContentDescription("Resume"))
        // The remote goes on repeating OK until the user lets go, after the menu has opened.
        compose.repeatTvKey(KeyEvent.KEYCODE_DPAD_CENTER, times = 10)
        compose.waitForIdle()
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasContentDescription("Resume"))
        // Resume was not chosen either: the menu still shows.
        compose.onNodeWithText("Mark as Unwatched").assertExists()
        assertEquals(emptyList<String>(), app.writes.toList())
        // A new press still chooses.
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitForMenuToClose()
        assertEquals(listOf("DELETE /sync/playback/70", "POST /sync/history"), app.writes.toList())
    }

    /** A press that began before a row had focus never chooses it, even Mark as Watched, which sends at once. */
    @Test
    fun aPressThatBeganBeforeTheRowHadFocusChoosesNothing() {
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNode(isFocused()).assert(hasContentDescription("Mark as Watched"))
        compose.repeatTvKey(KeyEvent.KEYCODE_DPAD_CENTER, times = 3)
        compose.waitForIdle()
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        Thread.sleep(300)
        compose.onNodeWithText("Mark as Unwatched").assertExists()
        assertEquals(emptyList<String>(), app.writes.toList())
    }

    /**
     * A report from the TV, the second title: Mark as Watched closed the menu, focus went to the next
     * card, and the same held OK opened that card's menu. A press that began elsewhere opens nothing.
     */
    @Test
    fun aPressThatBeganOnAnotherItemDoesNotOpenACardsMenu() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        // The next card, which has not had a long press yet.
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(hasContentDescription("Tidewater"))
        compose.repeatTvKey(KeyEvent.KEYCODE_DPAD_CENTER, times = 5)
        compose.waitForIdle()
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        compose.onNodeWithText("Mark as Unwatched").assertDoesNotExist()
        compose.onNode(isFocused()).assert(hasContentDescription("Tidewater"))
        assertEquals(emptyList<String>(), app.writes.toList())
    }

    /** Review c8a9021, K1: a cancelled OK on Mark as Watched sends nothing. */
    @Test
    fun aCanceledOkSendsNothing() {
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNode(isFocused()).assert(hasContentDescription("Mark as Watched"))
        compose.downTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER, canceled = true)
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
        assertEquals(emptyList<String>(), app.writes.toList())
        compose.onNodeWithText("Mark as Unwatched").assertExists()
    }

    @Test
    fun resumeClosesTheMenuAndChangesNothing() {
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitForMenuToClose()
        assertEquals(emptyList<String>(), app.writes.toList())
    }

    @Test
    fun markAsWatchedClosesTheMenuAndTheCardLeaves() {
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitForMenuToClose()
        assertEquals(listOf("DELETE /sync/playback/70", "POST /sync/history"), app.writes.toList())
        compose.onNodeWithContentDescription("Harbour Lights").assertDoesNotExist()
        // Focus goes to the card now in its place.
        compose.onNode(isFocused()).assert(hasContentDescription("Tidewater"))
    }

    @Test
    fun resetProgressAsksForASecondOk() {
        repeat(3) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.onNode(isFocused()).assert(hasContentDescription("Reset Progress"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Press OK again to confirm.").assertExists()
        assertEquals(emptyList<String>(), app.writes.toList())
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitForMenuToClose()
        assertEquals(listOf("DELETE /sync/playback/70"), app.writes.toList())
        compose.onNodeWithContentDescription("Harbour Lights").assertDoesNotExist()
    }

    /** Each change that asks for a second OK sends its request after a fresh OK confirms it. */
    @Test
    fun markAsUnwatchedSendsAfterTheSecondOk() {
        repeat(2) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.onNode(isFocused()).assert(hasContentDescription("Mark as Unwatched"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Press OK again to confirm.").assertExists()
        assertEquals(emptyList<String>(), app.writes.toList())
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitForMenuToClose()
        assertEquals(listOf("POST /sync/history/remove"), app.writes.toList())
    }

    @Test
    fun removeFromWatchlistSendsAfterTheSecondOk() {
        repeat(4) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.onNode(isFocused()).assert(hasContentDescription("Remove from Watchlist"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Press OK again to confirm.").assertExists()
        assertEquals(emptyList<String>(), app.writes.toList())
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitForMenuToClose()
        assertEquals(listOf("POST /sync/watchlist/remove"), app.writes.toList())
    }

    /** A report from the TV (The Glacier): Trakt had no plays to remove. The menu stays and says so. */
    @Test
    fun markAsUnwatchedWithNoPlaysSaysSo() {
        app.noPlaysToRemove = true
        repeat(2) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Trakt has no plays of this title to remove.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("POST /sync/history/remove"), app.writes.toList())
        compose.onNode(isFocused()).assert(hasContentDescription("Mark as Unwatched"))
    }

    @Test
    fun aRefusedChangeOffersARetry() {
        app.refuseWrites = true
        repeat(2) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Trakt did not confirm. Press OK to retry.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("POST /sync/history/remove"), app.writes.toList())
        // The retry needs one OK.
        app.refuseWrites = false
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitForMenuToClose()
        assertEquals(listOf("POST /sync/history/remove", "POST /sync/history/remove"), app.writes.toList())
        compose.onNode(isFocused()).assert(hasContentDescription("Harbour Lights"))
    }

    @Test
    fun backClosesTheMenuAndFocusReturnsToTheCard() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Mark as Watched").assertDoesNotExist()
        compose.onNode(isFocused()).assert(hasContentDescription("Harbour Lights"))
        assertEquals(emptyList<String>(), app.writes.toList())
    }

    /** Review O4: closing and reopening the menu while Mark as Watched is sending shows it as sending and sends nothing more. */
    @Test
    fun aReopenedMenuDoesNotSendTheSameChangeAgain() {
        val trakt = app.server.dispatcher
        val deletes = AtomicInteger()
        val release = CountDownLatch(1)
        app.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "DELETE") {
                    deletes.incrementAndGet()
                    release.await(10, TimeUnit.SECONDS)
                }
                return trakt.dispatch(request)
            }
        }
        try {
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(5_000) { deletes.get() == 1 }
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Harbour Lights").requestFocus()
            compose.holdTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitForIdle()
            compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitForIdle()
            compose.onNodeWithText("Sending to Trakt.").assertExists()
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            Thread.sleep(300)
            assertEquals(1, deletes.get())
        } finally {
            release.countDown()
        }
        waitForMenuToClose()
        assertEquals(listOf("DELETE /sync/playback/70", "POST /sync/history"), app.writes.toList())
    }

    /** Review N2: Android recreates the activity while Mark as Watched is sending. The reopened menu still sends nothing more. */
    @Test
    fun aRecreatedActivityDoesNotSendTheSameChangeAgain() {
        val trakt = app.server.dispatcher
        val deletes = AtomicInteger()
        val release = CountDownLatch(1)
        app.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "DELETE") {
                    deletes.incrementAndGet()
                    release.await(10, TimeUnit.SECONDS)
                }
                return trakt.dispatch(request)
            }
        }
        try {
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(5_000) { deletes.get() == 1 }
            compose.activityRule.scenario.recreate()
            compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Harbour Lights").requestFocus()
            compose.holdTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitForIdle()
            compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitForIdle()
            compose.onNodeWithText("Sending to Trakt.").assertExists()
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            Thread.sleep(300)
            assertEquals(1, deletes.get())
        } finally {
            release.countDown()
        }
        compose.waitUntil(5_000) { app.writes.count { it == "POST /sync/history" } >= 1 }
        compose.waitForIdle()
        assertEquals(listOf("DELETE /sync/playback/70", "POST /sync/history"), app.writes.toList())
    }
}
