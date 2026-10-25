package app.opentvlauncher.home

import app.opentvlauncher.data.CONTINUE_WATCHING_ROW
import app.opentvlauncher.data.SlideKind
import app.opentvlauncher.home.ChangeResult.Done
import app.opentvlauncher.home.ChangeResult.Failed
import app.opentvlauncher.home.ChangeResult.NothingToRemove
import app.opentvlauncher.trakt.TokenResponse
import app.opentvlauncher.trakt.TraktRowsSource
import app.opentvlauncher.trakt.TraktSession
import app.opentvlauncher.trakt.historyAdded
import app.opentvlauncher.trakt.historyRemoved
import app.opentvlauncher.trakt.json
import app.opentvlauncher.trakt.tempStore
import app.opentvlauncher.trakt.testApi
import app.opentvlauncher.tv.TvChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Continue Watching menu's Trakt writes (24 Oct 2026): what each one sends, what the
 * saved rows do after it, and the session generation rule.
 */
class TitleChangeTest {
    private val server = MockWebServer()
    private var now = 1_800_000_000_000L
    private lateinit var file: File
    private lateinit var session: TraktSession

    /** Each request as "METHOD /path body". */
    private val sent = CopyOnWriteArrayList<String>()

    /** The play's time: the repository's clock, to the second. */
    private val watchedAt = "2027-01-15T08:00:00Z"

    /** Trakt's answers: 204 with no body for a DELETE, the counted plays for a history add or remove, 201 for another POST. */
    private val traktAnswer: (RecordedRequest) -> MockResponse = {
        when {
            it.method == "DELETE" -> MockResponse.Builder().code(204).build()
            it.url.encodedPath == "/sync/history" -> historyAdded(movies = 1, episodes = 1)
            it.url.encodedPath == "/sync/history/remove" -> historyRemoved(movies = 1, episodes = 1)
            else -> json(201)
        }
    }
    private var answer: (RecordedRequest) -> MockResponse = traktAnswer

    private val film = Title(kind = TitleKind.Movie, title = "Harbour Lights", traktId = 1, progress = 0.53f, playbackIds = listOf(70))
    private val episode = Title(
        kind = TitleKind.Show,
        title = "Kitchen Council",
        traktId = 2,
        episode = EpisodeRef(1, 4),
        progress = 0.2f,
        playbackIds = listOf(80, 81),
    )

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                sent += "${request.method} ${request.url.encodedPath} ${request.body?.utf8().orEmpty()}".trim()
                return answer(request)
            }
        }
        server.start()
        file = File(Files.createTempDirectory("home").toFile(), "home-state.json")
        HomeStateStore(file).write(
            HomeState(
                trakt = TraktRows(
                    savedAt = now,
                    continueWatching = listOf(film, episode),
                    watchlistKeys = setOf(TitleKey(TitleKind.Movie, 1)),
                ),
            ),
        )
        session = TraktSession(tempStore(), testApi(server)) { now }
        runBlocking { session.save(TokenResponse("access", "refresh", 86_400, now / 1000)) }
    }

    @After
    fun tearDown() = server.close()

    private fun repository() = HomeRepository(
        store = HomeStateStore(file),
        trakt = TraktRowsSource(testApi(server), session),
        session = session,
        tvRows = { TvChannels.Rows(emptyList(), emptyList()) },
        now = { now },
    )

    private fun HomeRepository.paused() = state.value.trakt!!.continueWatching.map { it.title }

    @Test
    fun markAsWatchedClearsTheProgressThenAddsAPlay() = runBlocking {
        val repository = repository()
        assertEquals(Done, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(
            listOf("DELETE /sync/playback/70", """POST /sync/history {"movies":[{"ids":{"trakt":1},"watched_at":"$watchedAt"}]}"""),
            sent.toList(),
        )
        assertEquals(listOf("Kitchen Council"), repository.paused())
        assertTrue(TitleKey(TitleKind.Movie, 1) in repository.state.value.trakt!!.seen)
    }

    /** An episode is named by its show, season and number. Every paused episode of the show is cleared. */
    @Test
    fun markAsWatchedNamesTheEpisode() = runBlocking {
        val repository = repository()
        assertEquals(Done, repository.change(episode, TitleChange.MarkWatched, session.generation.value))
        assertEquals(
            listOf(
                "DELETE /sync/playback/80",
                "DELETE /sync/playback/81",
                """POST /sync/history {"shows":[{"ids":{"trakt":2},"seasons":[{"number":1,"episodes":[{"number":4,"watched_at":"$watchedAt"}]}]}]}""",
            ),
            sent.toList(),
        )
        assertEquals(listOf("Harbour Lights"), repository.paused())
    }

    /**
     * Trakt after the play: Kitchen Council's next episode is [kitchenNext] (S1E5 once Trakt has
     * the play, still S1E4 while it lags), and Northway is up next as well.
     */
    private fun upNextAfterThePlay(kitchenNext: Int): (RecordedRequest) -> MockResponse = { request ->
        fun next(id: Int, title: String, number: Int) =
            """{"show":{"title":"$title","ids":{"trakt":$id}},"progress":{"last_watched_at":"2027-01-15T07:00:00.000Z",""" +
                """"next_episode":{"season":1,"number":$number,"ids":{"trakt":${id * 100}}}}}"""
        when {
            request.method != "GET" -> traktAnswer(request)
            request.url.encodedPath == "/sync/progress/up_next" ->
                json(200, listOf(next(2, "Kitchen Council", kitchenNext), next(9, "Northway", 9)).joinToString(",", "[", "]"))
            else -> json(200, "[]")
        }
    }

    /** Marks the paused Kitchen Council S1E4 watched from the menu, and waits for the refresh that follows to show Northway. */
    private fun markedFromTheMenu(kitchenNext: Int): List<Title> = runBlocking {
        answer = upNextAfterThePlay(kitchenNext)
        val repository = repository()
        val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(thread)
        try {
            assertTrue(TitleChanges(scope, repository).send(episode, TitleChange.MarkWatched, session.generation.value))
            withTimeout(5_000) { repository.state.first { state -> state.trakt?.continueWatching.orEmpty().any { it.title == "Northway" } } }
                .trakt!!.continueWatching
        } finally {
            scope.cancel()
            thread.close()
        }
    }

    /** 25 Oct 2026: after Mark as Watched on an episode, a refresh brings in the show's next episode. */
    @Test
    fun markAsWatchedOnAnEpisodeBringsInItsNextEpisode() {
        val kitchen = markedFromTheMenu(kitchenNext = 5).single { it.title == "Kitchen Council" }
        assertEquals(EpisodeRef(1, 5), kitchen.episode)
        assertTrue(kitchen.upNextAt != null && kitchen.progress == null)
    }

    /** While Trakt still names the episode just marked watched as next, its card does not come back. */
    @Test
    fun theEpisodeJustMarkedWatchedDoesNotComeBackAsUpNext() {
        assertEquals(listOf("Northway"), markedFromTheMenu(kitchenNext = 4).map { it.title })
    }

    private val addPlay = """POST /sync/history {"movies":[{"ids":{"trakt":1},"watched_at":"$watchedAt"}]}"""

    /** One page of Trakt's history, with the page count Trakt sends with it. */
    private fun historyPage(body: String, pages: String? = "1") =
        MockResponse.Builder().code(200).body(body).apply { if (pages != null) addHeader("X-Pagination-Page-Count", pages) }.build()

    /** Trakt's history for the film, with the plays it has taken. */
    private fun history(plays: Int) = historyPage(List(plays) { """{"watched_at":"2027-01-15T08:00:00.000Z","type":"movie"}""" }.joinToString(",", "[", "]"))

    /** Review O5: the play reached Trakt but its answer was lost. The retry finds it and sends nothing. */
    @Test
    fun aPlayWhoseAnswerWasLostIsNotSentAgain() = runBlocking {
        val repository = repository()
        var taken = 0
        answer = {
            when {
                it.url.encodedPath == "/sync/history" -> {
                    taken++
                    if (taken == 1) json(502) else historyAdded(movies = 1)
                }
                it.url.encodedPath.startsWith("/sync/history/movies/1") -> history(taken)
                else -> traktAnswer(it)
            }
        }
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        // The progress is cleared and the play is unclear: the card has gone, and the menu offers a retry.
        assertEquals(listOf("Kitchen Council"), repository.paused())
        assertEquals(Done, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(1, taken)
        assertEquals(1, sent.count { it == addPlay })
        assertTrue(sent.any { it.startsWith("GET /sync/history/movies/1") })
        assertTrue(TitleKey(TitleKind.Movie, 1) in repository.state.value.trakt!!.seen)
    }

    /** The play did not reach Trakt: the retry finds no play and sends it again, at the same time. */
    @Test
    fun aPlayTraktDidNotTakeIsSentAgainAtTheSameTime() = runBlocking {
        val repository = repository()
        var refused = true
        answer = {
            when {
                it.url.encodedPath == "/sync/history" -> if (refused) json(503) else historyAdded(movies = 1)
                it.url.encodedPath.startsWith("/sync/history/movies/1") -> history(0)
                it.method == "DELETE" -> json(404)
                else -> traktAnswer(it)
            }
        }
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        refused = false
        now += 60_000
        assertEquals(Done, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(2, sent.count { it == addPlay })
    }

    /** When Trakt's history cannot be read, the retry sends nothing and fails. */
    @Test
    fun aRetryThatCannotCheckTheHistorySendsNothing() = runBlocking {
        val repository = repository()
        answer = { if (it.url.encodedPath == "/sync/history" || it.method == "GET") json(502) else traktAnswer(it) }
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(1, sent.count { it == addPlay })
    }

    /** A history lookup Trakt refuses (404) sends nothing more. */
    @Test
    fun aRefusedHistoryLookupSendsNothing() = runBlocking {
        val repository = repository()
        var accepted = 0
        answer = {
            when {
                it.url.encodedPath == "/sync/history" -> {
                    accepted++
                    json(502)
                }
                it.method == "GET" -> json(404)
                else -> traktAnswer(it)
            }
        }
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(1, accepted)
    }

    /** Review N3: the play that reached Trakt is on the history's second page. The retry reads it and sends nothing. */
    @Test
    fun aPlayOnTheHistorysSecondPageIsFound() = runBlocking {
        val repository = repository()
        var accepted = 0
        answer = {
            when {
                it.url.encodedPath == "/sync/history" -> {
                    accepted++
                    if (accepted == 1) json(502) else historyAdded(episodes = 1)
                }
                it.url.encodedPath == "/sync/history/shows/2" -> {
                    val body = if (it.url.queryParameter("page") == "2") {
                        """[{"watched_at":"$watchedAt","type":"episode","episode":{"season":1,"number":4}}]"""
                    } else {
                        (5..14).joinToString(",", "[", "]") { number -> """{"watched_at":"$watchedAt","type":"episode","episode":{"season":1,"number":$number}}""" }
                    }
                    MockResponse.Builder().code(200).body(body).addHeader("X-Pagination-Page-Count", "2").build()
                }
                else -> traktAnswer(it)
            }
        }
        assertEquals(Failed, repository.change(episode, TitleChange.MarkWatched, session.generation.value))
        assertEquals(Done, repository.change(episode, TitleChange.MarkWatched, session.generation.value))
        assertEquals(1, accepted)
    }

    /** Review N3: a play in the history without its time cannot show that the play is missing. */
    @Test
    fun anIncompleteHistoryRecordSendsNothing() = runBlocking {
        val repository = repository()
        var accepted = 0
        answer = {
            when {
                it.url.encodedPath == "/sync/history" -> {
                    accepted++
                    json(502)
                }
                it.method == "GET" -> historyPage("""[{"type":"movie"}]""")
                else -> traktAnswer(it)
            }
        }
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(1, accepted)
    }

    /** Review N3: a history too long to read in full sends nothing. */
    @Test
    fun aHistoryTooLongToReadSendsNothing() = runBlocking {
        val repository = repository()
        var accepted = 0
        answer = {
            when {
                it.url.encodedPath == "/sync/history" -> {
                    accepted++
                    json(502)
                }
                it.method == "GET" -> MockResponse.Builder().code(200).body("[]").addHeader("X-Pagination-Page-Count", "11").build()
                else -> traktAnswer(it)
            }
        }
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(1, accepted)
    }

    /** Review Q3: page 2 of 3 comes without its page count. The history is unknown, not two pages long. */
    @Test
    fun aPageWithoutItsCountDoesNotEndTheHistory() = runBlocking {
        val repository = repository()
        var posts = 0
        val read = mutableListOf<Int>()
        answer = {
            when {
                it.url.encodedPath == "/sync/history" -> {
                    posts++
                    if (posts == 1) json(502) else historyAdded(movies = 1)
                }
                it.method == "GET" -> {
                    val page = it.url.queryParameter("page")!!.toInt()
                    read += page
                    historyPage(if (page == 3) """[{"watched_at":"$watchedAt","type":"movie"}]""" else "[]", pages = if (page == 2) null else "3")
                }
                else -> traktAnswer(it)
            }
        }
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals("read $read", 1, posts)
    }

    /** Review Q3: a page count too large for an Int is unreadable, not one page. */
    @Test
    fun aPageCountTooLargeToReadSendsNothing() = runBlocking {
        val repository = repository()
        var posts = 0
        answer = {
            when {
                it.url.encodedPath == "/sync/history" -> {
                    posts++
                    json(502)
                }
                it.method == "GET" -> historyPage("[]", pages = "2147483648")
                else -> traktAnswer(it)
            }
        }
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(1, posts)
    }

    /** Review Q3: a page count that changes between pages leaves the history unknown. */
    @Test
    fun aPageCountThatChangesSendsNothing() = runBlocking {
        val repository = repository()
        var posts = 0
        answer = {
            when {
                it.url.encodedPath == "/sync/history" -> {
                    posts++
                    json(502)
                }
                it.method == "GET" -> historyPage("[]", pages = if (it.url.queryParameter("page") == "1") "3" else "2")
                else -> traktAnswer(it)
            }
        }
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(1, posts)
    }

    /** The tenth page is read. A failed page before it sends nothing; a later full read finds the play. */
    @Test
    fun theTenthPageIsReadAndAFailedPageSendsNothing() = runBlocking {
        val repository = repository()
        var posts = 0
        val pages = mutableListOf<Int>()
        var failPageSix = true
        answer = {
            when {
                it.url.encodedPath == "/sync/history" -> {
                    posts++
                    json(502)
                }
                it.method == "GET" -> {
                    val page = it.url.queryParameter("page")!!.toInt()
                    pages += page
                    if (page == 6 && failPageSix) {
                        json(503)
                    } else {
                        historyPage(if (page == 10) """[{"watched_at":"$watchedAt","type":"movie"}]""" else "[]", pages = "10")
                    }
                }
                else -> traktAnswer(it)
            }
        }
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals((1..6).toList(), pages)
        assertEquals(1, posts)
        pages.clear()
        failPageSix = false
        assertEquals(Done, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals((1..10).toList(), pages)
        assertEquals(1, posts)
    }

    /** Review Q1: the old sign-in's DELETE ends after the new sign-in's unclear play. It must not erase that play. */
    @Test
    fun aLateRequestFromTheOldSignInLeavesTheNewSignInsUnclearPlay() = runBlocking {
        val repository = repository()
        val oldGeneration = session.generation.value
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val posts = AtomicInteger()
        answer = { request ->
            when {
                request.method == "DELETE" && request.headers["Authorization"] == "Bearer access" -> {
                    entered.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    MockResponse.Builder().code(204).build()
                }
                request.url.encodedPath == "/sync/history" -> if (posts.incrementAndGet() == 1) json(502) else historyAdded(movies = 1)
                request.method == "GET" -> history(1)
                else -> traktAnswer(request)
            }
        }
        val old = async(Dispatchers.IO) { repository.change(film, TitleChange.MarkWatched, oldGeneration) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            session.save(TokenResponse("second", "second-refresh", 86_400, now / 1000))
            val current = session.generation.value
            val newFilm = film.copy(playbackIds = emptyList())
            assertEquals(Failed, repository.change(newFilm, TitleChange.MarkWatched, current))
            assertEquals(1, posts.get())
            release.countDown()
            assertEquals(Failed, old.await())
            assertEquals(Done, repository.change(newFilm, TitleChange.MarkWatched, current))
            assertEquals(1, posts.get())
        } finally {
            release.countDown()
            old.cancelAndJoin()
        }
    }

    /** Review N4: after a new sign-in, a play for the same film uses its own time, not the old account's. */
    @Test
    fun aNewSignInDoesNotUseAnotherAccountsUnclearPlay() = runBlocking {
        val repository = repository()
        answer = { if (it.url.encodedPath == "/sync/history") json(502) else traktAnswer(it) }
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        now += 3_600_000
        session.save(TokenResponse("second", "second-refresh", 86_400, now / 1000))
        sent.clear()
        answer = { if (it.method == "GET") historyPage("[]") else traktAnswer(it) }
        assertEquals(Done, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertEquals(listOf("""POST /sync/history {"movies":[{"ids":{"trakt":1},"watched_at":"2027-01-15T09:00:00Z"}]}"""), sent.filter { it.startsWith("POST /sync/history ") })
        // The new account's history was never asked about the old account's play.
        assertTrue(sent.none { it.startsWith("GET") })
    }

    /** Review O6: HTTP 200 with the film under not_found adds nothing, so it is not watched. */
    @Test
    fun aTitleTraktDoesNotKnowIsNotWatched() = runBlocking {
        val repository = repository()
        answer = {
            if (it.url.encodedPath == "/sync/history") {
                json(
                    200,
                    """{"added":{"movies":0,"episodes":0},"not_found":{"movies":[{"ids":{"trakt":1}}],"shows":[],"seasons":[],"episodes":[]}}""",
                )
            } else {
                traktAnswer(it)
            }
        }
        assertEquals(Failed, repository.change(film, TitleChange.MarkWatched, session.generation.value))
        assertFalse(TitleKey(TitleKind.Movie, 1) in repository.state.value.trakt!!.seen)
    }

    @Test
    fun resetProgressClearsEveryPausedItemBehindTheCard() = runBlocking {
        val repository = repository()
        assertEquals(Done, repository.change(episode, TitleChange.ResetProgress, session.generation.value))
        assertEquals(listOf("DELETE /sync/playback/80", "DELETE /sync/playback/81"), sent.toList())
        assertEquals(listOf("Harbour Lights"), repository.paused())
    }

    @Test
    fun aRefusedResetKeepsTheCard() = runBlocking {
        val repository = repository()
        answer = { json(500) }
        assertEquals(Failed, repository.change(film, TitleChange.ResetProgress, session.generation.value))
        assertEquals(listOf("Harbour Lights", "Kitchen Council"), repository.paused())
    }

    @Test
    fun markAsUnwatchedRemovesThePlaysAndKeepsTheCard() = runBlocking {
        val repository = repository()
        assertEquals(Done, repository.change(film, TitleChange.MarkUnwatched, session.generation.value))
        assertEquals(listOf("""POST /sync/history/remove {"movies":[{"ids":{"trakt":1}}]}"""), sent.toList())
        assertEquals(listOf("Harbour Lights", "Kitchen Council"), repository.paused())
    }

    /** A report from the TV (The Glacier): a title with no plays has nothing to remove, and the menu says so. */
    @Test
    fun markAsUnwatchedWithNoPlaysRemovesNothing() = runBlocking {
        val repository = repository()
        answer = { if (it.url.encodedPath == "/sync/history/remove") historyRemoved() else traktAnswer(it) }
        assertEquals(NothingToRemove, repository.change(film, TitleChange.MarkUnwatched, session.generation.value))
        assertEquals(listOf("""POST /sync/history/remove {"movies":[{"ids":{"trakt":1}}]}"""), sent.toList())
    }

    /** A title Trakt does not know, or an answer that cannot be read, is a failure the menu offers to retry. */
    @Test
    fun markAsUnwatchedFailsForAnUnknownTitleOrAnUnreadableAnswer() = runBlocking {
        val repository = repository()
        answer = {
            if (it.url.encodedPath == "/sync/history/remove") {
                json(200, """{"deleted":{"movies":0,"episodes":0},"not_found":{"movies":[{"ids":{"trakt":1}}],"shows":[],"seasons":[],"episodes":[]}}""")
            } else {
                traktAnswer(it)
            }
        }
        assertEquals(Failed, repository.change(film, TitleChange.MarkUnwatched, session.generation.value))
        answer = { if (it.url.encodedPath == "/sync/history/remove") json(200, "not json") else traktAnswer(it) }
        assertEquals(Failed, repository.change(film, TitleChange.MarkUnwatched, session.generation.value))
    }

    /** Review 1cf623b, L3: an answer without the film's count does not show that there were no plays. */
    @Test
    fun aRemovalAnswerWithoutACountIsAFailure() = runBlocking {
        val repository = repository()
        listOf("{}", """{"deleted":{}}""", """{"deleted":{"episodes":1}}""", """{"deleted":{"movies":-1}}""").forEach { body ->
            answer = { if (it.url.encodedPath == "/sync/history/remove") json(200, body) else traktAnswer(it) }
            assertEquals(body, Failed, repository.change(film, TitleChange.MarkUnwatched, session.generation.value))
        }
        // The episode's own count decides for an episode.
        answer = { if (it.url.encodedPath == "/sync/history/remove") json(200, """{"deleted":{"movies":2}}""") else traktAnswer(it) }
        assertEquals(Failed, repository.change(episode, TitleChange.MarkUnwatched, session.generation.value))
        answer = { if (it.url.encodedPath == "/sync/history/remove") json(200, """{"deleted":{"episodes":1}}""") else traktAnswer(it) }
        assertEquals(Done, repository.change(episode, TitleChange.MarkUnwatched, session.generation.value))
    }

    @Test
    fun removeFromWatchlistUsesTheWatchlist() = runBlocking {
        val repository = repository()
        assertEquals(Done, repository.change(film, TitleChange.RemoveFromWatchlist, session.generation.value))
        assertEquals(listOf("""POST /sync/watchlist/remove {"movies":[{"ids":{"trakt":1}}]}"""), sent.toList())
        assertTrue(repository.state.value.trakt!!.watchlistKeys.isEmpty())
    }

    /** A change asked for before a sign-out and a new sign-in sends nothing and changes nothing. */
    @Test
    fun aChangeFromAnEarlierSessionSendsNothing() = runBlocking {
        val repository = repository()
        val asked = session.generation.value
        session.signOut()
        session.save(TokenResponse("access-2", "refresh-2", 86_400, now / 1000))
        TitleChange.entries.forEach { assertEquals(it.name, Failed, repository.change(film, it, asked)) }
        assertTrue(sent.isEmpty())
        assertEquals(listOf("Harbour Lights", "Kitchen Council"), repository.paused())
    }

    /** 24 Oct 2026: with every card cleared, Home has no Continue Watching row and no stale slides. */
    @Test
    fun clearingEveryCardRemovesTheRowAndItsSlides() = runBlocking {
        val repository = repository()
        listOf(film, episode).forEach { assertEquals(Done, repository.change(it, TitleChange.ResetProgress, session.generation.value)) }
        val content = HomeMapper.content(repository.state.value)
        assertTrue(content.rows.none { it.key == CONTINUE_WATCHING_ROW })
        assertTrue(content.slides.none { it.kind == SlideKind.ContinueWatching })
    }
}
