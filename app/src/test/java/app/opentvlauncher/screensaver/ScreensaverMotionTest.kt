package app.opentvlauncher.screensaver

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Nothing bright stays in one place on an OLED screen, even with one title. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensaverMotionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** With one title the year stayed in its first corner for good. */
    @Test
    fun oneTitleStillMovesToANewCorner() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LauncherTheme { ScreensaverScreen(listOf(Title(kind = TitleKind.Movie, title = "Ember", year = 1952, imdb = "tt9100008", traktId = 1))) }
        }
        compose.mainClock.advanceTimeBy(1_000)
        val first = compose.onNodeWithText("Film from 1952").getBoundsInRoot()
        compose.mainClock.advanceTimeBy(84_000)
        val later = compose.onNodeWithText("Film from 1952").getBoundsInRoot()
        assertNotEquals(first, later)
    }
}
