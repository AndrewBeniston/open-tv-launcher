package app.opentvlauncher.home

import app.opentvlauncher.trakt.TraktEpisode
import app.opentvlauncher.trakt.TraktIds
import app.opentvlauncher.trakt.TraktImages
import app.opentvlauncher.trakt.TraktListItem
import app.opentvlauncher.trakt.TraktMedia
import app.opentvlauncher.trakt.TraktPlayback
import app.opentvlauncher.trakt.TraktShowProgress
import app.opentvlauncher.trakt.TraktUpNext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The rules for the Trakt rows (technical plan, "Rules folded in"). */
class TraktRulesTest {
    private val now = Instant.parse("2026-10-24T20:00:00Z").toEpochMilli()

    private fun media(id: Int, title: String = "Title $id") = TraktMedia(title = title, year = 2000 + id % 20, ids = TraktIds(trakt = id, imdb = "tt$id"))

    private fun episode(show: Int, season: Int, number: Int, pausedAt: String, progress: Float = 40f) = TraktPlayback(
        progress = progress,
        pausedAt = pausedAt,
        type = "episode",
        episode = TraktEpisode(season, number),
        show = media(show, "Show $show"),
    )

    private fun movie(id: Int, pausedAt: String) = TraktPlayback(progress = 53f, pausedAt = pausedAt, type = "movie", movie = media(id))

    @Test
    fun continueWatchingKeepsOnlyTheLatestEpisodeOfEachShow() {
        val rows = TraktRules.continueWatching(
            listOf(
                episode(1, 5, 5, "2026-10-20T21:00:00.000Z"),
                episode(1, 5, 6, "2026-10-22T21:00:00.000Z", progress = 47f),
                episode(2, 2, 8, "2026-10-21T21:00:00.000Z"),
                movie(3, "2026-10-23T21:00:00.000Z"),
            ),
            now,
        )
        assertEquals(listOf("Title 3", "Show 1", "Show 2"), rows.map { it.title })
        val kitchen = rows[1]
        assertEquals(EpisodeRef(5, 6), kitchen.episode)
        assertEquals(0.47f, kitchen.progress!!, 0.001f)
    }

    @Test
    fun itemsPausedMoreThan180DaysAgoAreHidden() {
        val rows = TraktRules.continueWatching(
            listOf(movie(1, "2026-04-28T20:00:00.000Z"), movie(2, "2026-04-26T20:00:00.000Z")),
            now,
        )
        assertEquals(listOf("Title 1"), rows.map { it.title })
    }

    private fun upNext(show: Int, season: Int, number: Int, watchedAt: String?, aired: String? = "2026-01-01T00:00:00.000Z", title: String = "Show $show") = TraktUpNext(
        show = media(show, title),
        progress = TraktShowProgress(
            lastWatchedAt = watchedAt,
            nextEpisode = TraktEpisode(season, number, title = "Episode $number", overview = "Next overview", images = TraktImages(screenshot = listOf("media.trakt.tv/s$show.webp")), firstAired = aired),
        ),
    )

    /** 25 Oct 2026: each started show's next episode joins Continue Watching, as on Trakt. */
    @Test
    fun upNextShowsTheNextEpisodeOfEachStartedShow() {
        val rows = TraktRules.continueWatching(
            emptyList(),
            now,
            upNext = listOf(
                upNext(151163, 1, 5, "2026-10-24T12:21:00.000Z", title = "Kitchen Council"),
                upNext(280754, 1, 10, "2026-10-23T23:07:00.000Z", title = "The Long Field"),
            ),
        )
        assertEquals(listOf("Kitchen Council", "The Long Field"), rows.map { it.title })
        val kitchen = rows[0]
        assertEquals(EpisodeRef(1, 5, "Episode 5", "Next overview"), kitchen.episode)
        assertEquals(Instant.parse("2026-10-24T12:21:00Z").toEpochMilli(), kitchen.upNextAt)
        assertEquals(null, kitchen.progress)
        assertEquals(null, kitchen.pausedAt)
        assertEquals(emptyList<Long>(), kitchen.playbackIds)
        assertEquals("https://media.trakt.tv/s151163.webp", kitchen.images.still)
    }

    /** One card for each show: a paused episode wins over the show's up next. */
    @Test
    fun aPausedEpisodeWinsOverTheShowsUpNext() {
        val rows = TraktRules.continueWatching(
            listOf(episode(1, 1, 4, "2026-10-20T21:00:00.000Z")),
            now,
            upNext = listOf(upNext(1, 1, 5, "2026-10-23T21:00:00.000Z")),
        )
        assertEquals(1, rows.size)
        assertEquals(EpisodeRef(1, 4), rows[0].episode)
        assertEquals(null, rows[0].upNextAt)
    }

    @Test
    fun showsHiddenFromProgressOrDroppedHaveNoUpNext() {
        val rows = TraktRules.continueWatching(
            emptyList(),
            now,
            upNext = listOf(upNext(1, 1, 2, "2026-10-23T21:00:00.000Z"), upNext(2, 3, 4, "2026-10-22T21:00:00.000Z")),
            hiddenShows = setOf(1),
        )
        assertEquals(listOf("Show 2"), rows.map { it.title })
    }

    /** The 180-day limit counts from when the show's last episode was watched. */
    @Test
    fun upNextLastWatchedMoreThan180DaysAgoIsHidden() {
        val rows = TraktRules.continueWatching(
            emptyList(),
            now,
            upNext = listOf(upNext(1, 1, 2, "2026-04-28T20:00:00.000Z"), upNext(2, 1, 2, "2026-04-26T20:00:00.000Z"), upNext(3, 1, 2, null)),
        )
        assertEquals(listOf("Show 1"), rows.map { it.title })
    }

    /** Only an aired episode is up next. A show with no next episode has no card. */
    @Test
    fun anEpisodeThatHasNotAiredOrIsMissingIsNotUpNext() {
        val rows = TraktRules.continueWatching(
            emptyList(),
            now,
            upNext = listOf(
                upNext(1, 2, 1, "2026-10-23T21:00:00.000Z", aired = "2026-11-01T00:00:00.000Z"),
                upNext(2, 1, 2, "2026-10-22T21:00:00.000Z", aired = null),
                TraktUpNext(media(3), TraktShowProgress(lastWatchedAt = "2026-10-21T21:00:00.000Z", nextEpisode = null)),
            ),
        )
        assertEquals(listOf("Show 2"), rows.map { it.title })
    }

    /** One mixed row, newest activity first: paused_at for paused items, last_watched_at for up next. */
    @Test
    fun theRowMixesPausedAndUpNextByNewestActivity() {
        val rows = TraktRules.continueWatching(
            listOf(movie(10, "2026-10-22T21:00:00.000Z"), episode(11, 2, 3, "2026-10-20T21:00:00.000Z")),
            now,
            upNext = listOf(upNext(1, 1, 5, "2026-10-24T12:00:00.000Z"), upNext(2, 1, 10, "2026-10-21T12:00:00.000Z")),
        )
        assertEquals(listOf("Show 1", "Title 10", "Show 2", "Show 11"), rows.map { it.title })
    }

    /** After Mark as Watched, Trakt can still name the same episode as next: its card stays away. */
    @Test
    fun anUpNextEpisodeJustMarkedWatchedStaysAway() {
        val rows = TraktRules.continueWatching(
            listOf(episode(3, 1, 1, "2026-10-20T21:00:00.000Z")),
            now,
            upNext = listOf(upNext(1, 1, 5, "2026-10-24T12:00:00.000Z"), upNext(2, 1, 7, "2026-10-23T12:00:00.000Z")),
        )
        val watched = mapOf(TitleKey(TitleKind.Show, 1) to EpisodeRef(1, 5), TitleKey(TitleKind.Show, 2) to EpisodeRef(1, 6), TitleKey(TitleKind.Show, 3) to EpisodeRef(1, 1))
        // Show 2's next episode is after the one marked, and a paused card is not up next.
        assertEquals(listOf("Show 2", "Show 3"), TraktRules.withoutWatched(rows, watched).map { it.title })
    }

    private fun listed(id: Int, at: String, type: String = "movie") = if (type == "movie") {
        TraktListItem(listedAt = at, type = type, movie = media(id))
    } else {
        TraktListItem(listedAt = at, type = type, show = media(id))
    }

    @Test
    fun topPicksAreRecommendationsWithoutWatchedTitlesOrTitlesInContinueWatching() {
        val picks = TraktRules.picks(
            recommendedMovies = listOf(media(10), media(11)),
            recommendedShows = listOf(media(20), media(21)),
            seen = setOf(TitleKey(TitleKind.Movie, 11)),
            continueWatching = listOf(media(20).toTitle(TitleKind.Show)),
        )
        assertEquals(listOf(10, 21), picks.map { it.traktId })
        assertTrue(picks.all { it.source == PickSource.Recommended })
    }

    /** 24 Oct 2026: films and shows in turn, in Trakt's order, up to 30. */
    @Test
    fun topPicksTakeFilmsAndShowsInTurnUpToTheLimit() {
        val picks = TraktRules.picks(
            recommendedMovies = (100..119).map(::media),
            recommendedShows = (200..219).map(::media),
            seen = emptySet(),
            continueWatching = emptyList(),
        )
        assertEquals(TraktRules.PICKS_LIMIT, picks.size)
        assertEquals(listOf(100, 200, 101, 201), picks.take(4).map { it.traktId })
        assertEquals(listOf(TitleKind.Movie, TitleKind.Show).let { k -> List(15) { k }.flatten() }, picks.map { it.kind })
    }

    /** Seen on the TV, 24 Oct 2026: a hidden show put two films together. */
    @Test
    fun aHiddenTitleKeepsFilmsAndShowsInTurn() {
        val picks = TraktRules.picks(
            recommendedMovies = listOf(media(100), media(101), media(102)),
            recommendedShows = listOf(media(200), media(201), media(202)),
            seen = setOf(TitleKey(TitleKind.Show, 200)),
            continueWatching = emptyList(),
        )
        assertEquals(listOf(100, 201, 101, 202, 102), picks.map { it.traktId })
    }

    /** A watched film does not hide a show with the same Trakt id. */
    @Test
    fun aWatchedFilmDoesNotHideAShowWithTheSameId() {
        val picks = TraktRules.picks(
            recommendedMovies = emptyList(),
            recommendedShows = listOf(media(41)),
            seen = setOf(TitleKey(TitleKind.Movie, 41)),
            continueWatching = emptyList(),
        )
        assertEquals(listOf(TitleKey(TitleKind.Show, 41)), picks.map { it.key })
    }

    /** 24 Oct 2026: the watchlist row has films and shows, the most recently added first. */
    @Test
    fun theWatchlistRowPutsTheNewestFirst() {
        val row = TraktRules.watchlistRow(
            watchlist = listOf(
                listed(30, "2026-09-01T10:00:00.000Z"),
                listed(31, "2026-10-20T10:00:00.000Z", type = "show"),
                listed(32, "2026-10-01T10:00:00.000Z"),
            ),
            seen = emptySet(),
            continueWatching = emptyList(),
        )
        assertEquals(listOf(31, 32, 30), row.map { it.traktId })
        assertEquals(listOf(TitleKind.Show, TitleKind.Movie, TitleKind.Movie), row.map { it.kind })
        assertTrue(row.all { it.source == PickSource.Watchlist })
    }

    @Test
    fun theWatchlistRowHidesWatchedAndRatedTitlesAndTitlesInContinueWatching() {
        val row = TraktRules.watchlistRow(
            watchlist = (30..34).map { listed(it, "2026-10-${10 + it - 30}T10:00:00.000Z") },
            seen = setOf(TitleKey(TitleKind.Movie, 31), TitleKey(TitleKind.Movie, 33)),
            continueWatching = listOf(media(34).toTitle(TitleKind.Movie)),
        )
        assertEquals(listOf(32, 30), row.map { it.traktId })
    }

    @Test
    fun theWatchlistRowStopsAtItsLimit() {
        val row = TraktRules.watchlistRow((1..60).map { listed(it, "2026-10-01T10:00:00.000Z") }, emptySet(), emptyList())
        assertEquals(TraktRules.WATCHLIST_LIMIT, row.size)
    }

    @Test
    fun traktImagePathsBecomeHttpsAddresses() {
        val title = TraktMedia(
            title = "Fern",
            ids = TraktIds(trakt = 1),
            images = TraktImages(fanart = listOf("media.trakt.tv/images/shows/f.jpg.webp")),
        ).toTitle(TitleKind.Show)
        assertEquals("https://media.trakt.tv/images/shows/f.jpg.webp", title.images.fanart)
    }

    @Test
    fun aContinueWatchingEpisodeKeepsItsStillAndTheShowKeepsItsArt() {
        val item = TraktPlayback(
            progress = 40f,
            pausedAt = "2026-10-22T21:00:00.000Z",
            type = "episode",
            episode = TraktEpisode(5, 6, images = TraktImages(screenshot = listOf("media.trakt.tv/images/episodes/s.jpg.webp"))),
            show = TraktMedia(
                title = "Show",
                ids = TraktIds(trakt = 9),
                images = TraktImages(fanart = listOf("media.trakt.tv/f1.webp", "media.trakt.tv/f2.webp"), thumb = listOf("media.trakt.tv/t.webp")),
            ),
        )
        val images = TraktRules.continueWatching(listOf(item), now).single().images
        assertEquals("https://media.trakt.tv/images/episodes/s.jpg.webp", images.still)
        assertEquals("https://media.trakt.tv/f1.webp", images.fanart)
        assertEquals("https://media.trakt.tv/f2.webp", images.fanartAlt)
        assertEquals("https://media.trakt.tv/t.webp", images.thumb)
    }

    private fun titles(range: IntRange) = range.map { media(it).toTitle(TitleKind.Movie) }

    /** Option B (24 Oct 2026): fewer than 4 paused items fill up to 8 cards with the newest watchlist titles. */
    @Test
    fun aShortContinueWatchingRowFillsUpToEightCards() {
        assertEquals((20..26).toList(), TraktRules.continueFill(titles(1..1), titles(20..40)).map { it.traktId })
        assertEquals((20..24).toList(), TraktRules.continueFill(titles(1..3), titles(20..40)).map { it.traktId })
        assertEquals((20..21).toList(), TraktRules.continueFill(titles(1..2), titles(20..21)).map { it.traktId })
    }

    @Test
    fun fourPausedItemsOrNoneGiveNoFill() {
        assertEquals(emptyList<Title>(), TraktRules.continueFill(titles(1..4), titles(20..40)))
        assertEquals(emptyList<Title>(), TraktRules.continueFill(emptyList(), titles(20..40)))
    }

    /** A show's card keeps every paused episode's playback id, so Reset Progress can clear them all. */
    @Test
    fun aContinueWatchingCardKeepsThePlaybackIdsBehindIt() {
        val rows = TraktRules.continueWatching(
            listOf(
                episode(1, 5, 5, "2026-10-20T21:00:00.000Z").copy(id = 11),
                episode(1, 5, 6, "2026-10-22T21:00:00.000Z").copy(id = 12),
                movie(3, "2026-10-21T21:00:00.000Z").copy(id = 13),
            ),
            now,
        )
        assertEquals(listOf(listOf(12L, 11L), listOf(13L)), rows.map { it.playbackIds })
    }
}
