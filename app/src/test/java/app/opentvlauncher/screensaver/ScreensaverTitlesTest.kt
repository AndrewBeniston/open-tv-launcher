package app.opentvlauncher.screensaver

import app.opentvlauncher.home.HomeState
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleImages
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.home.TraktRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 24 Oct 2026: the screensaver shows Top Picks for You, the same list as the row, from
 * the saved rows. Without Top Picks, the watchlist.
 */
class ScreensaverTitlesTest {
    private fun title(n: Int, imdb: String? = "tt$n", fanart: String? = null) =
        Title(kind = TitleKind.Movie, title = "Title $n", imdb = imdb, traktId = n, images = TitleImages(fanart = fanart))

    @Test
    fun topPicksComeFirst() {
        val state = HomeState(trakt = TraktRows(0, picks = listOf(title(1), title(2)), watchlist = listOf(title(3)), continueWatching = listOf(title(4))))
        assertEquals(setOf("Title 1", "Title 2"), screensaverTitles(state, day = 1).map { it.title }.toSet())
    }

    @Test
    fun withoutTopPicksTheWatchlistIsUsedThenContinueWatching() {
        val withWatchlist = HomeState(trakt = TraktRows(0, watchlist = listOf(title(2), title(3)), continueWatching = listOf(title(5))))
        assertEquals(setOf("Title 2", "Title 3"), screensaverTitles(withWatchlist, day = 1).map { it.title }.toSet())
        val onlyInProgress = HomeState(trakt = TraktRows(0, continueWatching = listOf(title(5))))
        assertEquals(listOf("Title 5"), screensaverTitles(onlyInProgress, day = 1).map { it.title })
        assertTrue(screensaverTitles(HomeState(), day = 1).isEmpty())
    }

    /** Metahub's backdrop needs an IMDb id; Trakt's fanart does not. A title with neither has no picture. */
    @Test
    fun aTitleWithoutABackdropIsLeftOut() {
        val state = HomeState(trakt = TraktRows(0, picks = listOf(title(1, imdb = null), title(2, imdb = null, fanart = "https://trakt/f2"), title(3))))
        assertEquals(setOf("Title 2", "Title 3"), screensaverTitles(state, day = 1).map { it.title }.toSet())
    }

    @Test
    fun theOrderIsTheSameAllDayAndChangesTheNextDay() {
        val state = HomeState(trakt = TraktRows(0, picks = (1..20).map { title(it) }))
        assertEquals(screensaverTitles(state, 20_000), screensaverTitles(state, 20_000))
        assertTrue(screensaverTitles(state, 20_000) != screensaverTitles(state, 20_001))
    }

    @Test
    fun theLineUnderTheLogoIsTheBannersSentence() {
        assertEquals("Action film from 2002", title(1).copy(year = 2002, genres = listOf("action", "thriller")).screensaverMeta())
        assertEquals("Drama series from 2012", title(1).copy(kind = TitleKind.Show, year = 2012, genres = listOf("drama")).screensaverMeta())
        assertEquals("Film from 1952", title(1).copy(year = 1952).screensaverMeta())
        assertEquals("Film", title(1).screensaverMeta())
    }

    private fun synopsis(overview: String?) = title(1).copy(overview = overview).screensaverSynopsis()

    /** 24 Oct 2026: one or two sentences of the Trakt summary, cut only at a sentence end. */
    @Test
    fun theSynopsisIsTheFirstTwoSentences() {
        assertEquals(
            "A bureaucrat learns he is dying. He looks for meaning.",
            synopsis("A bureaucrat learns he is dying. He looks for meaning. He finds it in a park.\n\nA classic."),
        )
    }

    @Test
    fun aLongSecondSentenceIsLeftOut() {
        val second = "Then " + "very ".repeat(40) + "long."
        assertEquals("A short start.", synopsis("A short start. $second"))
    }

    @Test
    fun aFirstSentenceThatIsTooLongGivesNoSynopsis() {
        assertEquals(null, synopsis("A " + "very ".repeat(45) + "long sentence. Short."))
        assertEquals(null, synopsis(null))
        assertEquals(null, synopsis("   "))
    }

    @Test
    fun initialsAndTitlesDoNotEndASentence() {
        assertEquals(
            "Dr. Mabuse and J. R. Ewing meet in the U.S. capital. It goes badly.",
            synopsis("Dr. Mabuse and J. R. Ewing meet in the U.S. capital. It goes badly. Very badly."),
        )
    }

    @Test
    fun aSentenceCanEndInsideQuotes() {
        assertEquals("""He says "Run." She asks why?""", synopsis("""He says "Run." She asks why? Nobody knows."""))
    }

    @Test
    fun aSummaryWithoutAFullStopIsKeptWhenShort() {
        assertEquals("A road trip along a coast", synopsis("A road trip along a coast"))
    }
}
