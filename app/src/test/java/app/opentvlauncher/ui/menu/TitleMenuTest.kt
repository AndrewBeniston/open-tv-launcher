package app.opentvlauncher.ui.menu

import app.opentvlauncher.home.EpisodeRef
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleChange
import app.opentvlauncher.home.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which choices the menu offers, and the second OK for the changes that cannot be taken back. */
class TitleMenuTest {
    private val film = Title(TitleKind.Movie, "Harbour Lights", traktId = 1, playbackIds = listOf(70))

    @Test
    fun aPausedFilmOnTheWatchlistHasAllFourChoices() {
        assertEquals(TitleChange.entries, titleMenuChanges(film, listed = true))
        assertEquals(listOf("Mark as Watched", "Mark as Unwatched", "Reset Progress", "Remove from Watchlist"), TitleChange.entries.map { it.label })
    }

    /** A watchlist title that fills a short row: nothing to reset, and a show with no episode has no plays to mark. */
    @Test
    fun choicesFollowWhatTheTitleHas() {
        assertEquals(listOf(TitleChange.MarkWatched, TitleChange.MarkUnwatched), titleMenuChanges(film.copy(playbackIds = emptyList()), listed = false))
        assertEquals(listOf(TitleChange.RemoveFromWatchlist), titleMenuChanges(Title(TitleKind.Show, "Fern", traktId = 2), listed = true))
        val episode = Title(TitleKind.Show, "Fern", traktId = 2, episode = EpisodeRef(2, 8), playbackIds = listOf(5))
        assertEquals(listOf(TitleChange.MarkWatched, TitleChange.MarkUnwatched, TitleChange.ResetProgress), titleMenuChanges(episode, listed = false))
    }

    /** 25 Oct 2026: an up-next episode has no plays to remove, so the menu has no Mark as Unwatched. */
    @Test
    fun anUpNextEpisodeHasNoMarkAsUnwatched() {
        val upNext = Title(TitleKind.Show, "Kitchen Council", traktId = 2, episode = EpisodeRef(1, 5), upNextAt = 1L)
        assertEquals(listOf(TitleChange.MarkWatched), titleMenuChanges(upNext, listed = false))
        assertEquals(listOf(TitleChange.MarkWatched, TitleChange.RemoveFromWatchlist), titleMenuChanges(upNext, listed = true))
    }

    @Test
    fun onlyMarkAsWatchedSendsAtOnce() {
        assertEquals(TitleMenuStatus.Sending(TitleChange.MarkWatched), TitleMenuStatus.Ready.next(TitleChange.MarkWatched))
        listOf(TitleChange.MarkUnwatched, TitleChange.ResetProgress, TitleChange.RemoveFromWatchlist).forEach { change ->
            assertEquals(TitleMenuStatus.Confirming(change), TitleMenuStatus.Ready.next(change))
            assertEquals(TitleMenuStatus.Sending(change), TitleMenuStatus.Confirming(change).next(change))
            assertEquals(TitleMenuStatus.Sending(change), TitleMenuStatus.Failed(change).next(change))
        }
    }

    /** A second OK on another row asks again, and nothing starts while a change is sending. */
    @Test
    fun aSecondOkCountsOnTheSameRowOnly() {
        assertEquals(
            TitleMenuStatus.Confirming(TitleChange.ResetProgress),
            TitleMenuStatus.Confirming(TitleChange.MarkUnwatched).next(TitleChange.ResetProgress),
        )
        assertNull(TitleMenuStatus.Sending(TitleChange.MarkWatched).next(TitleChange.ResetProgress))
    }
}
