package app.opentvlauncher.trakt

import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleKey
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.home.TraktRows
import app.opentvlauncher.home.TraktRules
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.time.Instant
import kotlin.math.abs

/** What Trakt said about a play the launcher added (TraktRowsSource.addPlay). */
enum class PlayResult { Added, NotFound, Unclear }

/**
 * Reads the Trakt rows: paused items, up next, recommendations, the watchlist, and the watched and
 * rated lists, in parallel. The watchlist, watched and rated lists are read to their last page.
 * Returns null when the TV is not signed in, or when a sign-in or sign-out has happened since
 * [generation] (TraktSession.generation).
 */
class TraktRowsSource(private val api: TraktApi, private val session: TraktSession) {
    suspend fun fetch(now: Long, generation: Long): TraktRows? = session.call(generation) { token ->
        coroutineScope {
            val full = mapOf("extended" to "full,images")
            val playback = async { api.get("sync/playback", token, ListSerializer(TraktPlayback.serializer()), full) }
            // Newest watched first, so one page reaches well past the 180 days Continue Watching shows.
            val upNext = async { api.get("sync/progress/up_next", token, ListSerializer(TraktUpNext.serializer()), full + UP_NEXT) }
            // Trakt's up next still lists them.
            val hiddenProgress = async { api.getAll("users/hidden/progress_watched", token, TraktHiddenShow.serializer(), mapOf("type" to "show")) }
            val dropped = async { api.getAll("users/hidden/dropped", token, TraktHiddenShow.serializer()) }
            val recommendedMovies = async { api.get("recommendations/movies", token, MEDIA_LIST, full + RECOMMENDATIONS) }
            val recommendedShows = async { api.get("recommendations/shows", token, MEDIA_LIST, full + RECOMMENDATIONS) }
            val watchlistMovies = async { api.getAll("sync/watchlist/movies", token, TraktListItem.serializer(), full) }
            val watchlistShows = async { api.getAll("sync/watchlist/shows", token, TraktListItem.serializer(), full) }
            val watchedMovies = async { api.getAll("sync/watched/movies", token, TraktWatched.serializer()) }
            val watchedShows = async { api.getAll("sync/watched/shows", token, TraktWatched.serializer(), mapOf("extended" to "noseasons")) }
            val ratedMovies = async { api.getAll("sync/ratings/movies", token, TraktRated.serializer()) }
            val ratedShows = async { api.getAll("sync/ratings/shows", token, TraktRated.serializer()) }

            val hiddenShows = (hiddenProgress.await() + dropped.await()).mapNotNull { it.show?.ids?.trakt }.toSet()
            val continueWatching = TraktRules.continueWatching(playback.await(), now, upNext = upNext.await(), hiddenShows = hiddenShows)
            val watchlist = watchlistMovies.await() + watchlistShows.await()
            // Watched or rated: the user has seen it, so Top Picks leaves it out. A rating without a
            // play happens, such as Transformers at 10/10 with no play on Trakt.
            val seen = (watchedMovies.await().mapNotNull { it.movie.key(TitleKind.Movie) } +
                watchedShows.await().mapNotNull { it.show.key(TitleKind.Show) } +
                ratedMovies.await().mapNotNull { it.movie.key(TitleKind.Movie) } +
                ratedShows.await().mapNotNull { it.show.key(TitleKind.Show) }).toSet()
            TraktRows(
                savedAt = now,
                continueWatching = continueWatching,
                picks = TraktRules.picks(recommendedMovies.await(), recommendedShows.await(), seen, continueWatching),
                watchlistRow = TraktRules.watchlistRow(watchlist, seen, continueWatching),
                watchlist = TraktRules.screensaverWatchlist(TraktRules.watchlistTitles(watchlist)),
                seen = seen,
                watchlistKeys = watchlist.mapNotNull { it.movie.key(TitleKind.Movie) ?: it.show.key(TitleKind.Show) }.toSet(),
            )
        }
    }

    /**
     * Adds [title] to the watchlist ([on]) or removes it, in [generation] only. False after a
     * sign-in or sign-out since then, or when Trakt refuses. A network failure throws IOException.
     */
    suspend fun setWatchlisted(title: Title, on: Boolean, generation: Long): Boolean {
        val id = title.traktId ?: return false
        val group = if (title.kind == TitleKind.Movie) "movies" else "shows"
        val body = buildJsonObject {
            putJsonArray(group) { addJsonObject { putJsonObject("ids") { put("trakt", id) } } }
        }
        val path = if (on) "sync/watchlist" else "sync/watchlist/remove"
        val code = session.call(generation) { token -> api.postAuthorized(path, token, body) } ?: return false
        return code in 200..299
    }

    /**
     * Removes every play of [title] from the history (`/sync/history/remove`), in [generation]
     * only, and gives how many Trakt removed: 0 when it had none. Null for a show with no
     * episode, after a sign-in or sign-out since then, when Trakt refuses, does not know the
     * title, or sends no count. A network failure, or an answer that cannot be read, throws.
     */
    suspend fun removePlays(title: Title, generation: Long): Int? {
        val body = historyBody(title, watchedAt = null) ?: return null
        val (code, answer) = session.call(generation) { token -> api.postReading("sync/history/remove", token, body) } ?: return null
        if (code !in 200..299) return null
        val removed = api.read(TraktRemoveAnswer.serializer(), answer)
        if (!removed.notFound.isEmpty) return null
        // Only a count Trakt sent shows how many plays went.
        val count = removed.deleted?.let { if (title.episode == null) it.movies else it.episodes } ?: return null
        return count.takeIf { it >= 0 }
    }

    /**
     * Adds one play of [title], watched at [watchedAt] (ISO 8601), to the history
     * (`POST /sync/history`), in [generation] only. Trakt does not stop a second play of the same
     * title, so the answer decides:
     * - [PlayResult.Added]: Trakt counted it.
     * - [PlayResult.NotFound]: Trakt does not know the title or the episode. Nothing was added.
     * - [PlayResult.Unclear]: the answer was lost, refused or could not be read. The play may be
     *   on Trakt, so a retry checks the history first ([hasPlay]).
     * Null for a show with no episode, or after a sign-in or sign-out since then.
     */
    suspend fun addPlay(title: Title, watchedAt: String, generation: Long): PlayResult? {
        val body = historyBody(title, watchedAt) ?: return null
        val (code, answer) = try {
            session.call(generation) { token -> api.postReading("sync/history", token, body) } ?: return null
        } catch (_: IOException) {
            return PlayResult.Unclear
        }
        if (code !in 200..299) return PlayResult.Unclear
        val counted = try {
            api.read(TraktHistoryAnswer.serializer(), answer)
        } catch (_: IOException) {
            return PlayResult.Unclear
        }
        val added = if (title.episode == null) counted.added.movies else counted.added.episodes
        return when {
            added > 0 -> PlayResult.Added
            !counted.notFound.isEmpty -> PlayResult.NotFound
            else -> PlayResult.Unclear
        }
    }

    /**
     * Whether Trakt's history has the play of [title] watched at [watchedAt], within a second:
     * `/sync/history/{movies|shows}/{id}`, every page, for that second only. Null when Trakt
     * cannot say: a page failed, the list was too long, a play lacks the time or the episode it
     * needs to be checked, or a sign-in or sign-out has happened since. Only a complete answer
     * can show that the play is not there.
     */
    suspend fun hasPlay(title: Title, watchedAt: String, generation: Long): Boolean? {
        val id = title.traktId ?: return null
        val episode = title.episode
        val at = Instant.parse(watchedAt)
        val group = if (episode == null) "movies" else "shows"
        val window = mapOf("start_at" to at.minusSeconds(1).toString(), "end_at" to at.plusSeconds(1).toString())
        val plays = try {
            session.call(generation) { token -> api.getComplete("sync/history/$group/$id", token, TraktHistoryItem.serializer(), window, HISTORY_PAGES) } ?: return null
        } catch (_: IOException) {
            return null
        }
        var found = false
        for (play in plays) {
            val time = play.watchedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
            val sameEpisode = if (episode == null) {
                true
            } else {
                val played = play.episode?.takeIf { it.number > 0 } ?: return null
                played.season == episode.season && played.number == episode.number
            }
            if (sameEpisode && abs(time.epochSecond - at.epochSecond) <= 1) found = true
        }
        return found
    }

    /** A film by its id; a Continue Watching episode by its show, season and number. Null for a show with no episode. */
    private fun historyBody(title: Title, watchedAt: String?) = title.traktId?.let { id ->
        val episode = title.episode
        if (title.kind == TitleKind.Show && episode == null) return null
        buildJsonObject {
            if (episode == null) {
                putJsonArray("movies") {
                    addJsonObject {
                        putJsonObject("ids") { put("trakt", id) }
                        watchedAt?.let { put("watched_at", it) }
                    }
                }
            } else {
                putJsonArray("shows") {
                    addJsonObject {
                        putJsonObject("ids") { put("trakt", id) }
                        putJsonArray("seasons") {
                            addJsonObject {
                                put("number", episode.season)
                                putJsonArray("episodes") {
                                    addJsonObject {
                                        put("number", episode.number)
                                        watchedAt?.let { put("watched_at", it) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Removes the paused items [ids] (`DELETE /sync/playback/{id}`), in [generation] only. An item
     * that is already gone (404) counts as removed, so a retry after a part failure works. False
     * after a sign-in or sign-out since then, or when Trakt refuses. A network failure throws.
     */
    suspend fun removePlayback(ids: List<Long>, generation: Long): Boolean {
        for (id in ids) {
            val code = session.call(generation) { token -> api.deleteAuthorized("sync/playback/$id", token) } ?: return false
            if (code !in 200..299 && code != 404) return false
        }
        return true
    }

    private fun TraktMedia?.key(kind: TitleKind) = this?.ids?.trakt?.let { TitleKey(kind, it) }

    private companion object {
        val MEDIA_LIST = ListSerializer(TraktMedia.serializer())

        // Plays within two seconds of one time: more than a page or two only after a whole
        // season or show was marked watched at once.
        const val HISTORY_PAGES = 10
        val UP_NEXT = mapOf("limit" to "100")

        // 30 of each kind, so enough remain after the watched and rated titles are left out.
        val RECOMMENDATIONS = mapOf("limit" to "30", "ignore_collected" to "true", "ignore_watchlisted" to "true")
    }
}
