package app.opentvlauncher.home

import app.opentvlauncher.data.AppKind
import app.opentvlauncher.data.AppTile
import app.opentvlauncher.data.Art
import app.opentvlauncher.data.BannerSlide
import app.opentvlauncher.data.CONTINUE_WATCHING_ROW
import app.opentvlauncher.data.CardAction
import app.opentvlauncher.data.ContentRow
import app.opentvlauncher.data.HomeContent
import app.opentvlauncher.data.PosterCard
import app.opentvlauncher.data.PosterRow
import app.opentvlauncher.data.SlideKind
import app.opentvlauncher.data.WideCard
import app.opentvlauncher.data.WideRow
import app.opentvlauncher.netflix.NetflixRules
import app.opentvlauncher.tv.TvChannels
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Stremio's own image service, keyed by IMDb id, with no key (technical plan, "Artwork").
 * Sizes measured on 24 Oct 2026: background small 480 × 270 and medium 1920 × 1080, poster
 * small 300 × 450, logo 800 × 310. Each use takes the smallest size that is still sharp.
 */
object Metahub {
    const val SMALL = "small"
    const val MEDIUM = "medium"

    fun url(kind: String, imdb: String, size: String = MEDIUM) = "https://images.metahub.space/$kind/$size/$imdb/img"
}

/** The 16:9 backdrop: Metahub first, then Trakt's fanart. Small for a card, medium for the banner. */
fun Title.backdropArt(size: String = Metahub.MEDIUM) = Art(listOfNotNull(imdb?.let { Metahub.url("background", it, size) }, images.fanart))

/** True when the title has a backdrop: an IMDb id for Metahub, or Trakt's fanart. The screensaver's one rule. */
fun Title.hasBackdrop() = backdropArt().urls.isNotEmpty()

/**
 * A Continue Watching card: a different picture from the banner's backdrop, so the two do not
 * repeat. The paused episode's still, then Trakt's titled landscape art, then a second backdrop.
 * The backdrop itself comes last, for a title with none of these.
 */
fun Title.cardArt() = Art(
    listOfNotNull(images.still, images.thumb, images.fanartAlt, imdb?.let { Metahub.url("background", it, Metahub.SMALL) }, images.fanart),
)

fun Title.posterArt() = Art(listOfNotNull(imdb?.let { Metahub.url("poster", it, Metahub.SMALL) }, images.poster))

/**
 * The title logo, a transparent image: details page and screensaver. Only Trakt's logo is used
 * (24 Oct 2026). Metahub's logo can be in another language, such as Italian, and Metahub
 * does not say which. With no Trakt logo, the title shows as text.
 */
fun Title.logoArt() = Art(listOfNotNull(images.logo))

/**
 * Builds the screen from the saved HomeState (design, "Data behind each element").
 * - Banner: up to 3 Continue Watching slides, then one Top Pick and one watchlist title in turn,
 *   up to 3 of each. Without Trakt rows, Stremio's own Continue Watching channel fills the
 *   Continue Watching slides.
 * - Rows, under the apps: Continue Watching, Recommended on YouTube, Top Picks for You, From Your
 *   Watchlist (24 Oct 2026). An empty row is left out.
 * - A short Continue Watching row goes on with the newest watchlist titles (TraktRules.continueFill).
 *   From Your Watchlist then starts after them, so no title shows twice.
 * - The Movies and Shows tabs ([kind]) filter the same Trakt lists to films or shows (Up Next
 *   exploration, "What each tab shows"). They have no apps row and no YouTube row, and without
 *   Trakt rows they are empty.
 */
object HomeMapper {
    private const val CONTINUE_SLIDES = 3
    private const val PICK_SLIDES = 3
    private const val WATCHLIST_SLIDES = 3

    val apps = listOf(
        AppTile(AppKind.YouTube, "YouTube", TvChannels.YOUTUBE),
        AppTile(AppKind.Stremio, "Stremio", TvChannels.STREMIO),
        AppTile(AppKind.Netflix, "Netflix", "com.netflix.ninja"),
        AppTile(AppKind.IPlayer, "BBC iPlayer", "bbc.iplayer.android"),
        AppTile(AppKind.AllApps, "All Apps", null),
    )

    /**
     * The apps row: the apps of [apps] that the TV has ([installed]), then All Apps, each tile at its
     * own size. Before the TV's apps are read ([installed] is null), every app shows.
     */
    fun appsRow(installed: Set<String>?): List<AppTile> =
        apps.filter { it.packageName == null || installed == null || it.packageName in installed }

    /**
     * What Home and the Movies and Shows tabs show. [signIn] is the current sign-in's id
     * (TraktSession.signInId): only its own Netflix answer gives a title a Netflix button. Null
     * gives none. [installed] is the apps row's apps that the TV has ([appsRow]).
     */
    fun content(
        saved: HomeState,
        zone: ZoneId = ZoneId.systemDefault(),
        kind: TitleKind? = null,
        now: Long = System.currentTimeMillis(),
        signIn: String? = null,
        installed: Set<String>? = null,
    ): HomeContent {
        // Each title carries its Netflix id, for the Netflix button (netflix-play).
        val state = NetflixRules.withIds(saved, now, signIn)
        if (kind != null) {
            val trakt = state.trakt ?: return HomeContent(slides = emptyList(), apps = emptyList(), rows = emptyList())
            val only = trakt.copy(
                continueWatching = trakt.continueWatching.filter { it.kind == kind },
                picks = trakt.picks.filter { it.kind == kind },
                watchlistRow = trakt.watchlistRow.filter { it.kind == kind },
            )
            val all = content(state.copy(trakt = only, youtube = emptyList()), zone, now = now, signIn = signIn)
            return all.copy(apps = emptyList())
        }
        val trakt = state.trakt
        val slides = buildList {
            if (trakt != null) {
                trakt.continueWatching.take(CONTINUE_SLIDES).forEach { add(continueSlide(it, zone)) }
                val picks = trakt.picks.take(PICK_SLIDES)
                val listed = trakt.watchlistRow.take(WATCHLIST_SLIDES)
                (0 until maxOf(picks.size, listed.size)).forEach { i ->
                    picks.getOrNull(i)?.let { add(pickSlide(it)) }
                    listed.getOrNull(i)?.let { add(pickSlide(it)) }
                }
            } else {
                state.stremio.take(CONTINUE_SLIDES).forEach { add(stremioSlide(it)) }
            }
        }
        val paused = trakt?.continueWatching?.map(::continueCard) ?: state.stremio.map(::videoCard)
        val fill = trakt?.let { TraktRules.continueFill(it.continueWatching, it.watchlistRow) }.orEmpty()
        val continueRow = WideRow(
            "Continue Watching",
            paused + fill.map(::fillCard),
            key = CONTINUE_WATCHING_ROW,
            fillStart = paused.size.takeIf { fill.isNotEmpty() },
        )
        val rows = listOfNotNull<ContentRow>(
            continueRow.takeIf { it.cards.isNotEmpty() },
            WideRow("Recommended on YouTube", state.youtube.map(::videoCard), key = "youtube").takeIf { it.cards.isNotEmpty() },
            PosterRow("Top Picks for You", trakt?.picks.orEmpty().map(::pickCard), key = "top-picks").takeIf { it.posters.isNotEmpty() },
            PosterRow("From Your Watchlist", trakt?.watchlistRow.orEmpty().drop(fill.size).map(::pickCard), key = "watchlist")
                .takeIf { it.posters.isNotEmpty() },
        )
        return HomeContent(slides = slides, apps = appsRow(installed), rows = rows, status = status(state, zone))
    }

    private fun status(state: HomeState, zone: ZoneId): String? {
        val failedAt = state.traktFailedAt ?: return null
        val saved = state.trakt?.savedAt
        return if (saved != null) "Trakt Unavailable. Saved at ${clock(saved, zone)}" else "Trakt Unavailable. No Saved Data."
    }

    private fun continueSlide(t: Title, zone: ZoneId): BannerSlide {
        val episode = t.episode
        val percent = ((t.progress ?: 0f) * 100).roundToInt()
        val meta = when {
            episode != null && t.upNextAt != null -> upNextLine(episode)
            episode != null -> listOfNotNull("Season ${episode.season}", "Episode ${episode.number}", t.pausedAt?.let { "Paused ${day(it, zone)}" }).joinToString(", ")
            else -> "Film, $percent% Watched"
        }
        return BannerSlide(
            key = "${SlideKind.ContinueWatching}:${identity(t)}",
            kind = SlideKind.ContinueWatching,
            eyebrow = "Continue Watching",
            title = t.title,
            meta = meta,
            synopsis = episode?.overview ?: t.overview.orEmpty(),
            art = t.backdropArt(),
            progress = t.progress,
            action = CardAction.OpenTitle(t),
        )
    }

    private fun pickSlide(t: Title) = BannerSlide(
        key = "${SlideKind.TopPick}:${identity(t)}",
        kind = SlideKind.TopPick,
        eyebrow = if (t.source == PickSource.Watchlist) "From Your Watchlist" else "Top Pick for You",
        title = t.title,
        meta = kindLine(t),
        synopsis = t.overview.orEmpty(),
        art = t.backdropArt(),
        action = CardAction.OpenTitle(t),
    )

    private fun stremioSlide(v: Video) = BannerSlide(
        key = "${SlideKind.ContinueWatching}:${v.intentUri ?: v.title}",
        kind = SlideKind.ContinueWatching,
        eyebrow = "Continue Watching",
        title = v.title,
        meta = listOfNotNull(v.subtitle, v.progress?.let { "${(it * 100).roundToInt()}% Watched" }).joinToString(", "),
        synopsis = "",
        art = stremioBackdrop(v),
        progress = v.progress,
        action = v.intentUri?.let(CardAction::OpenVideo),
    )

    /**
     * The banner for a Stremio program: Metahub's backdrop by IMDb id, then the program's poster
     * art. A picture that is the card's own picture is left out, so the banner and the card do
     * not repeat. Only when nothing else is left does the banner use the card's picture.
     */
    fun stremioBackdrop(v: Video): Art {
        val card = v.image
        fun sameAsCard(url: String) = url == card || (v.imdb != null && card != null && "/background/" in card && v.imdb in card && "/background/" in url)
        val others = listOfNotNull(v.imdb?.let { Metahub.url("background", it) }, v.poster).filterNot(::sameAsCard)
        return Art(others + listOfNotNull(card))
    }

    private fun continueCard(t: Title): WideCard {
        val percent = ((t.progress ?: 0f) * 100).roundToInt()
        // The meta line under a card keeps Google TV's middle dot. Up next: "S1 E5 · Up next" (25 Oct 2026).
        val episode = t.episode
        val meta = when {
            episode != null && t.upNextAt != null -> "S${episode.season} E${episode.number} · Up next"
            episode != null -> "S${episode.season} E${episode.number} · $percent% Watched"
            else -> "Film · $percent% Watched"
        }
        return WideCard(t.title, meta, t.cardArt(), progress = t.progress, action = CardAction.OpenTitle(t), key = identity(t))
    }

    /** The banner's line for an up-next episode: "Season 1, Episode 5, Up next" (25 Oct 2026). */
    private fun upNextLine(episode: EpisodeRef) = "Season ${episode.season}, Episode ${episode.number}, Up next"

    /** A watchlist title at the end of a short Continue Watching row: the year and genre, as on its poster. */
    private fun fillCard(t: Title) = WideCard(t.title, yearAndGenre(t), t.cardArt(), action = CardAction.OpenTitle(t), key = identity(t))

    private fun yearAndGenre(t: Title) = listOfNotNull(t.year?.toString(), t.genres.firstOrNull()?.let(::genre)).joinToString(" · ")

    /** A title's identity for its cards and slides: its kind and Trakt id, or its IMDb id, or its name. */
    private fun identity(t: Title) = t.key?.let { "${it.kind}:${it.traktId}" } ?: t.imdb ?: t.title

    private fun pickCard(t: Title) = PosterCard(
        title = t.title,
        // The row's name says where the title comes from, so the poster gives the year and genre,
        // with Google TV's middle dot.
        meta = yearAndGenre(t),
        art = t.posterArt(),
        action = CardAction.OpenTitle(t),
        key = identity(t),
    )

    private fun videoCard(v: Video) = WideCard(
        title = v.title,
        meta = v.subtitle,
        art = Art(listOfNotNull(v.image)),
        progress = v.progress,
        duration = v.durationMillis?.let(::duration),
        action = v.intentUri?.let(CardAction::OpenVideo),
        key = v.intentUri ?: v.title,
    )

    /**
     * A title's genre, type and year as a short sentence (25 Oct 2026): "Horror film from
     * 2018", "Drama series from 2012". The first genre only. "Horror film" with no year, "Film
     * from 2018" with no genre, "Film" with neither. The banner and the screensaver use it.
     */
    fun kindLine(t: Title): String {
        val type = if (t.kind == TitleKind.Movie) "film" else "series"
        val start = t.genres.firstOrNull()?.let { "${genre(it)} $type" } ?: type.replaceFirstChar { it.titlecase(Locale.UK) }
        return t.year?.let { "$start from $it" } ?: start
    }

    /** "science-fiction" as "Science Fiction". */
    fun genre(slug: String) = slug.split('-').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.UK) } }

    /** "26:35", or "1:02:03" for an hour or more. */
    fun duration(millis: Long): String {
        val total = millis / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun day(millis: Long, zone: ZoneId) = DateTimeFormatter.ofPattern("d MMM", Locale.UK).format(Instant.ofEpochMilli(millis).atZone(zone))

    private fun clock(millis: Long, zone: ZoneId) = DateTimeFormatter.ofPattern("HH:mm", Locale.UK).format(Instant.ofEpochMilli(millis).atZone(zone))
}
