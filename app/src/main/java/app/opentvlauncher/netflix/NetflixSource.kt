package app.opentvlauncher.netflix

import app.opentvlauncher.BuildConfig
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleKey
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.trakt.TraktApi
import app.opentvlauncher.trakt.TraktListItem
import app.opentvlauncher.trakt.TraktMedia
import app.opentvlauncher.trakt.TraktSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Reads which titles are on Netflix, and their Netflix ids (netflix-play research, option 1).
 *
 * Trakt's `watchnow=netflix` filter answers for the account's region. It matched JustWatch's UK
 * data on all 91 titles checked on 25 Oct 2026. It works on lists, not on one title, and
 * `/sync/playback` takes no filter, so a paused title is checked through its own history: that
 * finds it only when it has at least one play.
 */
class NetflixSource(
    private val api: TraktApi,
    private val session: TraktSession,
    private val http: OkHttpClient,
    private val wikidata: HttpUrl = "https://query.wikidata.org/sparql".toHttpUrl(),
    private val userAgent: String = userAgent(BuildConfig.VERSION_NAME, BuildConfig.WIKIDATA_CONTACT),
) {
    /**
     * The titles on Netflix: watchlist, recommendations and [continueWatching]. Null after a
     * sign-in or sign-out since [generation]. A failed request throws IOException.
     */
    suspend fun available(continueWatching: List<Title>, generation: Long): Set<TitleKey>? = session.call(generation) { token ->
        coroutineScope {
            val watchlistMovies = async { api.getAll("sync/watchlist/movies", token, TraktListItem.serializer(), NETFLIX) }
            val watchlistShows = async { api.getAll("sync/watchlist/shows", token, TraktListItem.serializer(), NETFLIX) }
            val recommendedMovies = async { api.get("recommendations/movies", token, MEDIA_LIST, NETFLIX + RECOMMENDATIONS) }
            val recommendedShows = async { api.get("recommendations/shows", token, MEDIA_LIST, NETFLIX + RECOMMENDATIONS) }
            val paused = async { onNetflix(continueWatching, token) }
            buildSet {
                watchlistMovies.await().forEach { item -> item.movie.key(TitleKind.Movie)?.let(::add) }
                watchlistShows.await().forEach { item -> item.show.key(TitleKind.Show)?.let(::add) }
                recommendedMovies.await().forEach { media -> media.key(TitleKind.Movie)?.let(::add) }
                recommendedShows.await().forEach { media -> media.key(TitleKind.Show)?.let(::add) }
                addAll(paused.await())
            }
        }
    }

    /**
     * Which of [continueWatching] are on Netflix, from each title's own Trakt history: for titles
     * that join Continue Watching between daily checks. Null after a sign-in or sign-out since
     * [generation]. A failed request throws IOException.
     */
    suspend fun continueAvailable(continueWatching: List<Title>, generation: Long): Set<TitleKey>? =
        session.call(generation) { token -> onNetflix(continueWatching, token) }

    /** A title in Continue Watching has plays, so Trakt's Netflix filter on its history tells. */
    private suspend fun onNetflix(titles: List<Title>, token: String): Set<TitleKey> = coroutineScope {
        titles.mapNotNull { it.key }.distinct().map { key ->
            async {
                val group = if (key.kind == TitleKind.Movie) "movies" else "shows"
                val plays = api.get("users/me/history/$group/${key.traktId}", token, PLAYS, NETFLIX + mapOf("limit" to "1"))
                key.takeIf { plays.isNotEmpty() }
            }
        }.awaitAll().filterNotNull().toSet()
    }

    /** Netflix ids by IMDb id, from Wikidata, in groups of [GROUP]. A failed request throws IOException. */
    suspend fun ids(imdbIds: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        imdbIds.distinct().chunked(GROUP).fold(emptyMap()) { found, group ->
            val query = NetflixRules.wikidataQuery(group) ?: return@fold found
            found + NetflixRules.parseWikidata(fetch(query))
        }
    }

    private fun fetch(query: String): String {
        val url = wikidata.newBuilder().addQueryParameter("query", query).addQueryParameter("format", "json").build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/sparql-results+json")
            // Wikimedia asks every client to name itself and give a contact.
            .header("User-Agent", userAgent)
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Wikidata query failed: HTTP ${response.code}")
            response.body.string()
        }
    }

    private fun TraktMedia?.key(kind: TitleKind) = this?.ids?.trakt?.let { TitleKey(kind, it) }

    private companion object {
        val NETFLIX = mapOf("watchnow" to "netflix")

        // The recommendation calls' own settings (TraktRowsSource), with room for the filter.
        val RECOMMENDATIONS = mapOf("limit" to "100", "ignore_collected" to "true", "ignore_watchlisted" to "true")
        val MEDIA_LIST = ListSerializer(TraktMedia.serializer())

        // Only whether a play is there is read.
        val PLAYS = ListSerializer(JsonElement.serializer())

        // About 1.4 KB of ids in each query address.
        const val GROUP = 100
    }
}

/** "open-tv-launcher/0.1.0 (contact)", or without the brackets when the build sets no contact. */
fun userAgent(version: String, contact: String): String =
    "open-tv-launcher/$version" + contact.trim().takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
