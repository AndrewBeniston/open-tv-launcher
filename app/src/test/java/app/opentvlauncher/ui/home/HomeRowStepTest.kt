package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.data.WideRow
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
 * Seen on the TV, 24 Oct 2026: with one card in Continue Watching, Down from All Apps went to Top
 * Picks. Up and Down must always enter the next row, at the card it remembers or its first card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeRowStepTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager

    /** The sample Home with one card in Continue Watching, on the left. */
    private val content = SampleHome.content.let { home ->
        val continueWatching = home.rows[0] as WideRow
        home.copy(rows = listOf(continueWatching.copy(cards = continueWatching.cards.take(1))) + home.rows.drop(1))
    }

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
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    /** A key as the TV sends it. If no key handler uses it, the system moves focus that way. */
    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        compose.sendTvKey(keyCode, focusManager)
        settle()
    }

    private fun up() = press(KeyEvent.KEYCODE_DPAD_UP)
    private fun down() = press(KeyEvent.KEYCODE_DPAD_DOWN)
    private fun right(times: Int = 1) = press(KeyEvent.KEYCODE_DPAD_RIGHT, times)

    private fun assertFocusOn(name: String) = compose.onNode(isFocused()).assert(
        SemanticsMatcher("named $name") { hasText(name).matches(it) || hasContentDescription(name).matches(it) },
    )

    @Test
    fun downFromAllAppsEntersAOneCardRow() {
        right(4)
        assertFocusOn("All Apps")
        down()
        assertFocusOn("Tales of the Quiet Coast")
    }

    @Test
    fun downFromEachAppTileEntersTheFirstRow() {
        listOf("YouTube", "Stremio", "Netflix", "BBC iPlayer", "All Apps").forEachIndexed { index, tile ->
            right(index)
            assertFocusOn(tile)
            down()
            assertFocusOn("Tales of the Quiet Coast")
            up()
            assertFocusOn(tile)
            press(KeyEvent.KEYCODE_DPAD_LEFT, index)
        }
    }

    @Test
    fun upFromACardOnTheRightEntersTheOneCardRowThenTheTileItCameFrom() {
        right(2)
        down()
        down()
        right(4)
        assertFocusOn("Tech Weekly 9")
        up()
        assertFocusOn("Tales of the Quiet Coast")
        up()
        assertFocusOn("Netflix")
    }

    @Test
    fun downFromTheOneCardRowGoesBackToTheRememberedCard() {
        down()
        down()
        right(3)
        assertFocusOn("Tech News: Sailing Research and Model 7 Loom")
        up()
        assertFocusOn("Tales of the Quiet Coast")
        down()
        assertFocusOn("Tech News: Sailing Research and Model 7 Loom")
    }

    @Test
    fun downWalksEveryRowInTurnAndStopsOnTheLast() {
        right(4)
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
    fun downFromTheBannerGoesToTheTileItCameFrom() {
        right(4)
        up()
        assertFocusOn("Resume")
        right()
        assertFocusOn("Details")
        down()
        assertFocusOn("All Apps")
    }
}
