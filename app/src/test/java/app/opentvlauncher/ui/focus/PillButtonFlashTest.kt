package app.opentvlauncher.ui.focus

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A report from the TV: moving left and right between the banner's and the details page's buttons, the button
 * losing or gaining focus flashed very dark. The focus shadow, on a slower spring than the fill,
 * showed through the see-through fill. Now one value draws both, and the shadow comes only once
 * the fill is nearly opaque.
 *
 * Robolectric draws no shadows, so the frame test checks the fill on screen, and the second test
 * checks fill and shadow together, with the darkest possible shadow under the fill.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PillButtonFlashTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val requesters = List(2) { FocusRequester() }

    /** The fill's brightness inside [index]'s button, left of its label. */
    private fun fill(index: Int): Double {
        val box = compose.onNodeWithTag("button-$index").fetchSemanticsNode().boundsInRoot
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val pixel = image.getPixel(box.left.toInt() + 12, box.center.y.toInt())
        return (0.2126 * ((pixel shr 16) and 0xFF) + 0.7152 * ((pixel shr 8) and 0xFF) + 0.0722 * (pixel and 0xFF)) / 255
    }

    private fun focus(index: Int) = compose.runOnUiThread { requesters[index].requestFocus() }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    /** At every point of the fill's animation, over bright and dark art, with a black shadow at full strength. */
    @Test
    fun theShadowNeverDarkensASeeThroughFill() {
        fun brightness(lift: Float, art: Double): Double {
            val fill = buttonFill(lift)
            val ink = 0.2126 * fill.red + 0.7152 * fill.green + 0.0722 * fill.blue
            // Under the fill: the art, darkened by the shadow's share.
            val under = art * (1 - buttonShadow(lift))
            return ink * fill.alpha + under * (1 - fill.alpha)
        }
        listOf(0.65, 0.05).forEach { art ->
            val ends = minOf(brightness(0f, art), brightness(1f, art))
            (0..100).forEach { step ->
                val lift = step / 100f
                assertTrue("art $art, lift $lift: ${brightness(lift, art)} below $ends", brightness(lift, art) >= ends - 1e-6)
            }
        }
    }

    @Test
    fun noFrameOfAMoveDarkensEitherButtonsFill() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LauncherTheme {
                // Bright art, where a dark frame shows most.
                Box(Modifier.fillMaxSize().background(Color(0xFFE2A05C))) {
                    Row(Modifier.padding(48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton("Resume", {}, Modifier.focusRequester(requesters[0]).testTag("button-0"), showPlayIcon = true)
                        PillButton("Details", {}, Modifier.focusRequester(requesters[1]).testTag("button-1"))
                    }
                }
            }
        }
        focus(1)
        settle()
        val rest0 = fill(0)
        val focused1 = fill(1)
        focus(0)
        settle()
        val focused0 = fill(0)
        val rest1 = fill(1)
        listOf(1 to "right", 0 to "left").forEach { (to, way) ->
            focus(to)
            repeat(30) { frame ->
                compose.mainClock.advanceTimeByFrame()
                listOf(Triple(0, rest0, focused0), Triple(1, rest1, focused1)).forEach { (index, rest, focused) ->
                    val now = fill(index)
                    assertTrue("$way, frame ${frame + 1}: button $index at $now, rest $rest, focused $focused", now >= minOf(rest, focused) - 0.01)
                }
            }
        }
    }
}
