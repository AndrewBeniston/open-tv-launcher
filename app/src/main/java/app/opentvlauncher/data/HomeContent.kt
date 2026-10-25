package app.opentvlauncher.data

import androidx.compose.runtime.Immutable
import app.opentvlauncher.home.Title

/** Everything the home screen draws: built from the saved HomeState (HomeMapper), or the tests' own sample. */
@Immutable
data class HomeContent(
    val slides: List<BannerSlide>,
    val apps: List<AppTile>,
    val rows: List<ContentRow>,
    /** The quiet chip next to the clock, such as "Trakt Unavailable. Saved at 21:40". */
    val status: String? = null,
)

/** What OK does on a card or a banner button. */
@Immutable
sealed interface CardAction {
    /** A film or show from Trakt. The Selecting a Title setting decides where it opens. */
    data class OpenTitle(val title: Title) : CardAction

    /** A program from another app's TV channel, opened through the app's own link. */
    data class OpenVideo(val intentUri: String) : CardAction

    /** A Trakt title's page in Netflix: the banner's Netflix button (netflix-play). */
    data class OpenNetflix(val title: Title) : CardAction
}

enum class SlideKind { ContinueWatching, TopPick }

/**
 * [key] is the slide's identity: the same slide after a refresh, even when its progress or meta
 * has changed, and the slide Home gives back after All Apps. A Trakt title's slide uses its
 * TitleKey, and a TV channel program's slide its link, never the title text: two films can have
 * the same name.
 */
@Immutable
data class BannerSlide(
    val key: String,
    val kind: SlideKind,
    val eyebrow: String,
    val title: String,
    val meta: String,
    val synopsis: String,
    val art: Art,
    /** Watched fraction, 0 to 1. Only Continue Watching slides have one. */
    val progress: Float? = null,
    val action: CardAction? = null,
)

enum class AppKind { YouTube, Stremio, Netflix, IPlayer, AllApps }

@Immutable
data class AppTile(
    val kind: AppKind,
    val label: String,
    /** The app to open. All Apps has none. */
    val packageName: String?,
)

/** The key of the Continue Watching row. Its cards open the long-press menu. */
const val CONTINUE_WATCHING_ROW = "continue-watching"

/**
 * A row of cards. [key] names the row's role, such as "top-picks", so that a refresh that adds,
 * removes or moves rows keeps focus on the same row.
 */
@Immutable
sealed interface ContentRow {
    val title: String
    val key: String
}

/**
 * A row of 16:9 cards: Continue Watching and Recommended on YouTube. [fillStart] is the index of
 * the first watchlist title in a short Continue Watching row. A wider gap goes before it.
 */
@Immutable
data class WideRow(
    override val title: String,
    val cards: List<WideCard>,
    override val key: String = title,
    val fillStart: Int? = null,
) : ContentRow

/** A row of posters: Top Picks for You, From Your Watchlist. */
@Immutable
data class PosterRow(override val title: String, val posters: List<PosterCard>, override val key: String = title) : ContentRow

@Immutable
data class WideCard(
    val title: String,
    val meta: String?,
    val art: Art,
    val progress: Float? = null,
    val duration: String? = null,
    val action: CardAction? = null,
    /** The title or video the card shows, so focus stays on it when a refresh reorders the row. */
    val key: String = title,
)

@Immutable
data class PosterCard(
    val title: String,
    val meta: String,
    val art: Art,
    val action: CardAction? = null,
    val key: String = title,
)
