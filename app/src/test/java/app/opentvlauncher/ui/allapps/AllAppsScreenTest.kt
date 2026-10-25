package app.opentvlauncher.ui.allapps

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.opentvlauncher.data.AppTile
import app.opentvlauncher.home.HomeMapper
import app.opentvlauncher.system.InstalledApp
import app.opentvlauncher.system.InstalledApps
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** All Apps with the TV's own apps: Other Apps, the Play Store, and focus on the launcher tile after Settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AllAppsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val installed = InstalledApps.Groups(
        other = listOf(InstalledApp("com.channel4.ondemand", "Channel 4", null), InstalledApp("com.amazon.amazonvideo.livingroom", "Prime Video", null)),
        playStore = InstalledApp(InstalledApps.PLAY_STORE, "Google Play Store", null),
    )

    private fun show(
        startOnLauncher: Boolean = false,
        opened: MutableList<String> = mutableListOf(),
        apps: List<AppTile> = HomeMapper.apps.dropLast(1),
        installed: InstalledApps.Groups = this.installed,
    ) {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.setContent {
            LauncherTheme {
                AllAppsScreen(
                    apps = apps,
                    installed = installed,
                    onOpenApp = {},
                    onOpenInstalled = { opened += it.packageName },
                    onOpenStockHome = {},
                    onOpenSettings = {},
                    onOpenLauncherSettings = {},
                    onBack = {},
                    startOnLauncher = startOnLauncher,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun otherAppsAndThePlayStoreOpenTheirApps() {
        val opened = mutableListOf<String>()
        show(opened = opened)
        compose.onNodeWithText("Other Apps").assertExists()
        compose.onNodeWithText("Prime Video").performClick()
        compose.onNodeWithText("Play Store").performClick()
        assertEquals(listOf("com.amazon.amazonvideo.livingroom", InstalledApps.PLAY_STORE), opened)
    }

    /** A report from the TV: "Google TV Home" filled its card edge to edge. No System tile title reaches the card's edge or is cut off. */
    @Test
    fun noSystemTileTitleRunsToTheCardEdge() {
        show()
        listOf("Settings", "Play Store", "Google Launcher", "Personal TV\nLauncher").forEach { label ->
            val tile = compose.onNodeWithText(label).getUnclippedBoundsInRoot()
            val title = compose.onNodeWithText(label, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("$label is cut off", (0 until layout.lineCount).any(layout::isLineEllipsized))
            val right = title.getUnclippedBoundsInRoot().left + with(compose.density) { layout.size.width.toDp() }
            assertTrue("$label ends at $right, tile at ${tile.right}", right <= tile.right - 12.dp + 0.5.dp)
        }
    }

    /** 25 Oct 2026: the System tile has the TV's own home app's name, and does not cut it off. */
    @Test
    fun theHomeTileHasTheTvsHomeAppName() {
        show(installed = installed.copy(homeLabel = "Android TV Home"))
        compose.onNodeWithText("Google Launcher").assertDoesNotExist()
        compose.onNodeWithText("Opens Android TV Home.").assertExists()
        val title = compose.onNodeWithText("Android TV Home", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse((0 until layouts.single().lineCount).any(layouts.single()::isLineEllipsized))
    }

    /** 25 Oct 2026: with none of Home's apps on the TV, there is no On Home group, and focus starts on Other Apps. */
    @Test
    fun withNoneOfHomesAppsOnHomeIsLeftOut() {
        show(apps = emptyList())
        compose.onNodeWithText("On Home").assertDoesNotExist()
        compose.onNode(isFocused()).assert(hasText("Channel 4"))
    }

    @Test
    fun afterSettingsFocusIsOnTheLauncherTile() {
        show(startOnLauncher = true)
        compose.onNode(isFocused()).assert(hasText("Personal TV\nLauncher"))
    }
}
