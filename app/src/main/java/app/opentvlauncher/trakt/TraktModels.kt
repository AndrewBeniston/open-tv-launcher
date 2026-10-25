package app.opentvlauncher.trakt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// The parts of Trakt's JSON that the launcher reads. Unknown fields are ignored (TraktApi.json).

@Serializable
data class TraktIds(val trakt: Int? = null, val slug: String? = null, val imdb: String? = null, val tmdb: Int? = null)

/** Trakt `extended=images`: each list holds paths with no scheme, such as `media.trakt.tv/...webp`. */
@Serializable
data class TraktImages(
    val fanart: List<String> = emptyList(),
    val poster: List<String> = emptyList(),
    val logo: List<String> = emptyList(),
    val thumb: List<String> = emptyList(),
    val screenshot: List<String> = emptyList(),
)

/** A film or a show. Trakt uses the same fields for both. */
@Serializable
data class TraktMedia(
    val title: String = "",
    val year: Int? = null,
    val ids: TraktIds = TraktIds(),
    val overview: String? = null,
    val runtime: Int? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val images: TraktImages? = null,
)

@Serializable
data class TraktEpisode(
    val season: Int = 0,
    val number: Int = 0,
    val title: String? = null,
    val ids: TraktIds = TraktIds(),
    val overview: String? = null,
    val images: TraktImages? = null,
    @SerialName("first_aired") val firstAired: String? = null,
)

/** `/sync/progress/up_next`: a show the user has started, newest watched first, with its next episode. */
@Serializable
data class TraktUpNext(val show: TraktMedia? = null, val progress: TraktShowProgress? = null)

@Serializable
data class TraktShowProgress(
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
    @SerialName("next_episode") val nextEpisode: TraktEpisode? = null,
)

/** `/users/hidden/progress_watched` and `/users/hidden/dropped`: a show hidden from progress. Only the ids are read. */
@Serializable
data class TraktHiddenShow(val show: TraktMedia? = null)

/** `/sync/playback`: an item paused part-way. [progress] is a percentage. [id] names the paused item for `DELETE /sync/playback/{id}`. */
@Serializable
data class TraktPlayback(
    val id: Long = 0,
    val progress: Float = 0f,
    @SerialName("paused_at") val pausedAt: String = "",
    val type: String = "",
    val movie: TraktMedia? = null,
    val episode: TraktEpisode? = null,
    val show: TraktMedia? = null,
)

/** `/sync/watchlist/{movies|shows}`. */
@Serializable
data class TraktListItem(
    val rank: Int? = null,
    @SerialName("listed_at") val listedAt: String? = null,
    val type: String = "",
    val movie: TraktMedia? = null,
    val show: TraktMedia? = null,
)

/** `/sync/watched/{movies|shows}`: only the ids are read. */
@Serializable
data class TraktWatched(val movie: TraktMedia? = null, val show: TraktMedia? = null)

/** `/sync/ratings/{movies|shows}`: a title the user rated, 1 to 10. Only the ids are read. */
@Serializable
data class TraktRated(val rating: Int = 0, val movie: TraktMedia? = null, val show: TraktMedia? = null)

/** `POST /sync/history`: how many plays Trakt added, and what it did not know. */
@Serializable
data class TraktHistoryAnswer(
    val added: TraktCounts = TraktCounts(),
    @SerialName("not_found") val notFound: TraktNotFound = TraktNotFound(),
)

@Serializable
data class TraktCounts(val movies: Int = 0, val episodes: Int = 0)

/**
 * Trakt's answer to a history removal: the plays it removed, and what it did not know. A count
 * that is missing stays null, apart from a real 0.
 */
@Serializable
data class TraktRemoveAnswer(
    val deleted: TraktRemoved? = null,
    @SerialName("not_found") val notFound: TraktNotFound = TraktNotFound(),
)

@Serializable
data class TraktRemoved(val movies: Int? = null, val episodes: Int? = null)

@Serializable
data class TraktNotFound(
    val movies: List<JsonElement> = emptyList(),
    val shows: List<JsonElement> = emptyList(),
    val seasons: List<JsonElement> = emptyList(),
    val episodes: List<JsonElement> = emptyList(),
) {
    val isEmpty: Boolean get() = movies.isEmpty() && shows.isEmpty() && seasons.isEmpty() && episodes.isEmpty()
}

/** `/sync/history/{movies|shows}/{id}`: one play, with its episode for a show. A field Trakt left out stays null. */
@Serializable
data class TraktHistoryItem(
    @SerialName("watched_at") val watchedAt: String? = null,
    val type: String? = null,
    val episode: TraktEpisode? = null,
)
