package app.opentvlauncher.ui.details

import android.view.KeyEvent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.lifecycle.Lifecycle
import app.opentvlauncher.FakeTraktApp
import app.opentvlauncher.MainActivity
import app.opentvlauncher.home.HomeRepository
import app.opentvlauncher.home.HomeState
import app.opentvlauncher.home.NetflixState
import app.opentvlauncher.home.TitleKey
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.netflix.NetflixRules
import app.opentvlauncher.ui.activityFocusManager
import app.opentvlauncher.ui.sendTvKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
 * Review R7-3, in the real activity: when the Netflix answer stops counting after 7 days, its
 * buttons go with no new HomeState, as when the TV is offline. The time is the real clock, so
 * each test waits a few seconds for the moment to pass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = FakeTraktApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NetflixExpiryRouteTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = compose.activity.application as FakeTraktApp
    private lateinit var state: MutableStateFlow<HomeState>
    private lateinit var seeded: HomeState

    /** When the seeded answer stops counting: a few seconds from the start of each test. */
    private var expires = 0L

    @Before
    fun setUp() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Ember").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.autoAdvance = false
        val signIn = runBlocking { app.session.signedIn()!!.id }
        @Suppress("UNCHECKED_CAST")
        state = HomeRepository::class.java.getDeclaredField("mutable").apply { isAccessible = true }.get(app.home) as MutableStateFlow<HomeState>
        expires = System.currentTimeMillis() + 4_000
        val answered = expires - NetflixRules.KEEP_MILLIS
        compose.runOnUiThread {
            // Harbour Lights alone, on Netflix: the banner's Netflix button and the apps row's tile are the only two.
            val rows = state.value.trakt!!
            seeded = state.value.copy(
                trakt = rows.copy(continueWatching = rows.continueWatching.take(1), picks = emptyList(), watchlistRow = emptyList()),
                netflix = NetflixState(signIn, answered, answered, setOf(TitleKey(TitleKind.Movie, 1)), mapOf("tt1" to "81252357")),
            )
            state.value = seeded
        }
        settle()
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.onAllNodesWithText("YouTube")[0].requestFocus()
        settle()
    }

    @After
    fun tearDown() = app.server.close()

    private fun settle(millis: Long = 500) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun press(keyCode: Int) {
        compose.sendTvKey(keyCode, compose.activityFocusManager)
        settle()
    }

    private fun waitForExpiry() {
        Thread.sleep((expires - System.currentTimeMillis() + 100).coerceAtLeast(0))
        assertTrue(System.currentTimeMillis() > expires)
    }

    private fun focusNetflix() {
        press(KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(hasText("Netflix"))
        compose.onAllNodesWithText("Netflix").assertCountEquals(2)
    }

    private fun assertOnlyTheAppTile() {
        compose.onAllNodesWithText("Netflix").assertCountEquals(1)
        assertSame("no new HomeState", seeded, state.value)
    }

    private fun netflixNodes() = compose.onAllNodesWithText("Netflix").fetchSemanticsNodes().size

    private val aBannerButton = SemanticsMatcher("Resume or Details") { hasText("Resume").matches(it) || hasText("Details").matches(it) }

    @Test
    fun theBannerButtonGoesAtTheMomentTheAnswerStopsCounting() {
        focusNetflix()
        waitForExpiry()
        // The launcher's timer for the moment, which runs on the test's clock.
        settle(6_000)
        assertOnlyTheAppTile()
        compose.onNode(isFocused()).assert(aBannerButton)
    }

    @Test
    fun aReturnToTheFrontChecksTheAnswerAgain() {
        focusNetflix()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        settle(100)
        waitForExpiry()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        settle(100)
        // The banner is still open, without its Netflix button.
        compose.onNodeWithText("Details").assertExists()
        assertOnlyTheAppTile()
    }

    @Test
    fun theDetailsPageLosesItsNetflixButtonToo() {
        press(KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(hasText("Details"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Remove from Watchlist").assertExists()
        val before = netflixNodes()
        waitForExpiry()
        settle(6_000)
        assertEquals("the details page's Netflix button goes", before - 1, netflixNodes())
        assertSame("no new HomeState", seeded, state.value)
        compose.onNode(isFocused()).assertExists()
    }

    /** Review R8-1: focus on the details page's Netflix button when it goes. The first button left takes focus, and OK on it works. */
    @Test
    fun theFocusedNetflixButtonOnTheDetailsPageHandsFocusToTheFirstButton() {
        press(KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Remove from Watchlist").assertExists()
        compose.onNode(isFocused()).assert(hasText("Resume"))
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(hasText("Netflix"))
        waitForExpiry()
        settle(6_000)
        assertSame("no new HomeState", seeded, state.value)
        compose.onNode(isFocused()).assert(hasText("Resume"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Opening in Stremio").assertExists()
        assertTrue(shadowOf(compose.activity).nextStartedActivity.dataString!!.startsWith("stremio:"))
    }

    /** A Netflix button drawn just before the moment: OK checks the answer again and opens nothing. */
    @Test
    fun okOnAnExpiredButtonOpensNothing() {
        focusNetflix()
        waitForExpiry()
        // Before the launcher's timer: the button is still drawn.
        compose.onAllNodesWithText("Netflix").assertCountEquals(2)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Opening in Netflix").assertDoesNotExist()
        assertNull(shadowOf(compose.activity).nextStartedActivity)
        assertOnlyTheAppTile()
        compose.onNode(isFocused()).assert(aBannerButton)
    }
}
