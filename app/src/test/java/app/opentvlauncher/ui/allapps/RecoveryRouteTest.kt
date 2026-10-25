package app.opentvlauncher.ui.allapps

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.lifecycleScope
import app.opentvlauncher.MainActivity
import app.opentvlauncher.OfflineLauncherApp
import app.opentvlauncher.app
import app.opentvlauncher.system.GoogleHomePass
import app.opentvlauncher.system.SystemRoutes
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Home > All Apps > Google Launcher or Settings, in the real activity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = OfflineLauncherApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecoveryRouteTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        // Past the Trakt sign-in screen, as after "Skip for Now". runBlocking on the main thread
        // would wait for DataStore, which waits for the paused test main dispatcher.
        compose.runOnUiThread { compose.activity.lifecycleScope.launch { compose.activity.app.session.skip() } }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("All Apps").fetchSemanticsNodes().isNotEmpty() }
        val home = ComponentName(SystemRoutes.GOOGLE_TV_HOME, "com.google.android.apps.tv.launcherx.home.HomeActivity")
        shadowOf(compose.activity.packageManager).apply {
            addActivityIfNotPresent(home)
            addIntentFilterForActivity(
                home,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) },
            )
        }
        // A TV has no touchscreen, so its window is never in touch mode. Robolectric starts in it.
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("All Apps").performClick()
        compose.waitForIdle()
    }

    private fun nextStartedActivity(): Intent = shadowOf(compose.activity).nextStartedActivity

    @Test
    fun googleTvHomeOpensGooglesHomeScreen() {
        compose.onNodeWithText("Google Launcher").performClick()
        assertEquals(SystemRoutes.GOOGLE_TV_HOME, nextStartedActivity().component?.packageName)
        // The Home override lets Google's home stay when the launcher opened it.
        assertNotNull(GoogleHomePass.given(compose.activity))
    }

    @Test
    fun settingsOpensSystemSettings() {
        compose.onNodeWithText("Settings").performClick()
        assertEquals(Settings.ACTION_SETTINGS, nextStartedActivity().action)
    }

    @Test
    fun allAppsOpensWithFocusOnYouTube() {
        compose.onNode(isFocused()).assert(hasText("YouTube"))
    }

    /** Up Next exploration, "How focus moves": Back goes to the bar on Apps, then to the Home tab. */
    @Test
    fun backGoesToTheBarThenTheHomeTab() {
        back()
        compose.onNode(isFocused()).assert(hasText("Apps"))
        compose.onNodeWithText("Google Launcher").assertExists()
        back()
        compose.onNode(isFocused()).assert(hasText("Home"))
        compose.onNodeWithText("Google Launcher").assertDoesNotExist()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
