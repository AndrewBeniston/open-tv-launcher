package app.opentvlauncher.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/** The crossfade between tab pages: the page left behind is composed only while it fades out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TabFadeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theOldPageFadesOutAndThenLeaves() {
        var tab by mutableStateOf(Tab.Apps)
        var appsComposed = false
        var appsAlpha = -1f
        var homeAlpha = -1f
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val fade = rememberTabFade(tab)
            homeAlpha = fade.alpha(Tab.Home)
            appsAlpha = fade.alpha(Tab.Apps)
            if (tab == Tab.Apps || Tab.Apps in fade.outgoing) {
                DisposableEffect(Unit) {
                    appsComposed = true
                    onDispose { appsComposed = false }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(appsComposed)
        assertEquals(1f, appsAlpha)
        assertEquals(0f, homeAlpha)

        show { tab = Tab.Home }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        assertTrue("Apps stays while it fades out", appsComposed)
        assertTrue("Home is fading in: $homeAlpha", homeAlpha > 0f && homeAlpha < 1f)
        assertEquals(1f, homeAlpha + appsAlpha, 0.001f)

        compose.mainClock.advanceTimeBy(1_000)
        assertFalse("Apps leaves once the fade ends", appsComposed)
        assertEquals(1f, homeAlpha)
        assertEquals(0f, appsAlpha)
    }

    /** Review 1cf623b, L2: back to Home during the fade. Home turns round from where it was, with no jump. */
    @Test
    fun aFadeTurnedRoundStartsFromWhereItWas() {
        var tab by mutableStateOf(Tab.Home)
        lateinit var fade: TabFade
        compose.mainClock.autoAdvance = false
        compose.setContent { fade = rememberTabFade(tab) }
        compose.mainClock.advanceTimeByFrame()
        show { tab = Tab.Apps }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        val before = fade.alpha(Tab.Home)
        assertTrue("Home is fading out: $before", before > 0f && before < 1f)
        show { tab = Tab.Home }
        // Compose moves animations before it recomposes, so the old fade takes one more frame.
        compose.mainClock.advanceTimeByFrame()
        val after = fade.alpha(Tab.Home)
        assertTrue("Home went from $before to $after", abs(after - before) < 0.2f)
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        assertTrue("Home turns round: ${fade.alpha(Tab.Home)} after $after", fade.alpha(Tab.Home) > after)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1f, fade.alpha(Tab.Home))
        assertEquals(emptyList<Tab>(), fade.outgoing)
    }

    /** A third tab during the fade: both pages that were showing fade out from where they were. */
    @Test
    fun aThirdTabFadesBothPagesOut() {
        var tab by mutableStateOf(Tab.Home)
        lateinit var fade: TabFade
        compose.mainClock.autoAdvance = false
        compose.setContent { fade = rememberTabFade(tab) }
        compose.mainClock.advanceTimeByFrame()
        show { tab = Tab.Apps }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        val home = fade.alpha(Tab.Home)
        val apps = fade.alpha(Tab.Apps)
        show { tab = Tab.Movies }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(setOf(Tab.Home, Tab.Apps), fade.outgoing.toSet())
        assertTrue(abs(fade.alpha(Tab.Home) - home) < 0.2f && abs(fade.alpha(Tab.Apps) - apps) < 0.2f)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1f, fade.alpha(Tab.Movies))
        assertEquals(emptyList<Tab>(), fade.outgoing)
    }

    private fun show(change: () -> Unit) = compose.runOnUiThread {
        change()
        // With the test clock stopped, a state write waits for a frame that never comes.
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun theFirstPageShowsAtOnce() {
        var homeAlpha = -1f
        compose.setContent { homeAlpha = rememberTabFade(Tab.Home).alpha(Tab.Home) }
        compose.waitForIdle()
        assertEquals(1f, homeAlpha)
    }
}
