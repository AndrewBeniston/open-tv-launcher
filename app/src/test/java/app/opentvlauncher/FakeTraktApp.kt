package app.opentvlauncher

import app.opentvlauncher.home.HomeMapper
import app.opentvlauncher.netflix.NetflixSource
import app.opentvlauncher.system.TitleApp
import app.opentvlauncher.system.installHomeApps
import app.opentvlauncher.system.installTitleApps
import app.opentvlauncher.trakt.TokenResponse
import app.opentvlauncher.trakt.TraktApi
import app.opentvlauncher.trakt.historyAdded
import app.opentvlauncher.trakt.historyRemoved
import app.opentvlauncher.trakt.json
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The app signed in to a fake Trakt on a local server: four films in Continue Watching, Hollowby
 * in Top Picks, and Ember then River Hours in From Your Watchlist. Nothing reaches the real Trakt.
 * Four paused films keep the watchlist titles out of Continue Watching (TraktRules.continueFill).
 * Harbour Lights is on the watchlist too. [writes] keeps each POST and DELETE; with [refuseWrites]
 * Trakt answers them with 500.
 */
open class FakeTraktApp : LauncherApp() {
    val writes = CopyOnWriteArrayList<String>()

    @Volatile
    var refuseWrites = false

    /** Trakt's history has no plays to remove, as for The Glacier on a real account. */
    @Volatile
    var noPlaysToRemove = false

    val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when {
                request.method != "GET" -> {
                    writes += "${request.method} ${request.url.encodedPath}"
                    when {
                        refuseWrites -> json(500)
                        request.method == "DELETE" -> MockResponse.Builder().code(204).build()
                        request.url.encodedPath == "/sync/history" -> historyAdded(movies = 1)
                        request.url.encodedPath == "/sync/history/remove" -> historyRemoved(movies = if (noPlaysToRemove) 0 else 1)
                        else -> json(201)
                    }
                }
                request.url.encodedPath.startsWith("/sync/playback") -> json(
                    200,
                    """[{"id":70,"progress":53.0,"paused_at":"2099-01-01T20:00:00.000Z","type":"movie","movie":{"title":"Harbour Lights","year":2026,"ids":{"trakt":1,"imdb":"tt1"}}},""" +
                        """{"id":71,"progress":20.0,"paused_at":"2098-12-31T20:00:00.000Z","type":"movie","movie":{"title":"Tidewater","year":2025,"ids":{"trakt":11,"imdb":"tt11"}}},""" +
                        """{"id":72,"progress":14.0,"paused_at":"2098-12-30T20:00:00.000Z","type":"movie","movie":{"title":"Ironclaw","year":1987,"ids":{"trakt":12,"imdb":"tt12"}}},""" +
                        """{"id":73,"progress":62.0,"paused_at":"2098-12-29T20:00:00.000Z","type":"movie","movie":{"title":"The Glacier","year":2022,"ids":{"trakt":13,"imdb":"tt13"}}}]""",
                )
                request.url.encodedPath.startsWith("/recommendations/movies") ->
                    json(200, """[{"title":"Hollowby","year":2016,"ids":{"trakt":2,"imdb":"tt2"}}]""")
                request.url.encodedPath.startsWith("/sync/watchlist/movies") ->
                    json(
                        200,
                        """[{"rank":1,"listed_at":"2026-10-01T10:00:00.000Z","type":"movie","movie":{"title":"River Hours","year":1953,"ids":{"trakt":5,"imdb":"tt5"}}},""" +
                            """{"rank":2,"listed_at":"2026-10-20T10:00:00.000Z","type":"movie","movie":{"title":"Ember","year":1952,"ids":{"trakt":3,"imdb":"tt3"}}},""" +
                            """{"rank":3,"listed_at":"2026-10-21T10:00:00.000Z","type":"movie","movie":{"title":"Harbour Lights","year":2026,"ids":{"trakt":1,"imdb":"tt1"}}}]""",
                    )
                else -> json(200, "[]")
            }
        }
        start()
    }

    override val trakt: TraktApi by lazy { TraktApi(OkHttpClient(), "test-client", "test-secret", server.url("/")) }

    // Wikidata is asked on the local server too. Its answer cannot be read, so no title gets a Netflix id.
    override val netflix: NetflixSource by lazy { NetflixSource(trakt, session, OkHttpClient(), server.url("/sparql")) }

    /** The apps row's apps that this fake TV has: all of them. */
    protected open val tvApps: List<String> = HomeMapper.apps.mapNotNull { it.packageName }

    override fun onCreate() {
        super.onCreate()
        // The apps row's apps are installed, as on the TVs.
        installTitleApps(*TitleApp.entries.filter { it.packageName in tvApps }.toTypedArray())
        installHomeApps(*tvApps.toTypedArray())
        runBlocking { session.save(TokenResponse("access", "refresh", 86_400, System.currentTimeMillis() / 1000)) }
    }
}
