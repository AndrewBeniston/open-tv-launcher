package app.opentvlauncher.ui.details

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.opentvlauncher.home.EpisodeRef
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.system.TitleApp
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The details page: its words, its first focus and its buttons. Without a loader there is no logo, so the title shows as text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val slowWeekend = Title(
        kind = TitleKind.Movie,
        title = "Slow Weekend",
        year = 2023,
        imdb = "tt91000007",
        traktId = 7,
        overview = "Hal sweeps the harbour each morning.",
        runtime = 124,
        genres = listOf("drama"),
        rating = 7.9,
    )

    @Test
    fun theDesignsContentShowsAndPlayHasFocus() {
        var played = false
        // A TV has no touchscreen, so its window is never in touch mode. Robolectric starts in it.
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.setContent {
            LauncherTheme {
                DetailsScreen(slowWeekend, WatchlistButton.Remove, onPlay = { played = true }, onWatchlist = {}, onBack = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Slow Weekend").assertExists()
        compose.onNodeWithText("2023, 2 h 4 min, Drama").assertExists()
        compose.onNodeWithText("79%").assertExists()
        compose.onAllNodesWithText("Trakt", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Remove from Watchlist").assertExists()
        compose.onNode(isFocused()).assert(hasText("Stremio"))
        compose.onNodeWithText("Stremio").performClick()
        assertTrue(played)
    }

    @Test
    fun theWatchlistButtonAndBackCallBack() {
        var pressed = 0
        var closed = false
        compose.setContent {
            LauncherTheme {
                DetailsScreen(slowWeekend, WatchlistButton.Undo, onPlay = {}, onWatchlist = { pressed++ }, onBack = { closed = true })
            }
        }
        compose.onNodeWithText("Add Back to Watchlist").performClick()
        assertEquals(1, pressed)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertTrue(closed)
    }

    /** The hand-off takes focus, so OK cannot press Play again under it. */
    @Test
    fun theHandOffTakesFocusAndGivesItBackToPlay() {
        var handOff by mutableStateOf(false)
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.setContent {
            LauncherTheme {
                Box {
                    DetailsScreen(slowWeekend, WatchlistButton.Remove, onPlay = {}, onWatchlist = {}, onBack = {}, active = !handOff)
                    if (handOff) HandOffScreen(slowWeekend, onBack = { handOff = false })
                }
            }
        }
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Stremio"))
        handOff = true
        compose.waitForIdle()
        // The hand-off page itself has focus.
        compose.onNode(isFocused()).assert(!hasText("Stremio"))
        compose.onNodeWithText("Opening in Stremio").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Stremio"))
    }
}

/** The words on the details page and the hand-off screen. */
class TitleTextTest {
    private val film = Title(kind = TitleKind.Movie, title = "Hollowby", year = 2016, runtime = 118, genres = listOf("comedy", "drama", "romance"), rating = 7.04)

    @Test
    fun detailsMetaHasYearRuntimeAndTwoGenres() {
        assertEquals("2016, 1 h 58 min, Comedy, Drama", TitleText.detailsMeta(film))
        assertEquals("2017, Series, Science Fiction", TitleText.detailsMeta(Title(kind = TitleKind.Show, title = "Fern", year = 2017, genres = listOf("science-fiction"))))
    }

    @Test
    fun runtimeRatingAndButtonLabels() {
        assertEquals("58 min", TitleText.runtime(58))
        assertEquals("2 h", TitleText.runtime(120))
        assertEquals("70%", TitleText.rating(film))
        assertEquals("Stremio", TitleText.appLabel(film, TitleApp.Stremio))
        assertEquals("Resume", TitleText.appLabel(film.copy(progress = 0.53f), TitleApp.Stremio))
        assertEquals("Netflix", TitleText.appLabel(film.copy(progress = 0.53f), TitleApp.Netflix))
    }

    @Test
    fun handOffMetaFollowsTheTitle() {
        assertEquals("Film, 53% Watched", TitleText.handOffMeta(film.copy(progress = 0.53f)))
        assertEquals("Season 5, Episode 6", TitleText.handOffMeta(film.copy(kind = TitleKind.Show, episode = EpisodeRef(5, 6), progress = 0.4f)))
        assertEquals("Film, 2016", TitleText.handOffMeta(film))
    }
}
