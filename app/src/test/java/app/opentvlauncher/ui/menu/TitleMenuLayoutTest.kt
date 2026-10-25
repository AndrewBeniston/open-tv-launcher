package app.opentvlauncher.ui.menu

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import app.opentvlauncher.home.EpisodeRef
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Review c8a9021, K2: the menu with every row fits the 540 dp screen, focused last row included. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleMenuLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun assertFits(name: String, episode: EpisodeRef?) {
        val title = Title(
            kind = if (episode == null) TitleKind.Movie else TitleKind.Show,
            title = name,
            traktId = 2,
            episode = episode,
            progress = 0.3f,
            playbackIds = listOf(1),
        )
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.setContent { LauncherTheme { TitleMenu(title, titleMenuChanges(title, listed = true), TitleMenuStatus.Ready, {}, {}, {}, {}) } }
        compose.waitForIdle()
        val last = compose.onNodeWithContentDescription("Remove from Watchlist")
        val rest = last.getUnclippedBoundsInRoot()
        last.requestFocus()
        compose.waitForIdle()
        val focused = last.getUnclippedBoundsInRoot()
        val screen = compose.onRoot().getBoundsInRoot()
        assertTrue("Last row $rest, focused $focused, screen $screen", rest.bottom <= screen.bottom && focused.bottom <= screen.bottom)
    }

    @Test
    fun aFilmMenuFits() = assertFits("Harbour Lights", null)

    @Test
    fun aShortEpisodeMenuFits() = assertFits("Mist", EpisodeRef(1, 4))

    @Test
    fun anEpisodeWithATwoLineTitleFits() = assertFits("The Man in the Flat Across the Road from the Boy by the Old Bus Stop", EpisodeRef(1, 4))
}
