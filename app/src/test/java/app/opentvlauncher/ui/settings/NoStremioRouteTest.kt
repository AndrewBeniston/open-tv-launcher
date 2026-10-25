package app.opentvlauncher.ui.settings

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
import androidx.compose.ui.test.requestFocus
import app.opentvlauncher.FakeTraktApp
import app.opentvlauncher.MainActivity
import app.opentvlauncher.app
import app.opentvlauncher.settings.TitleAction
import app.opentvlauncher.system.TitleApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The fake Trakt TV, with every app of the apps row but Stremio. */
class NoStremioTraktApp : FakeTraktApp() {
    override val tvApps = super.tvApps - TitleApp.Stremio.packageName
}

/** 25 Oct 2026: without Stremio, OK on a title opens Details, and Settings offers only Show Details. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = NoStremioTraktApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoStremioRouteTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Ember").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.waitForIdle()
    }

    @After
    fun tearDown() = (compose.activity.application as FakeTraktApp).server.close()

    @Test
    fun okOnATitleOpensDetailsEvenWhenTheSettingSaysStremio() {
        runBlocking { compose.activity.app.settings.setTitleAction(TitleAction.OpenInStremio) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Ember").requestFocus()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Ember").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Remove from Watchlist").assertExists()
        compose.onNodeWithText("Opening in Stremio").assertDoesNotExist()
    }

    @Test
    fun theAppsRowAndTheButtonsHaveNoStremio() {
        compose.onAllNodesWithText("Stremio").assertCountEquals(0)
        compose.onNodeWithText("YouTube").assertExists()
    }

    @Test
    fun settingsOffersOnlyShowDetailsAndOkKeepsIt() {
        compose.onNodeWithContentDescription("All Apps").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Personal TV\nLauncher").performClick()
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Selecting a Title")).assert(hasText("Show Details"))
        compose.onNodeWithText("Open in Stremio").assertDoesNotExist()
        compose.onNode(isFocused()).performClick()
        compose.waitForIdle()
        assertEquals(TitleAction.ShowDetails, runBlocking { compose.activity.app.settings.titleAction.first() })
        compose.onNode(isFocused()).assert(hasText("Show Details"))
    }
}
