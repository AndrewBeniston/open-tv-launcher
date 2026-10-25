package app.opentvlauncher.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The banner art's drift from 100 % to 102 % over a slide. It is drawn, so the art never moves
 * as far as Compose can tell: a moving layer made Compose check its accessibility tree 5 times a
 * second while the Home override's accessibility service is on, about 0.7 MB of garbage a second.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BannerDriftTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var barFocused by mutableStateOf(false)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent { LauncherTheme { HomeScreen(SampleHome.content, homeRequests = 0, onOpenApp = {}, barFocused = barFocused) } }
        compose.waitForIdle()
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun art() = compose.onAllNodesWithTag(BANNER_ART_TAG, useUnmergedTree = true)[0]

    @Test
    fun theArtDoesNotMoveAsTheSlideRuns() {
        show()
        advance(500)
        val early = art().getUnclippedBoundsInRoot()
        advance(10_000)
        assertEquals(early, art().getUnclippedBoundsInRoot())
    }

    @Test
    fun theArtStillDriftsAsItIsDrawn() {
        show()
        advance(300)
        val early = art().captureToImage().asAndroidBitmap()
        advance(11_000)
        val late = art().captureToImage().asAndroidBitmap()
        var change = 0L
        for (x in 0 until early.width step 4) for (y in 0 until early.height step 4) {
            val a = early.getPixel(x, y)
            val b = late.getPixel(x, y)
            change += abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        }
        assertTrue("the art did not drift: $change", change > early.width * early.height / 16)
    }

    /** With focus in the bar every tab's slide stops at full drift, so the same title has the same size on every tab. */
    @Test
    fun withFocusInTheBarTheSlideStopsAtFullDrift() {
        show()
        advance(3_000)
        // 3 s into the slide the active dot is filled at its left end only.
        val (left, right) = dotEnds()
        assertTrue("filled to the end at 3 s: $left, $right", left - right > 30)
        compose.runOnUiThread {
            barFocused = true
            Snapshot.sendApplyNotifications()
        }
        advance(500)
        val (fullLeft, fullRight) = dotEnds()
        assertTrue("not full with focus in the bar: $fullLeft, $fullRight", abs(fullLeft - fullRight) < 8)
    }

    /** The green channel 3 px inside each end of the active dot. */
    private fun dotEnds(): Pair<Int, Int> {
        val dot = compose.onNodeWithTag("$BANNER_DOT_TAG-0").getUnclippedBoundsInRoot()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val d = compose.density.density
        val y = ((dot.top.value + dot.bottom.value) / 2 * d).toInt()
        fun at(x: Int) = (image.getPixel(x, y) shr 8) and 0xFF
        return at((dot.left.value * d).toInt() + 3) to at((dot.right.value * d).toInt() - 3)
    }
}
