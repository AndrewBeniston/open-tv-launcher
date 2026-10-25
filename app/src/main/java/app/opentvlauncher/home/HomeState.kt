package app.opentvlauncher.home

import kotlinx.serialization.Serializable

/**
 * Everything the home screen needs, saved as a small JSON file after each refresh. The first
 * frame draws the saved copy, so it never waits for the network (technical plan).
 */
@Serializable
data class HomeState(
    /** The last good Trakt rows. Null until Trakt answers once. */
    val trakt: TraktRows? = null,
    /** When the last Trakt refresh failed. Null after a refresh that worked. */
    val traktFailedAt: Long? = null,
    /** "Recommended" from the YouTube app's TV channel. */
    val youtube: List<Video> = emptyList(),
    /** "Continue Watching" from Stremio's TV channel: the fallback when there are no Trakt rows. */
    val stremio: List<Video> = emptyList(),
    /** Which Trakt titles are on Netflix, and their Netflix ids (netflix-play research, option 1). */
    val netflix: NetflixState = NetflixState(),
)

/**
 * What the Netflix button needs. Trakt's `watchnow=netflix` filter says which titles are on
 * Netflix in the Trakt account's region, checked once a day. Wikidata gives each title's Netflix
 * id, which is kept for good: a title's id does not change.
 */
@Serializable
data class NetflixState(
    /** The sign-in whose account [available] is for (TraktSession.SignedIn.id). */
    val signIn: String? = null,
    /** When the last check finished, Trakt and Wikidata both. Null until one does. */
    val checkedAt: Long? = null,
    /** When Trakt last said which titles are on Netflix. Its answer counts for 7 days (NetflixRules.known). */
    val availableAt: Long? = null,
    /** The titles that Trakt says are on Netflix. */
    val available: Set<TitleKey> = emptySet(),
    /** Netflix title ids by IMDb id. */
    val ids: Map<String, String> = emptyMap(),
    /**
     * The Continue Watching titles checked since the last daily check: a title that joins the row
     * later is checked at once (NetflixRules.unchecked).
     */
    val continueChecked: Set<TitleKey> = emptySet(),
    /**
     * The titles in [available] from a new-title check whose ids have not come yet. The next
     * check of such a title replaces its answer, so a negative removes it (merge gate C93-1). A
     * daily answer is never provisional: its watchlist and recommendation lists find titles on
     * Netflix that have no play yet, which a history check cannot.
     */
    val provisional: Set<TitleKey> = emptySet(),
) {
    /**
     * After a sign-out, or for another sign-in: what is on Netflix belongs to the account's
     * region, but the ids stay.
     */
    fun signedOut() = NetflixState(ids = ids)

    /** After 7 days with no answer from Trakt: what is on Netflix is not known. */
    fun unknown() = copy(availableAt = null, available = emptySet(), continueChecked = emptySet(), provisional = emptySet())
}

@Serializable
data class TraktRows(
    val savedAt: Long,
    val continueWatching: List<Title> = emptyList(),
    /** Top Picks for You: Trakt's recommendations only. */
    val picks: List<Title> = emptyList(),
    /** From Your Watchlist: the row, newest first, without watched or rated titles. */
    val watchlistRow: List<Title> = emptyList(),
    /** Watchlist titles for the screensaver. */
    val watchlist: List<Title> = emptyList(),
    /** Watched or rated titles, so a change made on the TV follows the same row rules as a refresh. */
    val seen: Set<TitleKey> = emptySet(),
    /** Every title on the watchlist, for the details page's watchlist button. */
    val watchlistKeys: Set<TitleKey> = emptySet(),
)

enum class TitleKind { Movie, Show }

/** A Trakt id names a title only with its kind: a film and a show can have the same number. */
@Serializable
data class TitleKey(val kind: TitleKind, val traktId: Int)

enum class PickSource { Watchlist, Recommended }

/** A film or a show from Trakt, with what the home screen and the details page show. */
@Serializable
data class Title(
    val kind: TitleKind,
    val title: String,
    val year: Int? = null,
    val imdb: String? = null,
    val traktId: Int? = null,
    val slug: String? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val images: TitleImages = TitleImages(),
    /**
     * Continue Watching: the episode that is paused, its progress (0 to 1) and when it was paused.
     * For an up-next card, the show's next episode, with no progress.
     */
    val episode: EpisodeRef? = null,
    val progress: Float? = null,
    val pausedAt: Long? = null,
    /** Continue Watching: for an up-next card, when the show's last episode was watched. */
    val upNextAt: Long? = null,
    /**
     * Continue Watching: Trakt's ids for every paused item behind the card, the newest first. A
     * show's card stands for all of its paused episodes, so Reset Progress clears them all.
     */
    val playbackIds: List<Long> = emptyList(),
    /** Top Picks: where the pick came from. */
    val source: PickSource? = null,
    /**
     * Netflix's id for the title, when it is on Netflix and the id is known. The screen sets it
     * from [NetflixState] (NetflixRules.withIds); the saved rows do not hold it.
     */
    val netflixId: String? = null,
) {
    val key: TitleKey? get() = traktId?.let { TitleKey(kind, it) }
}

@Serializable
data class EpisodeRef(val season: Int, val number: Int, val title: String? = null, val overview: String? = null)

/** Trakt image addresses, the fallback after Metahub. Each is a full https address. */
@Serializable
data class TitleImages(
    val fanart: String? = null,
    val poster: String? = null,
    val logo: String? = null,
    /** Trakt's landscape art with the title on it. */
    val thumb: String? = null,
    /** A second backdrop, when Trakt has one. */
    val fanartAlt: String? = null,
    /** Continue Watching: a still from the paused episode. */
    val still: String? = null,
)

/** A program from another app's TV channel. */
@Serializable
data class Video(
    val title: String,
    val subtitle: String? = null,
    /** The card's picture: the program's thumbnail, or its poster art. */
    val image: String? = null,
    /** The program's poster art, when it is a different picture from [image]. */
    val poster: String? = null,
    /** The IMDb id, when the app's link or content id names one. */
    val imdb: String? = null,
    /** The app's own link to open the program. */
    val intentUri: String? = null,
    val durationMillis: Long? = null,
    val progress: Float? = null,
)
