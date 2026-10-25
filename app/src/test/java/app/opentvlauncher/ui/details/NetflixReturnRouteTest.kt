package app.opentvlauncher.ui.details

import android.view.KeyEvent
import androidx.compose.ui.test.assert
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
import app.opentvlauncher.ui.activityFocusManager
import app.opentvlauncher.ui.sendTvKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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

/**
 * Review M2, in the real activity: after the Netflix hand-off, focus comes back to the banner's
 * Netflix button, not to Resume, whether Back closes the hand-off or Netflix came to the front.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = FakeTraktApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NetflixReturnRouteTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = compose.activity.application as FakeTraktApp

    @Before
    fun setUp() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Ember").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.mainClock.autoAdvance = false
        // Harbour Lights, the first Continue Watching slide, is on Netflix for this sign-in.
        val now = System.currentTimeMillis()
        val signIn = runBlocking { app.session.signedIn()!!.id }
        @Suppress("UNCHECKED_CAST")
        val state = HomeRepository::class.java.getDeclaredField("mutable").apply { isAccessible = true }.get(app.home) as MutableStateFlow<HomeState>
        compose.runOnUiThread {
            state.value = state.value.copy(
                netflix = NetflixState(signIn, now, now, setOf(TitleKey(TitleKind.Movie, 1)), mapOf("tt1" to "81252357")),
            )
        }
        settle()
        compose.onAllNodesWithText("YouTube")[0].requestFocus()
        settle()
    }

    @After
    fun tearDown() = app.server.close()

    private fun settle() {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    private fun press(keyCode: Int) {
        compose.sendTvKey(keyCode, compose.activityFocusManager)
        settle()
    }

    private fun openNetflix() {
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNode(isFocused()).assert(hasText("Resume"))
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(hasText("Netflix"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Opening in Netflix").assertExists()
        assertEquals("com.netflix.ninja", shadowOf(compose.activity).nextStartedActivity.`package`)
    }

    @Test
    fun backFromTheNetflixHandOffReturnsToTheNetflixButton() {
        openNetflix()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        compose.onNodeWithText("Opening in Netflix").assertDoesNotExist()
        compose.onNode(isFocused()).assert(hasText("Netflix"))
    }

    @Test
    fun aReturnFromNetflixFocusesTheNetflixButton() {
        openNetflix()
        // Netflix comes to the front, then Back in Netflix returns to the launcher.
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        settle()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        settle()
        compose.onNodeWithText("Opening in Netflix").assertDoesNotExist()
        compose.onNode(isFocused()).assert(hasText("Netflix"))
    }

    /** Merge gate V4: the hand-off holds its app with its title, so Resume after a Netflix hand-off opens Stremio. */
    @Test
    fun resumeAfterTheNetflixHandOffOpensStremio() {
        openNetflix()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNode(isFocused()).assert(hasText("Resume"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Opening in Stremio").assertExists()
        assertEquals("stremio:///detail/movie/tt1", shadowOf(compose.activity).nextStartedActivity.dataString)
    }
}
