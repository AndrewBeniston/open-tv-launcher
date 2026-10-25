package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.ui.focus.PillButton
import app.opentvlauncher.ui.sendTvKey
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Merge gate V3: a Movies or Shows page leaves the screen once its fade out ends, and comes back
 * from its saved state (MainActivity). Its banner button comes back with it: Down from the bar
 * goes to Details, not to the first button. The saved state holder here is the one MainActivity
 * keeps for its tabs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabBannerButtonTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var shown by mutableStateOf(true)
    private var returns by mutableIntStateOf(0)
    private var barFocused by mutableStateOf(false)
    private val bar = FocusRequester()

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val saved = rememberSaveableStateHolder()
            LauncherTheme {
                PillButton("Bar", {}, Modifier.focusRequester(bar))
                if (shown) {
                    saved.SaveableStateProvider("Movies") {
                        HomeScreen(
                            SampleHome.content.copy(apps = emptyList()),
                            homeRequests = 0,
                            onOpenApp = {},
                            takeFocus = false,
                            focusReturns = returns,
                            barFocused = barFocused,
                        )
                    }
                }
            }
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun change(block: () -> Unit) {
        compose.runOnUiThread {
            block()
            Snapshot.sendApplyNotifications()
        }
        settle()
    }

    @Test
    fun aTabThatLeftTheScreenKeepsItsBannerButton() {
        // Down from the bar to the page, Up to the banner, then focus on Details.
        change { returns++ }
        compose.sendTvKey(KeyEvent.KEYCODE_DPAD_UP)
        settle()
        compose.onNodeWithText("Details").requestFocus()
        settle()
        compose.onNodeWithText("Details").assertIsFocused()
        // Up to the bar, and the page leaves the screen after its fade.
        change {
            barFocused = true
            bar.requestFocus()
            shown = false
        }
        change { shown = true }
        // Down from the bar.
        change {
            barFocused = false
            returns++
        }
        compose.onNodeWithText("Details").assertIsFocused()
    }
}
