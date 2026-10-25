package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import app.opentvlauncher.data.SampleHome
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
 * Google TV's fixed focus position (24 Oct 2026): Right keeps the focused item where the
 * first item was, 58 dp from the screen edge, and the row slides under it. At the end of a row,
 * focus moves along the last visible items.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RowPivotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager

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
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun press(keyCode: Int) {
        compose.sendTvKey(keyCode, focusManager)
        settle()
    }

    private fun down() = press(KeyEvent.KEYCODE_DPAD_DOWN)
    private fun right() = press(KeyEvent.KEYCODE_DPAD_RIGHT)

    /** The focused item's left and right edges in dp, without its focus scale: the scale is about the centre. */
    private fun focusedEdges(): Pair<Float, Float> {
        val node = compose.onNode(isFocused()).fetchSemanticsNode()
        val coordinates = node.layoutInfo.coordinates
        val box = coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
        val d = compose.density.density
        val width = node.layoutInfo.width / d
        val centre = box.center.x / d
        return (centre - width / 2) to (centre + width / 2)
    }

    // The sample rows: five wide cards can slide 216 dp, one card's step. Eight posters can slide 288 dp, two steps.

    @Test
    fun rightKeepsTheFocusedCardWhereTheFirstCardWas() {
        down()
        assertEquals(58f, focusedEdges().first, 0.5f)
        right()
        assertEquals(58f, focusedEdges().first, 0.5f)
    }

    @Test
    fun postersKeepTheSamePlaceToo() {
        repeat(3) { down() }
        assertEquals(58f, focusedEdges().first, 0.5f)
        right()
        assertEquals(58f, focusedEdges().first, 0.5f)
        right()
        assertEquals(58f, focusedEdges().first, 0.5f)
    }

    @Test
    fun atTheEndOfARowFocusMovesAlongTheLastCards() {
        down()
        right()
        right()
        // The row cannot slide further, so focus moves one card to the right.
        assertEquals(58f + 196f + 20f, focusedEdges().first, 0.5f)
        right()
        right()
        assertEquals(960f - 58f, focusedEdges().second, 0.5f)
    }
}
