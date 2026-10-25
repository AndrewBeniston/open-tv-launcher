package app.opentvlauncher.home

import app.opentvlauncher.data.CardAction
import app.opentvlauncher.data.PosterRow
import app.opentvlauncher.data.SlideKind
import app.opentvlauncher.data.WideRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** The screen built from the saved HomeState (design, "Data behind each element"). */
class HomeMapperTest {
    /** 25 Oct 2026: the apps row leaves out the apps that the TV does not have, and keeps the order. */
    @Test
    fun theAppsRowHasOnlyTheTvsAppsThenAllApps() {
        val all = HomeMapper.apps.map { it.label }
        assertEquals(all, HomeMapper.appsRow(installed = null).map { it.label })
        assertEquals(listOf("YouTube", "Netflix", "BBC iPlayer", "All Apps"), HomeMapper.appsRow(setOf(YOUTUBE, NETFLIX, IPLAYER)).map { it.label })
        assertEquals(listOf("YouTube", "Netflix", "All Apps"), HomeMapper.appsRow(setOf(YOUTUBE, NETFLIX)).map { it.label })
        assertEquals(listOf("All Apps"), HomeMapper.appsRow(emptySet()).map { it.label })
        assertEquals(listOf("Stremio", "All Apps"), HomeMapper.content(HomeState(), installed = setOf(STREMIO)).apps.map { it.label })
    }

    private val london = ZoneId.of("Europe/London")
    private val saved = Instant.parse("2026-10-24T20:40:00Z").toEpochMilli()

    private fun show(n: Int) = Title(
        kind = TitleKind.Show,
        title = "Show $n",
        imdb = "tt$n",
        traktId = n,
        images = TitleImages(fanart = "https://media.trakt.tv/f$n.webp"),
        episode = EpisodeRef(5, 6, overview = "Episode overview"),
        progress = 0.47f,
        pausedAt = Instant.parse("2026-10-21T20:00:00Z").toEpochMilli(),
    )

    private fun pick(n: Int, source: PickSource = PickSource.Watchlist) =
        Title(kind = TitleKind.Movie, title = "Pick $n", year = 2023, imdb = "tt$n", traktId = n, genres = listOf("science-fiction"), source = source)

    /** 24 Oct 2026: Continue Watching first, then one Top Pick and one watchlist title in turn. */
    @Test
    fun theBannerShowsContinueWatchingThenTopPicksAndWatchlistTitlesInTurn() {
        val state = HomeState(
            trakt = TraktRows(
                saved,
                continueWatching = (1..4).map(::show),
                picks = (10..16).map { pick(it, PickSource.Recommended) },
                watchlistRow = (20..26).map { pick(it) },
            ),
        )
        val slides = HomeMapper.content(state, london).slides
        assertEquals(List(3) { SlideKind.ContinueWatching } + List(6) { SlideKind.TopPick }, slides.map { it.kind })
        assertEquals(listOf("Pick 10", "Pick 20", "Pick 11", "Pick 21", "Pick 12", "Pick 22"), slides.drop(3).map { it.title })
        assertEquals("Season 5, Episode 6, Paused 21 Oct", slides[0].meta)
        assertEquals("Episode overview", slides[0].synopsis)
        assertEquals("Science Fiction film from 2023", slides[3].meta)
        assertEquals("Top Pick for You", slides[3].eyebrow)
        assertEquals("From Your Watchlist", slides[4].eyebrow)
    }

    /**
     * 25 Oct 2026: the banner's and the screensaver's line about a title is a short
     * sentence of its genre, type and year.
     */
    @Test
    fun theKindLineIsAShortSentence() {
        fun line(kind: TitleKind, year: Int?, vararg genres: String) =
            HomeMapper.kindLine(Title(kind = kind, title = "T", year = year, genres = genres.toList()))
        assertEquals("Horror film from 2018", line(TitleKind.Movie, 2018, "horror", "thriller"))
        assertEquals("Drama series from 2012", line(TitleKind.Show, 2012, "drama"))
        assertEquals("Drama series from 2026", line(TitleKind.Show, 2026, "drama"))
        assertEquals("Horror film", line(TitleKind.Movie, null, "horror"))
        assertEquals("Film from 2018", line(TitleKind.Movie, 2018))
        assertEquals("Series from 2012", line(TitleKind.Show, 2012))
        assertEquals("Film", line(TitleKind.Movie, null))
        assertEquals("Series", line(TitleKind.Show, null))
    }

    /**
     * The text audit for the middle dot (25 Oct 2026). It shows only in the meta lines
     * under cards and posters. The banner's lines, the Continue Watching slide's episode line among
     * them, have none; the screensaver's line is the banner's sentence (ScreensaverTitlesTest).
     */
    @Test
    fun theMiddleDotShowsOnlyInTheAllowedLines() {
        val state = HomeState(
            trakt = TraktRows(
                saved,
                continueWatching = (1..2).map(::show),
                picks = (10..12).map { pick(it, PickSource.Recommended) },
                watchlistRow = (20..22).map { pick(it) },
            ),
            traktFailedAt = saved,
        )
        val dot = " · "
        // Home, and the Movies and Shows pages.
        val pages = listOf(HomeMapper.content(state, london)) + TitleKind.entries.map { HomeMapper.content(state, london, kind = it) }
        pages.forEach { page ->
            page.slides.forEach { slide ->
                listOf(slide.eyebrow, slide.title, slide.synopsis).forEach { assertEquals(it, false, dot in it) }
                assertEquals(slide.meta, false, dot in slide.meta)
            }
            page.rows.forEach { row ->
                assertEquals(row.title, false, dot in row.title)
                when (row) {
                    is WideRow -> row.cards.forEach { assertEquals(it.title, false, dot in it.title) }
                    is PosterRow -> row.posters.forEach { poster ->
                        assertEquals(poster.title, false, dot in poster.title)
                        assertEquals(poster.meta, true, dot in poster.meta)
                    }
                }
            }
            page.status?.let { assertEquals(it, false, dot in it) }
        }
    }

    /** Two films with the same name are two slides: a slide is known by its Trakt id, not its title. */
    @Test
    fun slidesWithTheSameNameHaveTheirOwnKeys() {
        val sameName = listOf(pick(10, PickSource.Recommended), pick(11, PickSource.Recommended)).map { it.copy(title = "The Same Name") }
        val slides = HomeMapper.content(HomeState(trakt = TraktRows(saved, picks = sameName)), london).slides
        assertEquals(listOf("TopPick:Movie:10", "TopPick:Movie:11"), slides.map { it.key })
    }

    @Test
    fun aStremioSlideIsKnownByItsLink() {
        val video = Video("A Film", intentUri = "stremio:///detail/movie/tt1")
        assertEquals("ContinueWatching:stremio:///detail/movie/tt1", HomeMapper.content(HomeState(stremio = listOf(video)), london).slides.single().key)
    }

    @Test
    fun aShortRowLeavesTheOtherRowsSlidesInTheBanner() {
        val state = HomeState(trakt = TraktRows(saved, picks = listOf(pick(10, PickSource.Recommended)), watchlistRow = (20..23).map { pick(it) }))
        assertEquals(listOf("Pick 10", "Pick 20", "Pick 21", "Pick 22"), HomeMapper.content(state, london).slides.map { it.title })
    }

    @Test
    fun artTriesMetahubFirstThenTrakt() {
        val slide = HomeMapper.content(HomeState(trakt = TraktRows(saved, continueWatching = listOf(show(7)))), london).slides.first()
        assertEquals(
            listOf("https://images.metahub.space/background/medium/tt7/img", "https://media.trakt.tv/f7.webp"),
            slide.art.urls,
        )
    }

    @Test
    fun rowsAreContinueWatchingTopPicksTheWatchlistAndYouTubeAndAnEmptyRowIsLeftOut() {
        val full = HomeMapper.content(
            HomeState(
                trakt = TraktRows(
                    saved,
                    continueWatching = (1..4).map(::show),
                    picks = listOf(pick(10, PickSource.Recommended)),
                    watchlistRow = listOf(pick(11)),
                ),
                youtube = listOf(Video("A video", "A channel", durationMillis = 1_595_000, intentUri = "https://youtu.be/x")),
            ),
            london,
        )
        assertEquals(
            listOf("Continue Watching", "Recommended on YouTube", "Top Picks for You", "From Your Watchlist"),
            full.rows.map { it.title },
        )
        assertEquals(listOf("continue-watching", "youtube", "top-picks", "watchlist"), full.rows.map { it.key })
        assertEquals("S5 E6 · 47% Watched", (full.rows[0] as WideRow).cards[0].meta)
        assertEquals("2023 · Science Fiction", (full.rows[2] as PosterRow).posters[0].meta)
        assertEquals(listOf("Pick 11"), (full.rows[3] as PosterRow).posters.map { it.title })
        val video = (full.rows[1] as WideRow).cards[0]
        assertEquals("26:35", video.duration)
        assertEquals(CardAction.OpenVideo("https://youtu.be/x"), video.action)

        val nothingInProgress = HomeMapper.content(HomeState(trakt = TraktRows(saved, picks = listOf(pick(2, PickSource.Recommended)))), london)
        assertEquals(listOf("Top Picks for You"), nothingInProgress.rows.map { it.title })
        assertEquals(listOf(SlideKind.TopPick), nothingInProgress.slides.map { it.kind })
    }

    /** 25 Oct 2026: an up-next card says which episode is next, and has no progress bar. */
    @Test
    fun anUpNextCardAndSlideSayUpNextWithNoProgress() {
        val next = show(1).copy(episode = EpisodeRef(1, 5, overview = "Next overview"), progress = null, pausedAt = null, upNextAt = saved)
        val content = HomeMapper.content(HomeState(trakt = TraktRows(saved, continueWatching = listOf(next, show(2)))), london)
        val cards = (content.rows[0] as WideRow).cards
        assertEquals(listOf("S1 E5 · Up next", "S5 E6 · 47% Watched"), cards.map { it.meta })
        assertEquals(null, cards[0].progress)
        val slide = content.slides[0]
        assertEquals("Continue Watching", slide.eyebrow)
        assertEquals("Season 1, Episode 5, Up next", slide.meta)
        assertEquals("Next overview", slide.synopsis)
        assertEquals(null, slide.progress)
        assertEquals(CardAction.OpenTitle(next), slide.action)
    }

    @Test
    fun withoutTraktRowsStremiosOwnContinueWatchingIsUsed() {
        val content = HomeMapper.content(HomeState(stremio = listOf(Video("Fern", "S2 E8", progress = 0.11f, intentUri = "stremio:///x"))), london)
        assertEquals("Continue Watching", content.rows.single().title)
        assertEquals("Continue Watching", content.slides.single().eyebrow)
    }

    @Test
    fun theChipSaysWhenTheSavedCopyIsFromOrThatThereIsNone() {
        val failedAt = Instant.parse("2026-10-24T21:46:00Z").toEpochMilli()
        assertEquals(
            "Trakt Unavailable. Saved at 21:40",
            HomeMapper.content(HomeState(trakt = TraktRows(saved), traktFailedAt = failedAt), london).status,
        )
        assertEquals("Trakt Unavailable. No Saved Data.", HomeMapper.content(HomeState(traktFailedAt = failedAt), london).status)
        assertNull(HomeMapper.content(HomeState(trakt = TraktRows(saved)), london).status)
    }

    /** 24 Oct 2026: the banner and the card must not show the same picture. */
    @Test
    fun aContinueWatchingCardShowsADifferentPictureFromTheBanner() {
        val withStill = show(8).copy(images = TitleImages(fanart = "https://trakt/f8", still = "https://trakt/still8", thumb = "https://trakt/thumb8"))
        val content = HomeMapper.content(HomeState(trakt = TraktRows(saved, continueWatching = listOf(withStill))), london)
        val banner = content.slides.first().art.urls
        val card = (content.rows.first() as WideRow).cards.first().art.urls
        assertEquals("https://images.metahub.space/background/medium/tt8/img", banner.first())
        assertEquals("https://trakt/still8", card.first())
        assertEquals("https://trakt/thumb8", card[1])
    }

    @Test
    fun aStremioBannerLeavesOutTheCardsPicture() {
        val video = Video("Delta Duke", image = "https://stremio/thumb.jpg", poster = "https://stremio/poster.jpg", imdb = "tt9100001")
        assertEquals(
            listOf("https://images.metahub.space/background/medium/tt9100001/img", "https://stremio/poster.jpg", "https://stremio/thumb.jpg"),
            HomeMapper.stremioBackdrop(video).urls,
        )
        val metahubThumb = Video("Delta Duke", image = "https://images.metahub.space/background/small/tt9100001/img", imdb = "tt9100001")
        assertEquals(listOf("https://images.metahub.space/background/small/tt9100001/img"), HomeMapper.stremioBackdrop(metahubThumb).urls)
    }

    /** Metahub's logo can be in another language, so only Trakt's is used. None means the title as text. */
    @Test
    fun theLogoIsTraktsOnly() {
        val title = pick(3).copy(images = TitleImages(logo = "https://trakt/logo3"))
        assertEquals(listOf("https://trakt/logo3"), title.logoArt().urls)
        assertEquals(emptyList<String>(), pick(3).logoArt().urls)
    }

    /** Option B (24 Oct 2026): a short Continue Watching row goes on with the newest watchlist titles. */
    @Test
    fun aShortContinueWatchingRowFillsWithWatchlistTitles() {
        val state = HomeState(trakt = TraktRows(saved, continueWatching = (1..2).map(::show), watchlistRow = (20..29).map { pick(it) }))
        val rows = HomeMapper.content(state, london).rows
        val continueWatching = rows[0] as WideRow
        assertEquals(listOf("Show 1", "Show 2") + (20..25).map { "Pick $it" }, continueWatching.cards.map { it.title })
        assertEquals(2, continueWatching.fillStart)
        assertEquals("2023 · Science Fiction", continueWatching.cards[2].meta)
        assertNull(continueWatching.cards[2].progress)
        // From Your Watchlist starts after the fill, so no title shows twice.
        assertEquals((26..29).map { "Pick $it" }, (rows[1] as PosterRow).posters.map { it.title })
        // The banner keeps its paused slides only.
        assertEquals(2, HomeMapper.content(state, london).slides.count { it.kind == SlideKind.ContinueWatching })
    }

    @Test
    fun aFullContinueWatchingRowDoesNotFill() {
        val state = HomeState(trakt = TraktRows(saved, continueWatching = (1..4).map(::show), watchlistRow = (20..29).map { pick(it) }))
        val rows = HomeMapper.content(state, london).rows
        assertEquals(4, (rows[0] as WideRow).cards.size)
        assertNull((rows[0] as WideRow).fillStart)
        assertEquals(10, (rows[1] as PosterRow).posters.size)
    }

    /** 24 Oct 2026: with every paused item cleared, the row goes. The watchlist does not keep it. */
    @Test
    fun withNothingPausedTheContinueWatchingRowGoes() {
        val state = HomeState(trakt = TraktRows(saved, watchlistRow = (20..29).map { pick(it) }))
        val content = HomeMapper.content(state, london)
        assertEquals(listOf("From Your Watchlist"), content.rows.map { it.title })
        assertEquals(10, (content.rows[0] as PosterRow).posters.size)
        assertEquals(0, content.slides.count { it.kind == SlideKind.ContinueWatching })
    }

    /** Up Next exploration, "What each tab shows": Movies and Shows filter the same lists, with no apps or YouTube row. */
    @Test
    fun moviesAndShowsFilterTheSameLists() {
        val showPick = show(12).copy(title = "Show Pick", episode = null, progress = null, source = PickSource.Recommended)
        val state = HomeState(
            trakt = TraktRows(
                saved,
                continueWatching = listOf(show(1), pick(2).copy(title = "Paused Film", progress = 0.3f)),
                picks = listOf(pick(10, PickSource.Recommended), showPick),
                watchlistRow = (20..29).map { pick(it) },
            ),
            youtube = listOf(Video("A video", "A channel", intentUri = "https://youtu.be/x")),
        )
        val movies = HomeMapper.content(state, london, kind = TitleKind.Movie)
        assertEquals(emptyList<Any>(), movies.apps)
        assertEquals(listOf("Continue Watching", "Top Picks for You", "From Your Watchlist"), movies.rows.map { it.title })
        val continueWatching = movies.rows[0] as WideRow
        // The short row fills from the film watchlist only.
        assertEquals(listOf("Paused Film") + (20..26).map { "Pick $it" }, continueWatching.cards.map { it.title })
        assertEquals(listOf("Pick 10"), (movies.rows[1] as PosterRow).posters.map { it.title })
        assertEquals(listOf("Paused Film", "Pick 10", "Pick 20", "Pick 21", "Pick 22"), movies.slides.map { it.title })

        val shows = HomeMapper.content(state, london, kind = TitleKind.Show)
        assertEquals(listOf("Continue Watching", "Top Picks for You"), shows.rows.map { it.title })
        assertEquals(listOf("Show 1"), (shows.rows[0] as WideRow).cards.map { it.title })
        assertEquals(listOf("Show 1", "Show Pick"), shows.slides.map { it.title })
    }

    @Test
    fun withoutTraktRowsMoviesAndShowsAreEmpty() {
        val state = HomeState(stremio = listOf(Video("Fern", "S2 E8", progress = 0.11f, intentUri = "stremio:///x")))
        TitleKind.entries.forEach { kind ->
            val content = HomeMapper.content(state, london, kind = kind)
            assertEquals(emptyList<Any>(), content.rows)
            assertEquals(emptyList<Any>(), content.slides)
        }
    }

    private companion object {
        const val YOUTUBE = "com.google.android.youtube.tv"
        const val STREMIO = "com.stremio.one"
        const val NETFLIX = "com.netflix.ninja"
        const val IPLAYER = "bbc.iplayer.android"
    }
}
