package app.opentvlauncher.home

import app.opentvlauncher.trakt.TraktEpisode
import app.opentvlauncher.trakt.TraktImages
import app.opentvlauncher.trakt.TraktListItem
import app.opentvlauncher.trakt.TraktMedia
import app.opentvlauncher.trakt.TraktPlayback
import app.opentvlauncher.trakt.TraktUpNext
import java.time.Instant

/**
 * The rules for the Trakt rows (technical plan, "Rules folded in"):
 * - Continue Watching shows only the latest paused episode of each show.
 * - It hides items paused more than [pausedLimitDays] days ago: 180 by default (24 Oct 2026,
 *   after 13 of his 14 paused items were older than the first 60-day rule).
 * - It also shows each started show's next aired episode, up next, in one row with the paused
 *   items, as Trakt's own Continue Watching does (25 Oct 2026). See [continueWatching].
 * - Top Picks for You and From Your Watchlist hide titles that are already watched or rated,
 *   and titles in Continue Watching.
 */
object TraktRules {
    const val PAUSED_LIMIT_DAYS = 180
    const val PICKS_LIMIT = 30
    const val WATCHLIST_LIMIT = 40
    const val SCREENSAVER_WATCHLIST_LIMIT = 40

    /** Continue Watching fills with watchlist titles below this many cards, paused or up next, up to [FILL_TO] cards. */
    const val FILL_BELOW = 4
    const val FILL_TO = 8

    /**
     * The paused items, and [upNext]: one card for each show, the paused episode when there is
     * one, otherwise the next episode. Up next leaves out shows hidden from progress or dropped
     * ([hiddenShows], Trakt ids), episodes that have not aired, and shows last watched more than
     * [pausedLimitDays] days ago. Newest activity first: when it was paused, or when the show's
     * last episode was watched.
     */
    fun continueWatching(
        playback: List<TraktPlayback>,
        now: Long,
        pausedLimitDays: Int = PAUSED_LIMIT_DAYS,
        upNext: List<TraktUpNext> = emptyList(),
        hiddenShows: Set<Int> = emptySet(),
    ): List<Title> {
        val oldest = now - pausedLimitDays * DAY_MILLIS
        val paused = paused(playback, oldest)
        val pausedShows = paused.filter { it.kind == TitleKind.Show }.mapNotNull { it.traktId }.toSet()
        val next = upNext.mapNotNull { item ->
            val show = item.show ?: return@mapNotNull null
            val id = show.ids.trakt ?: return@mapNotNull null
            if (id in hiddenShows || id in pausedShows) return@mapNotNull null
            val progress = item.progress ?: return@mapNotNull null
            val episode = progress.nextEpisode?.takeIf { it.number > 0 } ?: return@mapNotNull null
            val watchedAt = progress.lastWatchedAt?.let(::parseInstant) ?: return@mapNotNull null
            if (watchedAt < oldest) return@mapNotNull null
            if ((episode.firstAired?.let(::parseInstant) ?: Long.MIN_VALUE) > now) return@mapNotNull null
            show.toTitle(TitleKind.Show).let { title ->
                title.copy(
                    episode = episode.toRef(),
                    upNextAt = watchedAt,
                    images = title.images.copy(still = episode.images?.screenshot?.firstOrNull()?.let(::https)),
                )
            }
        }.distinctBy { it.traktId }
        return (paused + next).sortedByDescending { it.pausedAt ?: it.upNextAt }
    }

    /**
     * [continueWatching] without the up-next cards for episodes in [watched] (a show's key and
     * the episode marked watched on the TV), or for an episode before it: Trakt can still name
     * the episode as next just after the play.
     */
    fun withoutWatched(continueWatching: List<Title>, watched: Map<TitleKey, EpisodeRef>): List<Title> = continueWatching.filterNot { title ->
        val episode = title.episode
        val played = title.key?.let(watched::get)
        title.upNextAt != null && episode != null && played != null &&
            (episode.season < played.season || (episode.season == played.season && episode.number <= played.number))
    }

    private fun paused(playback: List<TraktPlayback>, oldest: Long): List<Title> {
        return playback
            .mapNotNull { item ->
                val pausedAt = parseInstant(item.pausedAt) ?: return@mapNotNull null
                if (pausedAt < oldest) return@mapNotNull null
                val progress = (item.progress / 100f).coerceIn(0f, 1f)
                val ids = listOfNotNull(item.id.takeIf { it > 0 })
                when {
                    item.movie != null -> item.movie.toTitle(TitleKind.Movie).copy(progress = progress, pausedAt = pausedAt, playbackIds = ids)
                    item.show != null && item.episode != null -> item.show.toTitle(TitleKind.Show).let { show ->
                        show.copy(
                            episode = item.episode.toRef(),
                            progress = progress,
                            pausedAt = pausedAt,
                            playbackIds = ids,
                            images = show.images.copy(still = item.episode.images?.screenshot?.firstOrNull()?.let(::https)),
                        )
                    }
                    else -> null
                }
            }
            .sortedByDescending { it.pausedAt }
            // The latest episode of each show: the list is newest first, so the first of each
            // shows. The card keeps the playback ids of every paused item behind it.
            .groupBy { if (it.kind == TitleKind.Show) "show:${it.traktId ?: it.title}" else "movie:${it.traktId ?: it.title}" }
            .values
            .map { same -> same.first().copy(playbackIds = same.flatMap { it.playbackIds }) }
    }

    /**
     * Top Picks for You: Trakt's personal recommendations only, films and shows in turn, in
     * Trakt's order (24 Oct 2026). Titles that are watched, rated or in Continue Watching
     * are left out.
     */
    fun picks(
        recommendedMovies: List<TraktMedia>,
        recommendedShows: List<TraktMedia>,
        seen: Set<TitleKey>,
        continueWatching: List<Title>,
        limit: Int = PICKS_LIMIT,
    ): List<Title> {
        val keep = keeper(seen, continueWatching)
        val titles = recommendedMovies.map { it.toTitle(TitleKind.Movie) } + recommendedShows.map { it.toTitle(TitleKind.Show) }
        return filmsAndShowsInTurn(titles.filter(keep))
            .map { it.copy(source = PickSource.Recommended) }
            .distinctBy { it.identity() }
            .take(limit)
    }

    /**
     * Films and shows in turn, each in its own order. A refresh and a change on the TV both use
     * it after they leave titles out, so a hidden title never puts two films or two shows together.
     */
    fun filmsAndShowsInTurn(titles: List<Title>): List<Title> =
        interleave(titles.filter { it.kind == TitleKind.Movie }, titles.filter { it.kind == TitleKind.Show })

    /**
     * From Your Watchlist: films and shows, the most recently added first. Titles that are
     * watched, rated or in Continue Watching are left out.
     */
    fun watchlistRow(
        watchlist: List<TraktListItem>,
        seen: Set<TitleKey>,
        continueWatching: List<Title>,
        limit: Int = WATCHLIST_LIMIT,
    ): List<Title> {
        val keep = keeper(seen, continueWatching)
        val newestFirst = watchlist.sortedByDescending { item -> item.listedAt?.let(::parseInstant) ?: Long.MIN_VALUE }
        return watchlistTitles(newestFirst).filter(keep).distinctBy { it.identity() }.take(limit)
    }

    /**
     * Option B, "fill when short" (24 Oct 2026): with 1 to 3 Continue Watching cards,
     * Continue Watching goes on with the newest titles of [watchlistRow] up to [FILL_TO] cards.
     * Up-next cards count as well as paused items (25 Oct 2026). With no cards the row
     * goes, so nothing fills it. The watchlist row is newest first and already leaves out
     * Continue Watching titles.
     */
    fun continueFill(continueWatching: List<Title>, watchlistRow: List<Title>): List<Title> =
        if (continueWatching.size in 1 until FILL_BELOW) watchlistRow.take(FILL_TO - continueWatching.size) else emptyList()

    /** True when [title] may show in Top Picks or From Your Watchlist: not watched, rated or in Continue Watching. */
    fun mayShow(title: Title, seen: Set<TitleKey>, continueWatching: List<Title>): Boolean = keeper(seen, continueWatching)(title)

    private fun keeper(seen: Set<TitleKey>, continueWatching: List<Title>): (Title) -> Boolean {
        val hidden = seen + continueWatching.mapNotNull { it.key }
        return { title -> title.key.let { it == null || it !in hidden } }
    }

    /**
     * The watchlist titles the screensaver can fall back to: those with a backdrop (hasBackdrop),
     * the same rule the screensaver uses, so a refresh and a change on the TV keep the same titles.
     */
    fun screensaverWatchlist(titles: List<Title>): List<Title> =
        titles.filter { it.hasBackdrop() }.distinctBy { it.identity() }.take(SCREENSAVER_WATCHLIST_LIMIT)

    fun watchlistTitles(watchlist: List<TraktListItem>): List<Title> = watchlist.mapNotNull { item ->
        when {
            item.movie != null -> item.movie.toTitle(TitleKind.Movie)
            item.show != null -> item.show.toTitle(TitleKind.Show)
            else -> null
        }?.copy(source = PickSource.Watchlist)
    }

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> =
        (0 until maxOf(a.size, b.size)).flatMap { listOfNotNull(a.getOrNull(it), b.getOrNull(it)) }

    private fun Title.identity() = "${kind.name}:${traktId ?: imdb ?: title}"

    private fun parseInstant(text: String): Long? = runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}

fun TraktMedia.toTitle(kind: TitleKind) = Title(
    kind = kind,
    title = title,
    year = year,
    imdb = ids.imdb,
    traktId = ids.trakt,
    slug = ids.slug,
    overview = overview?.takeIf { it.isNotBlank() },
    runtime = runtime?.takeIf { it > 0 },
    genres = genres,
    rating = rating?.takeIf { it > 0 },
    images = images.toTitleImages(),
)

private fun TraktEpisode.toRef() = EpisodeRef(season, number, title, overview?.takeIf { it.isNotBlank() })

/** Trakt's image paths have no scheme. */
fun TraktImages?.toTitleImages() = TitleImages(
    fanart = this?.fanart?.firstOrNull()?.let(::https),
    poster = this?.poster?.firstOrNull()?.let(::https),
    logo = this?.logo?.firstOrNull()?.let(::https),
    thumb = this?.thumb?.firstOrNull()?.let(::https),
    fanartAlt = this?.fanart?.getOrNull(1)?.let(::https),
)

private fun https(path: String) = if (path.startsWith("http")) path else "https://$path"
