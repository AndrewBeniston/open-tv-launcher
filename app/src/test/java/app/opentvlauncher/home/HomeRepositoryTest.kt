package app.opentvlauncher.home

import app.opentvlauncher.screensaver.screensaverTitles
import app.opentvlauncher.trakt.HeldStore
import app.opentvlauncher.trakt.SignIn
import app.opentvlauncher.trakt.TokenResponse
import app.opentvlauncher.trakt.TraktRowsSource
import app.opentvlauncher.trakt.TraktSession
import app.opentvlauncher.trakt.heldAnswers
import app.opentvlauncher.trakt.json
import app.opentvlauncher.trakt.tempStore
import app.opentvlauncher.trakt.testApi
import app.opentvlauncher.tv.TvChannels
import app.opentvlauncher.ui.details.WatchlistButton
import app.opentvlauncher.ui.details.watchlistButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** The saved HomeState: the first frame reads it, and a failed refresh keeps the last good rows. */
class HomeRepositoryTest {
    private val server = MockWebServer()
    private val now = 1_800_000_000_000L
    private lateinit var file: File
    private lateinit var session: TraktSession

    @Before
    fun setUp() {
        server.start()
        file = File(Files.createTempDirectory("home").toFile(), "home-state.json")
        session = TraktSession(tempStore(), testApi(server)) { now }
    }

    @After
    fun tearDown() = server.close()

    private fun repository() = HomeRepository(
        store = HomeStateStore(file),
        trakt = TraktRowsSource(testApi(server), session),
        session = session,
        tvRows = { TvChannels.Rows(listOf(Video("A video")), emptyList()) },
        now = { now },
    )

    /** Answers each Trakt path with a small, made-up response. */
    private fun answerTrakt(failing: Boolean = false) {
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: RecordedRequest) = traktAnswer(request, failing)
        }
    }

    private fun traktAnswer(request: RecordedRequest, failing: Boolean = false) = if (failing) {
        json(503)
    } else {
        val path = request.url.encodedPath
        when {
            path.startsWith("/sync/playback") -> json(
                200,
                """[{"progress":53.0,"paused_at":"2027-01-14T20:00:00.000Z","type":"movie","movie":{"title":"Harbour Lights","year":2026,"ids":{"trakt":1,"imdb":"tt1"}}}]""",
            )
            path.startsWith("/recommendations/movies") -> json(200, """[{"title":"Hollowby","year":2016,"ids":{"trakt":2,"imdb":"tt2"}}]""")
            path.startsWith("/sync/watchlist/movies") ->
                json(200, """[{"rank":1,"type":"movie","movie":{"title":"Ember","year":1952,"ids":{"trakt":3,"imdb":"tt3"}}}]""")
            path.startsWith("/sync/watched/movies") -> json(200, """[{"movie":{"title":"Seen","ids":{"trakt":4}}}]""")
            else -> json(200, "[]")
        }
    }

    @Test
    fun aRefreshSavesTheRowsAndTheNextStartReadsThemWithoutTheNetwork() = runBlocking {
        session.save(TokenResponse("access", "refresh", 86_400, now / 1000))
        answerTrakt()
        repository().refresh()

        server.close()
        val nextStart = repository().state.value
        assertEquals(listOf("Harbour Lights"), nextStart.trakt!!.continueWatching.map { it.title })
        assertEquals(listOf("Hollowby"), nextStart.trakt!!.picks.map { it.title })
        assertEquals(listOf("Ember"), nextStart.trakt!!.watchlistRow.map { it.title })
        assertEquals(listOf("A video"), nextStart.youtube.map { it.title })
        assertNull(nextStart.traktFailedAt)
    }

    @Test
    fun aFailedRefreshKeepsTheLastGoodTraktRowsAndNotesTheFailure() = runBlocking {
        session.save(TokenResponse("access", "refresh", 86_400, now / 1000))
        answerTrakt()
        repository().refresh()
        answerTrakt(failing = true)
        val repository = repository()
        repository.refresh()
        assertEquals(listOf("Harbour Lights"), repository.state.value.trakt!!.continueWatching.map { it.title })
        assertEquals(now, repository.state.value.traktFailedAt)
    }

    @Test
    fun withoutSignInOnlyTheTvRowsAreRead() = runBlocking {
        session.skip()
        repository().refresh()
        assertEquals(0, server.requestCount)
        assertTrue(file.exists())
    }

    @Test
    fun aSignOutDropsTheTraktRows() = runBlocking {
        session.save(TokenResponse("access", "refresh", 86_400, now / 1000))
        answerTrakt()
        repository().refresh()
        session.signOut()
        val repository = repository()
        repository.refresh()
        assertNull(repository.state.value.trakt)
    }

    /** Rows fetched for an account that signs out during the fetch are not saved. */
    @Test
    fun aSignOutDuringARefreshDropsThatAccountsRows() = runBlocking {
        session.save(TokenResponse("access", "refresh", 86_400, now / 1000))
        val release = CountDownLatch(1)
        server.dispatcher = heldAnswers(release) { traktAnswer(it) }
        val repository = repository()
        val refresh = async(Dispatchers.IO) { repository.refresh() }
        server.takeRequest()
        session.signOut()
        release.countDown()
        refresh.await()
        assertNull(repository.state.value.trakt)
    }

    /**
     * A refresh that starts while a sign-out is writing must not publish or save the account's
     * rows.
     */
    @Test
    fun aRefreshDuringASlowSignOutDoesNotPublishTheAccountsRows() = runBlocking {
        val store = HeldStore()
        session = TraktSession(store, testApi(server)) { now }
        session.save(TokenResponse("access", "refresh", 86_400, now / 1000))
        val answers = CountDownLatch(1)
        server.dispatcher = heldAnswers(answers) { traktAnswer(it) }
        val repository = repository()

        val release = CountDownLatch(1)
        store.hold = release
        val signingOut = async(Dispatchers.IO) { session.signOut() }
        store.entered.await()
        val refresh = async(Dispatchers.IO) { repository.refresh() }
        delay(200)
        release.countDown()
        signingOut.await()
        answers.countDown()
        refresh.await()
        assertNull(repository.state.value.trakt)
        assertNull(HomeStateStore(file).read().trakt)
        assertEquals(SignIn.SignedOut, session.state.first())
    }

    /** Signs in and refreshes with Trakt answering only [answers] (path to body); everything else is empty. */
    private suspend fun refreshedWith(answers: Map<String, String>): HomeRepository {
        session.save(TokenResponse("access", "refresh", 86_400, now / 1000))
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when (request.method) {
                "POST" -> json(201, "{}")
                else -> json(200, answers[request.url.encodedPath] ?: "[]")
            }
        }
        val repository = repository()
        repository.refresh()
        return repository
    }

    /** 25 Oct 2026: a refresh puts up next into Continue Watching, without hidden or dropped shows. */
    @Test
    fun aRefreshAddsUpNextWithoutHiddenOrDroppedShows() = runBlocking {
        fun next(id: Int, title: String, number: Int, watched: String) =
            """{"show":{"title":"$title","ids":{"trakt":$id,"imdb":"tt$id"}},"progress":{"last_watched_at":"$watched","next_episode":""" +
                """{"season":1,"number":$number,"title":"Episode $number","first_aired":"2026-05-27T01:00:00.000Z","ids":{"trakt":${id * 10}}}}}"""
        val repository = refreshedWith(
            mapOf(
                "/sync/progress/up_next" to listOf(
                    next(151163, "Kitchen Council", 5, "2027-01-15T07:21:00.000Z"),
                    next(48587, "Harbour Four-Seven", 2, "2027-01-15T07:00:00.000Z"),
                    next(280754, "The Long Field", 10, "2027-01-14T23:07:00.000Z"),
                    next(1, "Lodgers", 3, "2027-01-14T20:00:00.000Z"),
                ).joinToString(",", "[", "]"),
                "/users/hidden/progress_watched" to """[{"hidden_at":"2023-03-27T00:00:00.000Z","type":"show","show":{"title":"Harbour Four-Seven","ids":{"trakt":48587}}}]""",
                "/users/hidden/dropped" to """[{"type":"show","show":{"title":"Lodgers","ids":{"trakt":1}}}]""",
            ),
        )
        val shown = repository.state.value.trakt!!.continueWatching
        assertEquals(
            listOf("Kitchen Council" to EpisodeRef(1, 5, "Episode 5"), "The Long Field" to EpisodeRef(1, 10, "Episode 10")),
            shown.map { it.title to it.episode },
        )
        assertTrue(shown.all { it.upNextAt != null && it.progress == null })
    }

    /**
     * One Top Pick, no watchlist and nothing in progress. Adding the pick moved it to From Your
     * Watchlist but left the screensaver empty.
     */
    @Test
    fun addingTheLastTopPickKeepsItInTheScreensaver() = runBlocking {
        val repository = refreshedWith(mapOf("/recommendations/movies" to """[{"title":"Hollowby","year":2016,"ids":{"trakt":2,"imdb":"tt2"}}]"""))
        val hollowby = repository.state.value.trakt!!.picks.single()
        assertTrue(repository.setWatchlisted(hollowby, on = true))
        assertEquals(listOf("Hollowby"), screensaverTitles(repository.state.value, day = 1).map { it.title })
        assertEquals(listOf("Hollowby"), screensaverTitles(HomeStateStore(file).read(), day = 1).map { it.title })
    }

    /**
     * A watchlist title with Trakt's fanart but no IMDb id showed on Home and was dropped before
     * the screensaver could use it.
     */
    @Test
    fun aWatchlistTitleWithOnlyTraktArtReachesTheScreensaver() = runBlocking {
        val repository = refreshedWith(
            mapOf(
                "/sync/watchlist/movies" to
                    """[{"rank":1,"type":"movie","movie":{"title":"Trakt only","year":2020,"ids":{"trakt":7},"images":{"fanart":["media.trakt.tv/f7.webp"]}}}]""",
            ),
        )
        assertEquals(listOf("Trakt only"), repository.state.value.trakt!!.watchlistRow.map { it.title })
        assertEquals(listOf("Trakt only"), screensaverTitles(repository.state.value, day = 1).map { it.title })
    }

    /** Fills the rows, then answers the watchlist calls with 201 and records them. */
    private suspend fun signedInWithRows(): HomeRepository {
        session.save(TokenResponse("access", "refresh", 86_400, now / 1000))
        answerTrakt()
        val repository = repository()
        repository.refresh()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: RecordedRequest) = json(201, "{}")
        }
        return repository
    }

    private fun ember(repository: HomeRepository) = repository.state.value.trakt!!.watchlistRow.first { it.title == "Ember" }

    @Test
    fun removingFromTheWatchlistTellsTraktAndTheTitleLeavesTheWatchlistRow() = runBlocking {
        val repository = signedInWithRows()
        assertTrue(TitleKey(TitleKind.Movie, 3) in repository.state.value.trakt!!.watchlistKeys)
        val before = server.requestCount
        assertTrue(repository.setWatchlisted(ember(repository), on = false))

        val requests = (0 until server.requestCount).map { server.takeRequest() }.drop(before)
        val remove = requests.single()
        assertEquals("/sync/watchlist/remove", remove.url.encodedPath)
        assertEquals("""{"movies":[{"ids":{"trakt":3}}]}""", remove.body!!.utf8())
        val rows = repository.state.value.trakt!!
        assertTrue(rows.watchlistRow.none { it.title == "Ember" } && TitleKey(TitleKind.Movie, 3) !in rows.watchlistKeys)
    }

    /** An added title leads From Your Watchlist at once, before the next refresh. */
    @Test
    fun addingToTheWatchlistPutsTheTitleFirstInTheWatchlistRow() = runBlocking {
        val repository = signedInWithRows()
        val hollowby = repository.state.value.trakt!!.picks.first { it.title == "Hollowby" }
        assertTrue(repository.setWatchlisted(hollowby, on = true))
        val rows = repository.state.value.trakt!!
        assertEquals(listOf("Hollowby", "Ember"), rows.watchlistRow.map { it.title })
        assertEquals(PickSource.Watchlist, rows.watchlistRow.first().source)
        assertTrue(TitleKey(TitleKind.Movie, 2) in rows.watchlistKeys)
        // Trakt leaves watchlist titles out of its recommendations, so it leaves Top Picks too.
        assertTrue(rows.picks.none { it.title == "Hollowby" })
    }

    /** Adding a show from Top Picks keeps the rest of the row in turn: film, show, film. */
    @Test
    fun addingATopPickKeepsFilmsAndShowsInTurn() = runBlocking {
        val repository = refreshedWith(
            mapOf(
                "/recommendations/movies" to """[{"title":"M1","ids":{"trakt":1}},{"title":"M2","ids":{"trakt":2}}]""",
                "/recommendations/shows" to """[{"title":"S1","ids":{"trakt":1}},{"title":"S2","ids":{"trakt":2}}]""",
            ),
        )
        assertEquals(listOf("M1", "S1", "M2", "S2"), repository.state.value.trakt!!.picks.map { it.title })
        val s1 = repository.state.value.trakt!!.picks.first { it.title == "S1" }
        assertTrue(repository.setWatchlisted(s1, on = true))
        assertEquals(listOf("M1", "S2", "M2"), repository.state.value.trakt!!.picks.map { it.title })
    }

    /**
     * Adding a paused title to the watchlist put it in From Your Watchlist as well as Continue
     * Watching, and twice in the banner.
     */
    @Test
    fun addingAPausedTitleKeepsItOutOfTheWatchlistRow() = runBlocking {
        val repository = signedInWithRows()
        val paused = repository.state.value.trakt!!.continueWatching.single()
        assertTrue(repository.setWatchlisted(paused, on = true))
        val rows = repository.state.value.trakt!!
        assertEquals(listOf("Ember"), rows.watchlistRow.map { it.title })
        assertTrue(paused.key in rows.watchlistKeys)
        assertEquals(1, HomeMapper.content(repository.state.value).slides.count { it.title == "Harbour Lights" })
        // The saved copy is the same.
        assertEquals(listOf("Ember"), HomeStateStore(file).read().trakt!!.watchlistRow.map { it.title })
    }

    @Test
    fun addingAWatchedTitleKeepsItOutOfTheWatchlistRow() = runBlocking {
        val repository = signedInWithRows()
        val seen = Title(kind = TitleKind.Movie, title = "Seen", traktId = 4)
        assertTrue(repository.setWatchlisted(seen, on = true))
        assertEquals(listOf("Ember"), repository.state.value.trakt!!.watchlistRow.map { it.title })
    }

    /**
     * Trakt pages the watched list. A film watched on page 2, and a show that was rated but never
     * played, both stay out of Top Picks, From Your Watchlist and the banner.
     */
    @Test
    fun titlesWatchedOnALaterPageOrRatedStayOutOfTheRows() = runBlocking {
        session.save(TokenResponse("access", "refresh", 86_400, now / 1000))
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: RecordedRequest): mockwebserver3.MockResponse {
                val page = request.url.queryParameter("page") ?: "1"
                return when (request.url.encodedPath) {
                    "/sync/watched/movies" -> mockwebserver3.MockResponse.Builder()
                        .code(200)
                        .addHeader("X-Pagination-Page-Count", "2")
                        .body(if (page == "1") """[{"movie":{"title":"Seen","ids":{"trakt":4}}}]""" else """[{"movie":{"title":"Hollowby","ids":{"trakt":2}}}]""")
                        .build()
                    "/sync/ratings/shows" -> json(200, """[{"rating":4,"show":{"title":"Margin","ids":{"trakt":9}}}]""")
                    "/recommendations/movies" -> json(200, """[{"title":"Hollowby","year":2016,"ids":{"trakt":2,"imdb":"tt2"}}]""")
                    "/recommendations/shows" -> json(200, """[{"title":"Margin","year":2008,"ids":{"trakt":9,"imdb":"tt9"}}]""")
                    "/sync/watchlist/movies" ->
                        json(200, """[{"rank":1,"type":"movie","movie":{"title":"Ember","year":1952,"ids":{"trakt":3,"imdb":"tt3"}}}]""")
                    "/sync/watchlist/shows" -> json(200, """[{"rank":2,"type":"show","show":{"title":"Margin","year":2008,"ids":{"trakt":9,"imdb":"tt9"}}}]""")
                    else -> json(200, "[]")
                }
            }
        }
        val repository = repository()
        repository.refresh()
        val rows = repository.state.value.trakt!!
        assertEquals(emptyList<String>(), rows.picks.map { it.title })
        assertEquals(listOf("Ember"), rows.watchlistRow.map { it.title })
        assertEquals(listOf("Ember"), HomeMapper.content(repository.state.value).slides.map { it.title })
    }

    /** Removing a film keeps a show with the same Trakt id. */
    @Test
    fun removingAFilmKeepsAShowWithTheSameId() = runBlocking {
        session.save(TokenResponse("access", "refresh", 86_400, now / 1000))
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when (request.url.encodedPath) {
                "/sync/watchlist/movies" -> json(200, """[{"type":"movie","movie":{"title":"A Film","ids":{"trakt":41,"imdb":"tt41"}}}]""")
                "/sync/watchlist/shows" -> json(200, """[{"type":"show","show":{"title":"A Show","ids":{"trakt":41,"imdb":"tt9041"}}}]""")
                "/sync/watchlist/remove" -> json(201, "{}")
                else -> json(200, "[]")
            }
        }
        val repository = repository()
        repository.refresh()
        val film = repository.state.value.trakt!!.watchlistRow.first { it.kind == TitleKind.Movie }
        assertTrue(repository.setWatchlisted(film, on = false))

        val rows = repository.state.value.trakt!!
        assertEquals(listOf("A Show"), rows.watchlistRow.map { it.title })
        assertEquals(listOf("A Show"), rows.watchlist.map { it.title })
        assertEquals(setOf(TitleKey(TitleKind.Show, 41)), rows.watchlistKeys)
    }

    /**
     * Runs [test] with a WatchlistUndo in its own scope. The undo watches the account until the
     * scope is cancelled, so the scope is not a child that runBlocking would wait for.
     */
    private fun withUndo(delayMillis: Long, test: suspend CoroutineScope.(HomeRepository, WatchlistUndo) -> Unit) = runBlocking {
        val repository = signedInWithRows()
        val scope = CoroutineScope(coroutineContext + Job())
        try {
            test(repository, WatchlistUndo(scope, repository, session, delayMillis))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun anUndoWithinTheWindowSendsNothing() = withUndo(200) { repository, undo ->
        val before = server.requestCount
        val title = ember(repository)
        undo.remove(title)
        assertEquals(mapOf(TitleKey(TitleKind.Movie, 3) to WatchlistUndo.Phase.Waiting), undo.phases.value)
        undo.undo(title)
        delay(400)
        assertEquals(before, server.requestCount)
        assertTrue(undo.phases.value.isEmpty())
    }

    @Test
    fun withoutAnUndoTheRemovalRunsAfterTheWindow() = withUndo(100) { repository, undo ->
        val before = server.requestCount
        undo.remove(ember(repository))
        delay(1_000)
        assertEquals(before + 1, server.requestCount)
        assertTrue(undo.phases.value.isEmpty())
        assertTrue(repository.state.value.trakt!!.watchlistRow.none { it.title == "Ember" })
    }

    /**
     * Trakt accepts the removal but its answer is slow. Once the request is sent, "Add Back to
     * Watchlist" is no longer offered, because cancelling could not take the removal back. The
     * removal finishes, and the saved rows agree with Trakt.
     */
    @Test
    fun aRemovalThatIsSentCannotBeUndoneAndItsResultIsSaved() = withUndo(50) { repository, undo ->
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        val removedOnTrakt = AtomicBoolean(false)
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: RecordedRequest): mockwebserver3.MockResponse {
                if (request.url.encodedPath == "/sync/watchlist/remove") {
                    removedOnTrakt.set(true)
                    received.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
                return json(201, "{}")
            }
        }
        val title = ember(repository)
        val key = title.key!!
        undo.remove(title)
        assertEquals(WatchlistButton.Undo, watchlistButton(undo.phases.value[key], listed = true))
        assertTrue(withContext(Dispatchers.IO) { received.await(5, TimeUnit.SECONDS) })

        assertEquals(WatchlistUndo.Phase.Removing, undo.phases.value[key])
        assertEquals(WatchlistButton.Removing, watchlistButton(undo.phases.value[key], listed = true))
        undo.undo(title)
        assertEquals(WatchlistUndo.Phase.Removing, undo.phases.value[key])

        release.countDown()
        withTimeout(5_000) { undo.phases.first { key !in it } }
        assertTrue(removedOnTrakt.get())
        val rows = repository.state.value.trakt!!
        assertFalse(key in rows.watchlistKeys)
        assertTrue(rows.watchlistRow.none { it.key == key })
        assertEquals(WatchlistButton.Add, watchlistButton(undo.phases.value[key], key in rows.watchlistKeys))
    }

    /** A removal that Trakt refuses says so, keeps the title, and can be tried again. */
    @Test
    fun aFailedRemovalShowsAndCanBeTriedAgain() = withUndo(50) { repository, undo ->
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: RecordedRequest) = json(503)
        }
        val title = ember(repository)
        val key = title.key!!
        undo.remove(title)
        withTimeout(5_000) { undo.phases.first { it[key] == WatchlistUndo.Phase.Failed } }
        val listed = key in repository.state.value.trakt!!.watchlistKeys
        assertTrue(listed)
        assertEquals(WatchlistButton.RetryRemove, watchlistButton(undo.phases.value[key], listed))

        undo.remove(title)
        assertEquals(WatchlistUndo.Phase.Waiting, undo.phases.value[key])
    }

    /**
     * Account A's removal is sent and Trakt's answer is late. The TV signs in to B, and B removes
     * the same title. The undo runs on a test scheduler whose clock moves only when the test moves
     * it, so B stays in its undo window until A's removal has finished. [test] then gets the undo,
     * the title, the count of removals sent with B's token, and a function that ends B's undo
     * window and runs every removal to its end.
     */
    private fun lateAnswerForAnOldAccount(test: (WatchlistUndo, Title, AtomicInteger, () -> Unit) -> Unit) = runBlocking {
        val repository = signedInWithRows()
        val sentByA = CountDownLatch(1)
        val answerA = CountDownLatch(1)
        val removalsByB = AtomicInteger()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: RecordedRequest): mockwebserver3.MockResponse {
                if (request.url.encodedPath == "/sync/watchlist/remove") {
                    if (request.headers["Authorization"] == "Bearer access-B") {
                        removalsByB.incrementAndGet()
                    } else {
                        sentByA.countDown()
                        answerA.await(10, TimeUnit.SECONDS)
                    }
                }
                return json(201, "{}")
            }
        }
        val scheduler = TestCoroutineScheduler()
        val scope = CoroutineScope(StandardTestDispatcher(scheduler) + Job())
        val jobs = scope.coroutineContext.job

        /** Runs the undo's work at the current test time until [done], waiting in real time for the network. */
        fun runUntil(done: () -> Boolean) {
            val giveUp = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (true) {
                scheduler.runCurrent()
                if (done()) return
                check(System.nanoTime() < giveUp) { "Gave up waiting" }
                Thread.sleep(5)
            }
        }
        try {
            val undo = WatchlistUndo(scope, repository, session, delayMillis = 1_000)
            scheduler.runCurrent()
            val collector = jobs.children.single()
            val title = ember(repository)
            undo.remove(title)
            val removalA = jobs.children.single { it !== collector }
            scheduler.advanceTimeBy(1_000)
            runUntil { sentByA.count == 0L }

            session.signOut()
            session.save(TokenResponse("access-B", "refresh-B", 86_400, now / 1000))
            runUntil { undo.phases.value.isEmpty() }
            undo.remove(title)
            scheduler.runCurrent()
            assertEquals(WatchlistUndo.Phase.Waiting, undo.phases.value[title.key])

            answerA.countDown()
            runUntil { removalA.isCompleted }
            test(undo, title, removalsByB) {
                scheduler.advanceTimeBy(1_000)
                runUntil { jobs.children.singleOrNull() === collector }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aLateAnswerForAnOldAccountKeepsTheNewUndoWindow() = lateAnswerForAnOldAccount { undo, title, _, _ ->
        assertEquals(WatchlistUndo.Phase.Waiting, undo.phases.value[title.key])
    }

    @Test
    fun aLateAnswerForAnOldAccountDoesNotLetTheNewAccountRemoveTwice() = lateAnswerForAnOldAccount { undo, title, removalsByB, finish ->
        // A second press, as if the button offered "Remove from Watchlist" again.
        undo.remove(title)
        finish()
        assertEquals(1, removalsByB.get())
    }

    /** A removal asked for under account A is never sent for account B. */
    @Test
    fun aRemovalWaitingWhenTheAccountChangesIsNeverSent() = withUndo(300) { repository, undo ->
        val before = server.requestCount
        undo.remove(ember(repository))
        session.signOut()
        session.save(TokenResponse("access-B", "refresh-B", 86_400, now / 1000))
        delay(700)
        assertEquals(before, server.requestCount)
        assertTrue(undo.phases.value.isEmpty())
    }

    @Test
    fun aChangeForAnAccountThatHasSignedOutIsNotSent() = runBlocking {
        val repository = signedInWithRows()
        val accountA = session.generation.value
        session.save(TokenResponse("access-B", "refresh-B", 86_400, now / 1000))
        val before = server.requestCount
        assertFalse(repository.setWatchlisted(ember(repository), on = false, accountA))
        assertEquals(before, server.requestCount)
    }
}
