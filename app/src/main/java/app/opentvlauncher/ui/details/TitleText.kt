package app.opentvlauncher.ui.details

import app.opentvlauncher.home.HomeMapper
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.system.TitleApp
import kotlin.math.roundToInt

/** The words on the details page, the hand-off screen, the app buttons and the Continue Watching menu. */
object TitleText {
    /** "2023, 2 h 4 min, Drama". A show says "Series" where a film has its runtime. */
    fun detailsMeta(title: Title): String = listOfNotNull(
        title.year?.toString(),
        if (title.kind == TitleKind.Movie) title.runtime?.takeIf { it > 0 }?.let(::runtime) else "Series",
        title.genres.take(2).takeIf { it.isNotEmpty() }?.joinToString(", ") { HomeMapper.genre(it) },
    ).joinToString(", ")

    /** "79%", from Trakt's 0 to 10 rating, with no source credit (a design rule). Null without a rating. */
    fun rating(title: Title): String? = title.rating?.takeIf { it > 0 }?.let { "${(it * 10).roundToInt()}%" }

    /** "Season 5, Episode 6", "Film, 53% Watched", or "Film, 2023". */
    fun handOffMeta(title: Title): String {
        val episode = title.episode
        val progress = title.progress
        return when {
            episode != null -> "Season ${episode.season}, Episode ${episode.number}"
            progress != null -> "Film, ${(progress * 100).roundToInt()}% Watched"
            else -> listOfNotNull(if (title.kind == TitleKind.Movie) "Film" else "Series", title.year?.toString()).joinToString(", ")
        }
    }

    /**
     * An app button's name (25 Oct 2026): the app's own name, "Stremio" or "Netflix".
     * Stremio's button says "Resume" for a title in progress.
     */
    fun appLabel(title: Title, app: TitleApp): String = if (app == TitleApp.Stremio && title.progress != null) "Resume" else app.label

    /** "2 h 4 min", "58 min" or "2 h". */
    fun runtime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }
}
