package app.opentvlauncher.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.lifecycleScope
import app.opentvlauncher.MainActivity
import app.opentvlauncher.OfflineLauncherApp
import app.opentvlauncher.app
import app.opentvlauncher.settings.TitleAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** All Apps > Open TV Launcher > Settings, and back, in the real activity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = OfflineLauncherApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsRouteTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.lifecycleScope.launch { compose.activity.app.session.skip() } }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("All Apps").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("All Apps").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Open TV\nLauncher").performClick()
        compose.waitForIdle()
    }

    @Test
    fun settingsOpensWithFocusOnSelectingATitle() {
        compose.onNode(isFocused()).assert(hasText("Selecting a Title")).assert(hasText("Show Details"))
    }

    @Test
    fun okOnSelectingATitleSwitchesToStremio() {
        compose.onNode(isFocused()).performClick()
        compose.waitUntil(5_000) { runBlocking { compose.activity.app.settings.titleAction.first() } == TitleAction.OpenInStremio }
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Open in Stremio"))
        assertEquals(TitleAction.OpenInStremio, runBlocking { compose.activity.app.settings.titleAction.first() })
    }

    @Test
    fun backReturnsToAllAppsWithFocusOnTheLauncherTile() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Selecting a Title", substring = true).assertDoesNotExist()
        compose.onNode(isFocused()).assert(hasText("Open TV\nLauncher"))
    }

    /** The screensaver shows Top Picks, so its help must not say watchlist art. */
    @Test
    fun theScreensaverHelpSaysTopPicks() {
        compose.onNodeWithText("Screensaver").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        compose.onNodeWithText("Shows Top Picks artwork.", substring = true).assertExists()
        compose.onAllNodesWithText("watchlist art", substring = true).assertCountEquals(0)
    }

    /** Gate 2 failed on the Chromecast (24 Oct 2026): Settings has no way to change the TV's Home app. */
    @Test
    fun settingsHasNoHomeAppRows() {
        compose.onAllNodesWithText("Home App", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Google Home", substring = true).assertCountEquals(0)
    }

    @Test
    fun okOnTraktAccountShowsTheSignInScreen() {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.onNodeWithText("Trakt Account").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Sign In to Trakt").fetchSemanticsNodes().isNotEmpty() }
    }
}
