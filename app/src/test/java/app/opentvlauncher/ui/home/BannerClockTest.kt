package app.opentvlauncher.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The banner changes slide every 12 s of wall-clock time, whatever the TV's animation scale.
 * Both TVs run at 0.5, and 0 switches animations off.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BannerClockTest(private val scale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "animation scale {0}")
        fun scales() = listOf(arrayOf<Any>(0f), arrayOf<Any>(0.5f), arrayOf<Any>(1f))
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(
        effectContext = object : MotionDurationScale {
            override val scaleFactor = scale
        },
    )

    private val slides = SampleHome.content.slides.map { it.title }

    private fun shownSlides() = slides.filter { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    @Test
    fun eachSlideStaysForTwelveSeconds() {
        compose.mainClock.autoAdvance = false
        compose.setContent { LauncherTheme { HomeScreen(SampleHome.content, homeRequests = 0, onOpenApp = {}) } }
        advance(500)
        assertEquals(listOf(slides[0]), shownSlides())

        advance(11_000)
        assertEquals(listOf(slides[0]), shownSlides())

        // At 12 s the second slide arrives. Give the crossfade time to finish.
        advance(1_500)
        assertEquals(listOf(slides[1]), shownSlides())
    }

    /** Details over Home, opened from a content row: the hidden banner keeps its slide. */
    @Test
    fun theBannerWaitsWhileAPageCoversHome() {
        var active by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { LauncherTheme { HomeScreen(SampleHome.content, homeRequests = 0, onOpenApp = {}, active = active) } }
        advance(500)
        compose.runOnUiThread {
            active = false
            Snapshot.sendApplyNotifications()
        }
        advance(30_000)
        compose.runOnUiThread {
            active = true
            Snapshot.sendApplyNotifications()
        }
        advance(500)
        assertEquals(listOf(slides[0]), shownSlides())

        // Back on Home, the slide gets its full 12 s.
        advance(11_000)
        assertEquals(listOf(slides[0]), shownSlides())
        advance(1_500)
        assertEquals(listOf(slides[1]), shownSlides())
    }
}
