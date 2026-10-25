package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.data.WideRow
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

/** Option B: in a short Continue Watching row, a 40 dp gap comes before the watchlist titles. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinueFillTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager

    /** Two paused cards, then three cards that stand in for watchlist titles. */
    private val content = SampleHome.content.let { home ->
        val continueWatching = home.rows[0] as WideRow
        val filled = continueWatching.copy(
            cards = continueWatching.cards.take(2) + continueWatching.cards.drop(2).map { it.copy(progress = null) },
            fillStart = 2,
        )
        home.copy(rows = listOf(filled) + home.rows.drop(1))
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

    private fun press(keyCode: Int) {
        compose.sendTvKey(keyCode, focusManager)
        settle()
    }

    private fun bounds(name: String) = compose.onNodeWithContentDescription(name).getBoundsInRoot()

    @Test
    fun theWatchlistTitlesStartAfterAWiderGap() {
        val paused = bounds("Harbour Lights")
        val firstFill = bounds("Kitchen Council")
        val secondFill = bounds("The Glacier: Rescue from Skarvika")
        assertEquals(HomeLayout.fillGap.value, (firstFill.left - paused.right).value, 0.5f)
        assertEquals(HomeLayout.gutter.value, (secondFill.left - firstFill.right).value, 0.5f)
    }

    /** Right moves across the gap one card at a time, as along any row. */
    @Test
    fun focusCrossesTheGapInOneStep() {
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(hasContentDescription("Harbour Lights"))
        // The focused card stays at the row's start, as Google TV's fixed focus position.
        assertEquals(HomeLayout.sideMargin.value, bounds("Harbour Lights").left.value, 0.5f)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(hasContentDescription("Kitchen Council"))
    }
}
