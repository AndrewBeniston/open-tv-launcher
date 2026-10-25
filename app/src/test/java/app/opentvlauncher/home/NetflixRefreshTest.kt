package app.opentvlauncher.home

import app.opentvlauncher.netflix.NetflixRules
import app.opentvlauncher.netflix.NetflixSource
import app.opentvlauncher.trakt.TokenResponse
import app.opentvlauncher.trakt.TraktApi
import app.opentvlauncher.trakt.TraktRowsSource
import app.opentvlauncher.trakt.TraktSession
import app.opentvlauncher.trakt.json
import app.opentvlauncher.trakt.tempStore
import app.opentvlauncher.trakt.testApi
import app.opentvlauncher.tv.TvChannels
import app.opentvlauncher.ui.menu.titleMenuChanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The daily Netflix check that runs with the refresh (netflix-play, option 1). */
class NetflixRefreshTest {
    private val server = MockWebServer()
    private val start = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private var clock = start
    private lateinit var file: File
    private val store = tempStore()
    private lateinit var session: TraktSession
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    /** Answers that a test can switch: the Trakt Netflix lists, and Wikidata. */
    private var netflixFails = false
    private var netflixEmpty = false
    private var wikidataFails = false

    /** Up next in Continue Watching, the shows in it that a Netflix history check finds, and that check's failure. */
    private var upNext = listOf<String>()
    private var playback = listOf<String>()
    private var onNetflix = setOf<Int>()
    private var historyFails = false

    /** Runs as each request arrives, before its answer. */
    private var onRequest: (RecordedRequest) -> Unit = {}

    @Before
    fun setUp() {
        server.start()
        file = File(Files.createTempDirectory("home").toFile(), "home-state.json")
        session = TraktSession(store, testApi(server)) { clock }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                onRequest(request)
                return answer(request)
            }
        }
    }

    @After
    fun tearDown() = server.close()

    private fun repository() = HomeRepository(
        store = HomeStateStore(file),
        trakt = TraktRowsSource(testApi(server), session),
        session = session,
        tvRows = { TvChannels.Rows(emptyList(), emptyList()) },
        now = { clock },
        netflix = NetflixSource(testApi(server), session, OkHttpClient(), server.url("/sparql")),
    )

    private fun answer(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        val netflixOnly = request.url.queryParameter("watchnow") == "netflix"
        return when {
            path == "/sparql" -> if (wikidataFails) {
                json(503)
            } else {
                val query = request.url.queryParameter("query").orEmpty()
                val known = mapOf("tt1" to "81252357", "tt2" to "81314956", "tt50" to "80000050", "tt51" to "80000051", "tt91000003" to "81593881").filterKeys { "\"$it\"" in query }
                json(200, """{"results":{"bindings":[${known.entries.joinToString(",") { """{"imdb":{"value":"${it.key}"},"netflix":{"value":"${it.value}"}}""" }}]}}""")
            }
            path.startsWith("/users/me/history/shows/") && netflixOnly -> when {
                historyFails -> json(503)
                path.substringAfterLast('/') == "230553" -> json(200, FILES_HISTORY_ON_NETFLIX)
                path.substringAfterLast('/').toInt() in onNetflix -> json(200, """[{"id":1}]""")
                else -> json(200, "[]")
            }
            path == "/sync/progress/up_next" -> json(200, upNext.joinToString(",", "[", "]"))
            path == "/sync/playback" -> json(200, playback.joinToString(",", "[", "]"))
            netflixOnly && netflixFails -> json(503)
            netflixOnly && netflixEmpty -> json(200, "[]")
            // On Netflix: Don't Go Back (watchlist) and Leave the Coast Behind (a recommendation).
            path == "/sync/watchlist/movies" && netflixOnly -> json(200, """[{"type":"movie","movie":{"title":"Don't Go Back","ids":{"trakt":1,"imdb":"tt1"}}}]""")
            path == "/recommendations/movies" && netflixOnly -> json(200, """[{"title":"Leave the Coast Behind","ids":{"trakt":2,"imdb":"tt2"}}]""")
            path == "/sync/watchlist/movies" -> json(
                200,
                """[{"type":"movie","movie":{"title":"Don't Go Back","ids":{"trakt":1,"imdb":"tt1"}}},{"type":"movie","movie":{"title":"Kitebird","ids":{"trakt":3,"imdb":"tt3"}}}]""",
            )
            path == "/recommendations/movies" -> json(200, """[{"title":"Leave the Coast Behind","ids":{"trakt":2,"imdb":"tt2"}}]""")
            else -> json(200, "[]")
        }
    }

    // A 30-day token, so the day-later refreshes need no token refresh.
    private fun signIn() = runBlocking { session.save(TokenResponse("access", "refresh", 30 * 86_400, start / 1000)) }

    /** A show's next episode, watched an hour before [start]. Its imdb id is "tt" and its Trakt id. */
    private fun next(id: Int, title: String) =
        """{"show":{"title":"$title","ids":{"trakt":$id,"imdb":"tt$id"}},"progress":{"last_watched_at":"2027-01-15T07:00:00.000Z","next_episode":""" +
            """{"season":1,"number":2,"title":"Episode 2","first_aired":"2026-05-27T01:00:00.000Z","ids":{"trakt":${id * 10}}}}}"""

    private fun historyCalls() = requests.filter { it.url.encodedPath.startsWith("/users/me/history/") }.map { it.url.encodedPath.substringAfterLast('/').toInt() }

    private fun netflixCalls() = requests.count { it.url.queryParameter("watchnow") == "netflix" || it.url.encodedPath == "/sparql" }

    private fun currentSignIn() = runBlocking { session.signedIn()?.id }

    /** The Netflix id the screen gives Don't Go Back, on the watchlist row, for the current sign-in. */
    private fun HomeRepository.shownId() = NetflixRules.withIds(state.value, clock, currentSignIn()).trakt!!.watchlistRow.first { it.traktId == 1 }.netflixId

    @Test
    fun aRefreshSavesWhatIsOnNetflixAndTheIds() = runBlocking {
        signIn()
        repository().refresh()
        val netflix = repository().state.value.netflix
        assertEquals(setOf(TitleKey(TitleKind.Movie, 1), TitleKey(TitleKind.Movie, 2)), netflix.available)
        assertEquals(mapOf("tt1" to "81252357", "tt2" to "81314956"), netflix.ids)
        assertEquals(start, netflix.checkedAt)
        assertEquals(start, netflix.availableAt)
    }

    @Test
    fun theCheckRunsOnceADay() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        val first = netflixCalls()
        clock = start + day - 1
        repository.refresh()
        assertEquals(first, netflixCalls())
        clock = start + day
        repository.refresh()
        assertTrue(netflixCalls() > first)
        assertEquals(start + day, repository.state.value.netflix.checkedAt)
    }

    @Test
    fun knownIdsAreNotAskedForAgain() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        clock = start + day
        requests.clear()
        repository.refresh()
        assertEquals(0, requests.count { it.url.encodedPath == "/sparql" })
    }

    /** A day is well inside the 7 days a failed check keeps the last answer for (the rule since 25 Oct 2026; it replaced "hide at once"). */
    @Test
    fun aFailedCheckADayLaterKeepsTheLastAnswerAndTriesAgainAtTheNextRefresh() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        val saved = repository.state.value.netflix
        netflixFails = true
        clock = start + day
        repository.refresh()
        assertEquals(saved, repository.state.value.netflix)
        assertEquals("81252357", repository.shownId())
        // The Trakt rows still refresh.
        assertEquals(start + day, repository.state.value.trakt!!.savedAt)
        netflixFails = false
        clock = start + day + 60_000
        repository.refresh()
        assertEquals(start + day + 60_000, repository.state.value.netflix.checkedAt)
    }

    /** The rule: the last answer counts for 7 days from the last check that worked, then the button hides. */
    @Test
    fun afterSevenDaysOfFailedChecksWhatIsOnNetflixIsNotKnown() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        netflixFails = true
        clock = start + 7 * day
        repository.refresh()
        assertEquals("81252357", repository.shownId())
        clock = start + 7 * day + 1
        repository.refresh()
        assertNull(repository.shownId())
        assertEquals(emptySet<TitleKey>(), repository.state.value.netflix.available)
        // The ids stay: they do not change.
        assertEquals(mapOf("tt1" to "81252357", "tt2" to "81314956"), repository.state.value.netflix.ids)
        netflixFails = false
        clock += 60_000
        repository.refresh()
        assertEquals("81252357", repository.shownId())
    }

    @Test
    fun aWikidataFailureSavesWhatIsOnNetflixAndTriesAgainAtTheNextRefresh() = runBlocking {
        signIn()
        wikidataFails = true
        val repository = repository()
        repository.refresh()
        assertEquals(setOf(TitleKey(TitleKind.Movie, 1), TitleKey(TitleKind.Movie, 2)), repository.state.value.netflix.available)
        assertEquals(emptyMap<String, String>(), repository.state.value.netflix.ids)
        assertNull(repository.state.value.netflix.checkedAt)
        wikidataFails = false
        clock = start + 60_000
        repository.refresh()
        assertEquals(mapOf("tt1" to "81252357", "tt2" to "81314956"), repository.state.value.netflix.ids)
        assertEquals(start + 60_000, repository.state.value.netflix.checkedAt)
    }

    @Test
    fun aSignOutForgetsWhatIsOnNetflixButKeepsTheIds() = runBlocking {
        signIn()
        repository().refresh()
        session.signOut()
        val repository = repository()
        repository.refresh()
        assertEquals(NetflixState(ids = mapOf("tt1" to "81252357", "tt2" to "81314956")), repository.state.value.netflix)
    }

    /** Review M1: another account's answer must not give this account Netflix buttons, even when it is new. */
    @Test
    fun anotherAccountGetsItsOwnCheck() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        val before = netflixCalls()
        // A sign-out and another sign-in, with no refresh between them. That account has no titles on Netflix.
        session.signOut()
        session.save(TokenResponse("other-access", "other-refresh", 30 * 86_400, start / 1000))
        netflixEmpty = true
        clock += 60_000
        repository.refresh()
        assertNull(repository.shownId())
        assertTrue(netflixCalls() > before)
    }

    /** Review R7-2: the new sign-in's refresh drops the old answer before its own requests end. */
    @Test
    fun aNewSignInsRefreshDropsTheOldAnswerBeforeItsRowsArrive() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        session.save(TokenResponse("other-access", "other-refresh", 30 * 86_400, start / 1000))
        netflixEmpty = true
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        onRequest = {
            if (it.url.encodedPath == "/sync/watchlist/movies" && it.url.queryParameter("watchnow") == null) {
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }
        val refresh = async(Dispatchers.IO) { repository.refresh() }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            assertEquals(emptySet<TitleKey>(), repository.state.value.netflix.available)
            assertNull(repository.shownId())
        } finally {
            release.countDown()
            refresh.await()
        }
    }

    /** Review R7-2: the saved file is still the old sign-in's after a restart, but its answer gives no button on the first frame. */
    @Test
    fun aRestartAfterANewSignInShowsNoOldAnswer() = runBlocking {
        signIn()
        repository().refresh()
        session.save(TokenResponse("other-access", "other-refresh", 30 * 86_400, start / 1000))
        assertNull(repository().shownId())
    }

    /** Review M1: a sign-out while the refresh reads Trakt forgets what is on Netflix, as a refresh after it does. */
    @Test
    fun aSignOutDuringTheRefreshForgetsWhatIsOnNetflix() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        onRequest = {
            if (it.url.encodedPath == "/sync/watchlist/movies" && it.url.queryParameter("watchnow") == null) {
                onRequest = {}
                runBlocking { session.signOut() }
            }
        }
        clock += 60_000
        repository.refresh()
        assertEquals(NetflixState(ids = mapOf("tt1" to "81252357", "tt2" to "81314956")), repository.state.value.netflix)
    }

    /** The answer belongs to the saved sign-in, not to the app's run: after a restart it still counts. */
    @Test
    fun theSameSignInKeepsItsAnswerAfterARestart() = runBlocking {
        signIn()
        repository().refresh()
        val before = netflixCalls()
        session = TraktSession(store, testApi(server)) { clock }
        clock += 60_000
        val repository = repository()
        repository.refresh()
        assertEquals(before, netflixCalls())
        assertEquals("81252357", repository.shownId())
    }

    @Test
    fun withoutSignInNothingIsAsked() = runBlocking {
        session.skip()
        repository().refresh()
        assertEquals(0, netflixCalls())
    }

    @Test
    fun theScreenShowsTheIdOnlyForTitlesOnNetflix() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        val shown = NetflixRules.withIds(repository.state.value, clock, currentSignIn()).trakt!!
        assertEquals(mapOf("Don't Go Back" to "81252357", "Kitebird" to null), shown.watchlistRow.associate { it.title to it.netflixId })
    }

    /**
     * 25 Oct 2026: a title new to Continue Watching between daily checks, such as a show's
     * next episode, is checked at once through its history, not at the next daily check. The
     * watchlist and recommendation lists are not asked again, and the answer keeps its time.
     */
    @Test
    fun aTitleNewToContinueWatchingIsCheckedAtOnce() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        val daily = repository.state.value.netflix
        upNext = listOf(next(50, "Paper Kites"))
        onNetflix = setOf(50)
        requests.clear()
        clock = start + 60_000
        repository.refresh()
        assertEquals(listOf(50), historyCalls())
        assertEquals(0, requests.count { it.url.queryParameter("watchnow") == "netflix" && !it.url.encodedPath.startsWith("/users/me/history/") })
        val netflix = repository.state.value.netflix
        assertEquals(daily.available + TitleKey(TitleKind.Show, 50), netflix.available)
        assertEquals("80000050", netflix.ids["tt50"])
        assertEquals(setOf(TitleKey(TitleKind.Show, 50)), netflix.continueChecked)
        assertEquals(start, netflix.checkedAt)
        assertEquals(start, netflix.availableAt)
        val shown = NetflixRules.withIds(repository.state.value, clock, currentSignIn()).trakt!!.continueWatching
        assertEquals("80000050", shown.single().netflixId)
    }

    /** A title already checked is not asked for again, and a title that is not on Netflix gets no id. */
    @Test
    fun onlyTheNewTitlesAreChecked() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        upNext = listOf(next(50, "Paper Kites"))
        clock = start + 60_000
        repository.refresh()
        upNext = listOf(next(51, "Glass Foxes"), next(50, "Paper Kites"))
        requests.clear()
        clock += 60_000
        repository.refresh()
        assertEquals(listOf(51), historyCalls())
        assertEquals(0, requests.count { it.url.encodedPath == "/sparql" })
        assertEquals(setOf(TitleKey(TitleKind.Show, 50), TitleKey(TitleKind.Show, 51)), repository.state.value.netflix.continueChecked)
        requests.clear()
        clock += 60_000
        repository.refresh()
        assertEquals(0, netflixCalls())
    }

    @Test
    fun aFailedHistoryCheckLeavesTheTitleForTheNextRefresh() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        val daily = repository.state.value.netflix
        upNext = listOf(next(50, "Paper Kites"))
        onNetflix = setOf(50)
        historyFails = true
        clock = start + 60_000
        repository.refresh()
        assertEquals(daily, repository.state.value.netflix)
        historyFails = false
        clock += 60_000
        repository.refresh()
        assertTrue(TitleKey(TitleKind.Show, 50) in repository.state.value.netflix.available)
    }

    /**
     * Merge gate V2: a Wikidata failure after a new title's history check saves that it is on
     * Netflix, but not that it is checked. The next refresh checks it again and gets its id.
     */
    @Test
    fun aWikidataFailureForANewTitleTriesAgainAtTheNextRefresh() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        upNext = listOf(next(50, "Paper Kites"))
        onNetflix = setOf(50)
        wikidataFails = true
        clock = start + 60_000
        repository.refresh()
        val failed = repository.state.value.netflix
        assertTrue(TitleKey(TitleKind.Show, 50) in failed.available)
        assertNull(failed.ids["tt50"])
        assertEquals(emptySet<TitleKey>(), failed.continueChecked)
        wikidataFails = false
        clock += 60_000
        repository.refresh()
        assertEquals("80000050", repository.state.value.netflix.ids["tt50"])
        assertEquals(setOf(TitleKey(TitleKind.Show, 50)), repository.state.value.netflix.continueChecked)
        assertEquals(start, repository.state.value.netflix.checkedAt)
    }

    /**
     * Merge gate C93-1: a new title's positive answer whose id never came is provisional. When
     * the next check succeeds with a negative, the title is not on Netflix.
     */
    @Test
    fun aNegativeRetryRemovesAProvisionalAnswer() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        upNext = listOf(next(50, "Paper Kites"))
        onNetflix = setOf(50)
        wikidataFails = true
        clock = start + 60_000
        repository.refresh()
        assertTrue(TitleKey(TitleKind.Show, 50) in repository.state.value.netflix.available)
        assertNull(repository.state.value.netflix.ids["tt50"])
        assertTrue(repository.state.value.netflix.continueChecked.isEmpty())
        wikidataFails = false
        onNetflix = emptySet()
        requests.clear()
        clock += 60_000
        repository.refresh()
        assertEquals(listOf(50), historyCalls())
        assertNull(
            "The fresh negative must remove the rendered Netflix action",
            NetflixRules.withIds(repository.state.value, clock, currentSignIn()).trakt!!.continueWatching.single().netflixId,
        )
        assertTrue("The retry returned a current negative, but kept the earlier positive", TitleKey(TitleKind.Show, 50) !in repository.state.value.netflix.available)
    }

    /** The negative retry changes only the title it checks: the daily answer and a finished new-title answer stay. */
    @Test
    fun aNegativeRetryLeavesTheOtherAnswers() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        val daily = repository.state.value.netflix
        upNext = listOf(next(51, "Glass Foxes"))
        onNetflix = setOf(51)
        clock = start + 60_000
        repository.refresh()
        upNext = listOf(next(50, "Paper Kites"), next(51, "Glass Foxes"))
        onNetflix = setOf(50, 51)
        wikidataFails = true
        clock += 60_000
        repository.refresh()
        wikidataFails = false
        onNetflix = setOf(51)
        requests.clear()
        clock += 60_000
        repository.refresh()
        assertEquals(listOf(50), historyCalls())
        val netflix = repository.state.value.netflix
        assertEquals(daily.available + TitleKey(TitleKind.Show, 51), netflix.available)
        assertEquals(emptySet<TitleKey>(), netflix.provisional)
        assertEquals("80000051", netflix.ids["tt51"])
        assertEquals(setOf(TitleKey(TitleKind.Show, 50), TitleKey(TitleKind.Show, 51)), netflix.continueChecked)
        assertEquals(daily.signIn, netflix.signIn)
        assertEquals(start, netflix.checkedAt)
        assertEquals(start, netflix.availableAt)
    }

    /**
     * A watchlist film that the daily check found on Netflix, then paused: its history has no play
     * yet, so the new-title check says no. The daily answer is not provisional, so it stays.
     */
    @Test
    fun aNegativeNewTitleCheckKeepsTheDailyAnswer() = runBlocking {
        signIn()
        val repository = repository()
        repository.refresh()
        playback = listOf("""{"id":7,"progress":20.0,"paused_at":"2027-01-15T07:00:00.000Z","type":"movie","movie":{"title":"Don't Go Back","ids":{"trakt":1,"imdb":"tt1"}}}""")
        requests.clear()
        clock = start + 60_000
        repository.refresh()
        assertEquals(1, requests.count { it.url.encodedPath == "/users/me/history/movies/1" })
        val film = NetflixRules.withIds(repository.state.value, clock, currentSignIn()).trakt!!.continueWatching.single()
        assertEquals("Don't Go Back", film.title)
        assertEquals("81252357", film.netflixId)
    }

    /** The daily check covers the whole row, so it records each title in it as checked. */
    @Test
    fun theDailyCheckRecordsTheRowAsChecked() = runBlocking {
        signIn()
        upNext = listOf(next(50, "Paper Kites"))
        onNetflix = setOf(50)
        val repository = repository()
        repository.refresh()
        assertEquals(setOf(TitleKey(TitleKind.Show, 50)), repository.state.value.netflix.continueChecked)
        assertTrue(TitleKey(TitleKind.Show, 50) in repository.state.value.netflix.available)
        requests.clear()
        clock = start + 60_000
        repository.refresh()
        assertEquals(0, netflixCalls())
    }

    /**
     * A TV after the install of 25 Oct 2026, with Trakt's real answers for Tales of the
     * Quiet Coast (trakt 230553, tt91000003): up next S1E2, no paused item, not on the watchlist,
     * and its history with Trakt's Netflix filter has its S1E1 play. The saved state is the build
     * before's: a daily check earlier that day, before up next, and no list of checked titles. The
     * first refresh gives the show its Wikidata Netflix id, and its menu offers only Mark as Watched.
     */
    @Test
    fun talesOfTheQuietCoastGetsItsNetflixIdAtTheFirstRefreshAfterTheInstall() = runBlocking {
        signIn()
        repository().refresh()
        // The build before saved no continueChecked.
        val saved = TraktApi.json.parseToJsonElement(file.readText()).jsonObject
        val netflix = JsonObject(saved.getValue("netflix").jsonObject - "continueChecked")
        file.writeText(JsonObject(saved + ("netflix" to netflix)).toString())
        upNext = listOf(FILES_UP_NEXT)
        clock = start + 3 * 60 * 60 * 1000L
        val repository = repository()
        assertEquals(emptySet<TitleKey>(), repository.state.value.netflix.continueChecked)
        repository.refresh()
        assertEquals(start, repository.state.value.netflix.checkedAt)
        val files = NetflixRules.withIds(repository.state.value, clock, currentSignIn()).trakt!!.continueWatching.single()
        assertEquals("Tales of the Quiet Coast", files.title)
        assertEquals(EpisodeRef(1, 2, "File: Lights over the Salt Marshes"), files.episode)
        assertEquals("81593881", files.netflixId)
        assertEquals(listOf(TitleChange.MarkWatched), titleMenuChanges(files, listed = false))
    }

    private companion object {
        // Trakt's answers of 25 Oct 2026, with only the fields the launcher reads.
        val FILES_UP_NEXT = """{"show":{"title":"Tales of the Quiet Coast","year":2024,"ids":{"trakt":230553,"slug":"tales-of-the-quiet-coast",""" +
            """"imdb":"tt91000003","tmdb":248795,"tvdb":447413}},"progress":{"aired":8,"completed":1,"last_watched_at":"2026-10-20T22:18:00.000Z",""" +
            """"next_episode":{"season":1,"number":2,"title":"File: Lights over the Salt Marshes","first_aired":"2024-04-03T04:00:00.000Z",""" +
            """"ids":{"trakt":11708710,"imdb":"tt91000006","tmdb":5256946,"tvdb":10422732}}}}"""
        val FILES_HISTORY_ON_NETFLIX = """[{"id":14558289952,"watched_at":"2026-10-20T22:18:00.000Z","action":"watch","type":"episode",""" +
            """"episode":{"season":1,"number":1,"title":"File: The Ferry That Never Landed","ids":{"trakt":11623603}},""" +
            """"show":{"title":"Tales of the Quiet Coast","year":2024,"ids":{"trakt":230553,"imdb":"tt91000003"}}}]"""
    }
}
