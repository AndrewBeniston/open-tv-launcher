package app.opentvlauncher.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 24 Oct 2026: one evenly spaced row of dots, and the active dot widens smoothly. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BannerDotsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val slides = SampleHome.content.slides

    @Before
    fun setUp() {
        compose.mainClock.autoAdvance = false
        compose.setContent { LauncherTheme { HomeScreen(SampleHome.content, homeRequests = 0, onOpenApp = {}) } }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    private fun dot(index: Int) = compose.onNodeWithTag("$BANNER_DOT_TAG-$index").getBoundsInRoot()

    private fun width(index: Int) = dot(index).let { (it.right - it.left).value }

    /** The sample has Continue Watching and Top Pick slides. The dots no longer leave a gap between the two. */
    @Test
    fun theDotsAreEvenlySpaced() {
        val gaps = (1 until slides.size).map { (dot(it).left - dot(it - 1).right).value }
        gaps.forEach { assertEquals(6f, it, 0.5f) }
    }

    @Test
    fun theActiveDotWidensAndTheOthersReturnToRestSize() {
        assertEquals(28f, width(0), 0.5f)
        (1 until slides.size).forEach { assertEquals(8f, width(it), 0.5f) }

        // The timer started with Home, 500 ms before the test.
        compose.mainClock.advanceTimeBy(11_300)
        assertEquals(8f, width(1), 0.5f)
        // Frame by frame through the slide change: the widths move, they do not jump.
        var frames = 0
        while (width(1) <= 8.5f && frames < 100) {
            compose.mainClock.advanceTimeByFrame()
            frames++
        }
        assertTrue("dot 1 is ${width(1)} dp", width(1) < 27f)
        assertTrue("dot 0 is ${width(0)} dp", width(0) > 9f)

        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(8f, width(0), 0.5f)
        assertEquals(28f, width(1), 0.5f)
    }
}
