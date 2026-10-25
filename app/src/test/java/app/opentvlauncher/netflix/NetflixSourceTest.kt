package app.opentvlauncher.netflix

import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleKey
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.trakt.TokenResponse
import app.opentvlauncher.trakt.TraktSession
import app.opentvlauncher.trakt.json
import app.opentvlauncher.trakt.tempStore
import app.opentvlauncher.trakt.testApi
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** Trakt's Netflix lists and Wikidata's Netflix ids, against a mock server. */
class NetflixSourceTest {
    private val server = MockWebServer()
    private val now = 1_800_000_000_000L
    private lateinit var session: TraktSession
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    @Before
    fun setUp() {
        server.start()
        session = TraktSession(tempStore(), testApi(server)) { now }
    }

    @After
    fun tearDown() = server.close()

    private fun source() = NetflixSource(testApi(server), session, OkHttpClient(), server.url("/sparql"))

    private fun answer(handler: (RecordedRequest) -> mockwebserver3.MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): mockwebserver3.MockResponse {
                requests += request
                return handler(request)
            }
        }
    }

    private fun item(kind: String, trakt: Int) = """{"type":"$kind","$kind":{"title":"T$trakt","ids":{"trakt":$trakt,"imdb":"tt$trakt"}}}"""

    /** Trakt's lists, as Trakt answers them with watchnow=netflix: only the titles on Netflix. */
    private fun traktNetflix(request: RecordedRequest) = when (request.url.encodedPath) {
        "/sync/watchlist/movies" -> json(200, "[${item("movie", 1)}]")
        "/sync/watchlist/shows" -> json(200, "[${item("show", 2)}]")
        "/recommendations/movies" -> json(200, """[{"title":"R3","ids":{"trakt":3,"imdb":"tt3"}}]""")
        "/recommendations/shows" -> json(200, "[]")
        // A paused title with a play on Netflix, and one that is not on Netflix.
        "/users/me/history/shows/4" -> json(200, """[{"id":1,"type":"episode"}]""")
        "/users/me/history/movies/5" -> json(200, "[]")
        else -> json(404)
    }

    @Test
    fun netflixTitlesComeFromTheWatchlistRecommendationsAndPausedTitles() = runBlocking {
        session.save(TokenResponse("access", "refresh", 86_400, now / 1000))
        answer(::traktNetflix)
        val paused = listOf(
            Title(TitleKind.Show, "Tales of the Quiet Coast", traktId = 4),
            Title(TitleKind.Movie, "Harbour Lights", traktId = 5),
            Title(TitleKind.Movie, "No Trakt id"),
        )
        val found = source().available(paused, session.generation.value)
        assertEquals(
            setOf(TitleKey(TitleKind.Movie, 1), TitleKey(TitleKind.Show, 2), TitleKey(TitleKind.Movie, 3), TitleKey(TitleKind.Show, 4)),
            found,
        )
        // Every Trakt call asks for Netflix only.
        assertTrue(requests.all { it.url.queryParameter("watchnow") == "netflix" })
        assertEquals(6, requests.size)
    }

    @Test
    fun aSignOutDuringTheCheckGivesNothing() = runBlocking {
        session.save(TokenResponse("access", "refresh", 86_400, now / 1000))
        answer(::traktNetflix)
        val old = session.generation.value
        session.signOut()
        assertNull(source().available(emptyList(), old))
    }

    @Test
    fun aTraktFailureIsAFailedCheck() {
        runBlocking { session.save(TokenResponse("access", "refresh", 86_400, now / 1000)) }
        answer { json(503) }
        assertThrows(IOException::class.java) { runBlocking { source().available(emptyList(), session.generation.value) } }
    }

    @Test
    fun wikidataIsAskedOnceForManyIdsWithAnHonestUserAgent() = runBlocking {
        answer {
            json(200, """{"results":{"bindings":[{"imdb":{"value":"tt1"},"netflix":{"value":"81424086"}}]}}""")
        }
        assertEquals(mapOf("tt1" to "81424086"), source().ids(listOf("tt1", "tt2")))
        val request = requests.single()
        assertEquals("GET", request.method)
        assertTrue(request.url.queryParameter("query")!!.contains("\"tt1\" \"tt2\""))
        assertEquals("application/sparql-results+json", request.headers["Accept"])
        assertTrue(request.headers["User-Agent"]!!.startsWith("open-tv-launcher/"))
    }

    @Test
    fun theUserAgentGivesTheBuildsContactOrNone() {
        assertEquals("open-tv-launcher/0.1.0 (https://example.org/tv)", userAgent("0.1.0", " https://example.org/tv "))
        assertEquals("open-tv-launcher/0.1.0", userAgent("0.1.0", ""))
    }

    @Test
    fun manyIdsAreAskedInGroups() = runBlocking {
        answer { json(200, """{"results":{"bindings":[]}}""") }
        source().ids((1..250).map { "tt$it" })
        assertEquals(3, requests.size)
    }

    @Test
    fun noIdsMeansNoRequest() = runBlocking {
        answer { json(200, """{"results":{"bindings":[]}}""") }
        assertEquals(emptyMap<String, String>(), source().ids(emptyList()))
        assertEquals(0, requests.size)
    }

    @Test
    fun aWikidataFailureIsAFailedRequest() {
        answer { json(429) }
        assertThrows(IOException::class.java) { runBlocking { source().ids(listOf("tt1")) } }
    }
}
