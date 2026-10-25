package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.ui.sendTvKey
import app.opentvlauncher.ui.theme.LauncherTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 24 Oct 2026: the banner fades between its closed and open states. Its text does not slide. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BannerFadeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager
    private val title = SampleHome.content.slides[0].title

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            focusManager = LocalFocusManager.current
            LauncherTheme { HomeScreen(SampleHome.content, homeRequests = 0, onOpenApp = {}) }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun titleTop() = compose.onNodeWithText(title).getBoundsInRoot().top.value

    /** The title's top on each frame for 600 ms after [keyCode]. */
    private fun topsAfter(keyCode: Int): List<Float> {
        compose.sendTvKey(keyCode, focusManager)
        return List(36) {
            compose.mainClock.advanceTimeByFrame()
            titleTop()
        }
    }

    @Test
    fun theTitleJumpsWhileHiddenInsteadOfSliding() {
        val closed = titleTop()
        val opening = topsAfter(KeyEvent.KEYCODE_DPAD_UP)
        val open = closed - 18f
        assertEquals(open, opening.last(), 0.5f)
        assertTrue("tops while opening: $opening", opening.all { kotlin.math.abs(it - closed) < 0.5f || kotlin.math.abs(it - open) < 0.5f })

        val closing = topsAfter(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(closed, closing.last(), 0.5f)
        assertTrue("tops while closing: $closing", closing.all { kotlin.math.abs(it - closed) < 0.5f || kotlin.math.abs(it - open) < 0.5f })
    }

    /** A frame clock for [BannerTextFade] alone: each frame is [frameNanos] later, and [onFrame] runs after each. */
    private class Frames(private val frameNanos: Long, private val onFrame: () -> Unit) : MonotonicFrameClock {
        private var time = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            time += frameNanos
            return onFrame(time).also { this.onFrame() }
        }
    }

    /** Review O10: on every frame where the text takes its new place, it cannot be seen. At 60 Hz and at the Chromecast's 25 Hz. */
    @Test
    fun theTextMovesOnlyWhenItCannotBeSeen() {
        listOf(16_666_667L, 40_000_000L).forEach { frameNanos ->
            val fade = BannerTextFade(open = false)
            val frames = mutableListOf<Pair<Float, Boolean>>()
            runBlocking(Frames(frameNanos) { frames += fade.alpha.value to fade.shownOpen }) {
                fade.follow(open = true)
                fade.follow(open = false)
            }
            val moves = frames.zipWithNext().filter { (before, after) -> before.second != after.second }
            assertEquals("two moves at $frameNanos ns a frame: $frames", 2, moves.size)
            moves.forEach { (before, after) ->
                assertEquals("hidden before the move: $frames", 0f, before.first, 0f)
                assertEquals("hidden after the move: $frames", 0f, after.first, 0f)
            }
            assertEquals(1f, frames.last().first, 0f)
        }
    }

    /** Focus that comes back before the text has moved fades it straight back in, at the same place. */
    @Test
    fun aChangeTakenBackBeforeTheMoveLeavesTheTextInPlace() {
        val fade = BannerTextFade(open = false)
        runBlocking(Frames(16_666_667L) {}) {
            fade.follow(open = false)
        }
        assertEquals(false, fade.shownOpen)
        assertEquals(1f, fade.alpha.value, 0f)
    }
}
