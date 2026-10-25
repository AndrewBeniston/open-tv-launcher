package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import app.opentvlauncher.ui.downTvKey
import app.opentvlauncher.ui.releaseTvKey
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Bar items that go (reviews R8-1 and C8-1): Movies or Shows with no rows, Assistant when the TV
 * no longer answers it. Each item keeps its own node, so an item that stays keeps its focus, and
 * a press never moves to another item. When the focused item goes, the first item left in its
 * pill takes focus.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BarFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var assistant by mutableStateOf(true)
    private var tabs by mutableStateOf(listOf(Tab.Home, Tab.Movies, Tab.Shows, Tab.Apps))
    private var shown by mutableStateOf(Tab.Home)
    private val clicks = mutableListOf<BarButton>()

    private fun start(target: BarItem) {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.setContent {
            LauncherTheme {
                LauncherTabs(
                    tabs = tabs,
                    tab = shown,
                    buttons = listOfNotNull(BarButton.Assistant.takeIf { assistant }, BarButton.Screensaver, BarButton.Settings),
                    barRequest = BarRequest(target, 1),
                    scroll = remember { PageScroll() },
                    active = true,
                    onTab = { shown = it },
                    onButton = { clicks += it },
                    onDown = {},
                    onBarFocus = {},
                ) {
                    // Something focusable on the page, as Home is.
                    Box(Modifier.size(10.dp).testTag(PAGE).focusable())
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertFocusOn(name: String) = compose.onNode(isFocused()).assert(
        SemanticsMatcher("named $name") { hasText(name).matches(it) || hasContentDescription(name).matches(it) },
    )

    private fun change(action: () -> Unit) {
        compose.runOnUiThread {
            action()
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
    }

    @Test
    fun theFocusedAssistantGoesAndScreensaverTakesFocus() {
        start(BarItem.OfButton(BarButton.Assistant))
        assertFocusOn("Assistant")
        change { assistant = false }
        assertFocusOn("Start Screensaver")
    }

    @Test
    fun assistantGoesAndSettingsKeepsFocus() {
        start(BarItem.OfButton(BarButton.Settings))
        assertFocusOn("Settings")
        change { assistant = false }
        assertFocusOn("Settings")
    }

    @Test
    fun assistantGoesAndScreensaverKeepsFocus() {
        start(BarItem.OfButton(BarButton.Screensaver))
        assertFocusOn("Start Screensaver")
        change { assistant = false }
        assertFocusOn("Start Screensaver")
    }

    @Test
    fun theFocusedMoviesTabGoesAndHomeTakesFocus() {
        start(BarItem.OfTab(Tab.Movies))
        assertFocusOn("Movies")
        change { tabs = tabs - Tab.Movies }
        assertFocusOn("Home")
    }

    @Test
    fun moviesGoesAndAppsKeepsFocus() {
        start(BarItem.OfTab(Tab.Apps))
        assertFocusOn("Apps")
        change { tabs = tabs - Tab.Movies }
        assertFocusOn("Apps")
    }

    @Test
    fun anItemThatGoesDoesNotTakeFocusFromThePage() {
        start(BarItem.OfButton(BarButton.Assistant))
        assertFocusOn("Assistant")
        compose.onNodeWithTag(PAGE).requestFocus()
        compose.waitForIdle()
        change { assistant = false }
        compose.onNodeWithTag(PAGE).assertIsFocused()
    }

    /** The release belongs to the press on Assistant, which went: Screensaver does not open. */
    @Test
    fun okHeldOnAssistantAsItGoesOpensNothing() {
        start(BarItem.OfButton(BarButton.Assistant))
        assertFocusOn("Assistant")
        compose.downTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        change { assistant = false }
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        assertEquals(emptyList<BarButton>(), clicks)
        // A new OK on Screensaver opens it.
        assertFocusOn("Start Screensaver")
        compose.downTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        assertEquals(listOf(BarButton.Screensaver), clicks)
    }

    private companion object {
        const val PAGE = "page"
    }
}
