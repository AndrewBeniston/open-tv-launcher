package app.opentvlauncher.ui.details

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.system.TitleApp
import app.opentvlauncher.system.TitleAppIcons
import app.opentvlauncher.ui.downTvKey
import app.opentvlauncher.ui.releaseTvKey
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
 * Merge gate V1: an OK press belongs to the app button it began on. The list of buttons can
 * change under a held OK, as when the first read of the installed apps replaces the Stremio
 * button that shows before it with Netflix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleAppButtonsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var apps by mutableStateOf(listOf(TitleApp.Stremio))
    private val opened = mutableListOf<TitleApp>()

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.setContent {
            LauncherTheme {
                Row { TitleAppButtons(Title(TitleKind.Movie, "Slow Weekend"), apps, TitleAppIcons.Unknown, onOpen = { opened += it }) }
            }
        }
    }

    private fun showApps(vararg shown: TitleApp) {
        compose.runOnUiThread {
            apps = shown.toList()
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
    }

    private fun focus(label: String) {
        compose.onNodeWithText(label).requestFocus()
        compose.waitForIdle()
    }

    private fun okDown() {
        compose.downTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
    }

    private fun okUp() {
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
    }

    @Test
    fun aHeldOkDoesNotMoveFromARemovedStremioButtonToNetflix() {
        focus("Stremio")
        okDown()
        showApps(TitleApp.Netflix)
        okUp()
        assertEquals(emptyList<TitleApp>(), opened)
        // A new press on the new button opens it.
        focus("Netflix")
        okDown()
        okUp()
        assertEquals(listOf(TitleApp.Netflix), opened)
    }

    /** The held button stays when the other one goes, on either side of it, and its release opens it. */
    @Test
    fun aHeldOkOpensItsOwnButtonWhenTheOtherGoes() {
        showApps(TitleApp.Stremio, TitleApp.Netflix)
        focus("Netflix")
        okDown()
        showApps(TitleApp.Netflix)
        okUp()
        assertEquals(listOf(TitleApp.Netflix), opened)

        opened.clear()
        showApps(TitleApp.Stremio, TitleApp.Netflix)
        focus("Stremio")
        okDown()
        showApps(TitleApp.Stremio)
        okUp()
        assertEquals(listOf(TitleApp.Stremio), opened)
    }
}
