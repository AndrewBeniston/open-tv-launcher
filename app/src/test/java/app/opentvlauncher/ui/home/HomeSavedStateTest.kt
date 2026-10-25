package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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
 * 24 Oct 2026: after All Apps or Settings, the rows forgot their last card and the banner
 * went back to its first slide, because Home left the composition. The rows and the banner must
 * keep them, also when Android closes the launcher behind another app and opens it again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeSavedStateTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager
    private var showHome by mutableStateOf(true)
    private var startApp by mutableIntStateOf(0)
    private var content by mutableStateOf(SampleHome.content)

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.mainClock.autoAdvance = false
    }

    @Composable
    private fun Home() {
        focusManager = LocalFocusManager.current
        LauncherTheme { HomeScreen(content, homeRequests = 0, onOpenApp = {}, startApp = startApp) }
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        compose.sendTvKey(keyCode, focusManager)
        settle()
    }

    private fun up(times: Int = 1) = press(KeyEvent.KEYCODE_DPAD_UP, times)
    private fun down() = press(KeyEvent.KEYCODE_DPAD_DOWN)
    private fun right(times: Int = 1) = press(KeyEvent.KEYCODE_DPAD_RIGHT, times)

    private fun assertFocusOn(name: String) = compose.onNode(isFocused()).assert(
        SemanticsMatcher("named $name") { hasText(name).matches(it) || hasContentDescription(name).matches(it) },
    )

    /** The banner slide on screen, by its meta line: each sample slide has its own. */
    private fun shownSlide(): String = content.slides.map { it.meta }.single { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }

    /** Leaves focus on a card past the first in every row, and the banner on a slide past the first. */
    private fun leaveAMarkEverywhere(): String {
        down()
        right(3)
        assertFocusOn("The Glacier: Rescue from Skarvika")
        down()
        right(2)
        assertFocusOn("Building a Shed with a Friend")
        down()
        right(4)
        assertFocusOn("Orbit")
        down()
        right(6)
        assertFocusOn("The Fifth Bay")
        up(5)
        compose.onNode(isFocused()).assert(hasText("Resume") or hasText("Stremio"))
        right(4) // Details, the dot of this slide, then two slides on.
        while (shownSlide() == content.slides.first().meta) right()
        return shownSlide()
    }

    /** Down through the rows: each one gives back the card it had. */
    private fun assertEveryRowRemembers() {
        down()
        assertFocusOn("The Glacier: Rescue from Skarvika")
        down()
        assertFocusOn("Building a Shed with a Friend")
        down()
        assertFocusOn("Orbit")
        down()
        assertFocusOn("The Fifth Bay")
    }

    /**
     * Changes test state on the UI thread and tells Compose at once. With the banner focused nothing
     * else runs, so without the notice the change waits for an unrelated frame.
     */
    private fun change(block: () -> Unit) {
        compose.runOnUiThread {
            block()
            Snapshot.sendApplyNotifications()
        }
        settle()
    }

    /** Home, or in its place a page that stands in for All Apps, as MainActivity draws them. */
    private fun setHomeOrAllApps() {
        compose.setContent {
            val saved = rememberSaveableStateHolder()
            if (showHome) {
                saved.SaveableStateProvider("home") { Home() }
            } else {
                val page = remember { FocusRequester() }
                Box(Modifier.fillMaxSize().focusRequester(page).focusable())
                LaunchedEffect(Unit) { page.requestFocus() }
            }
        }
        settle()
    }

    /** To All Apps and back: Home opens on the All Apps tile. */
    private fun toAllAppsAndBack() {
        change { showHome = false }
        compose.onAllNodesWithContentDescription("All Apps").assertCountEquals(0)
        change {
            startApp = 4
            showHome = true
        }
        assertFocusOn("All Apps")
    }

    @Test
    fun afterAllAppsTheRowsKeepTheirCardsAndTheBannerItsSlide() {
        setHomeOrAllApps()
        val slide = leaveAMarkEverywhere()
        toAllAppsAndBack()
        assertEquals(slide, shownSlide())
        assertEveryRowRemembers()
    }

    /** Two Top Picks with the same name: Home gives back the one that showed, not the first. */
    @Test
    fun afterAllAppsTheBannerKeepsItsSlideWhenTwoTitlesShareAName() {
        content = SampleHome.content.copy(
            slides = List(2) { n ->
                SampleHome.content.slides[3].copy(key = "TopPick:Movie:${n + 1}", title = "The Same Name", meta = "Distinct title $n")
            },
        )
        setHomeOrAllApps()
        assertFocusOn("YouTube")
        up()
        assertFocusOn("Stremio")
        right()
        assertFocusOn("Details")
        right(2) // The dot of this slide, then the next slide.
        assertEquals("Distinct title 1", shownSlide())
        toAllAppsAndBack()
        assertEquals("Distinct title 1", shownSlide())
    }

    /** Android saves the launcher's state in a Bundle, closes it, and later opens it with that state. */
    @Test
    fun afterAndroidClosesTheLauncherBehindAnAppTheRowsAndBannerAreKept() {
        var registry by mutableStateOf(SaveableStateRegistry(null, ::canGoInABundle))
        compose.setContent {
            if (showHome) CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { Home() }
        }
        settle()
        val slide = leaveAMarkEverywhere()

        var saved: Map<String, List<Any?>> = emptyMap()
        compose.runOnUiThread { saved = registry.performSave() }
        change { showHome = false }
        change {
            registry = SaveableStateRegistry(saved, ::canGoInABundle)
            showHome = true
        }
        assertFocusOn("YouTube")
        assertEquals(slide, shownSlide())
        assertEveryRowRemembers()
    }

    /** Roughly what Android's saved state accepts. */
    private fun canGoInABundle(value: Any) = value is java.io.Serializable || value is android.os.Parcelable
}
