package app.opentvlauncher.system

import app.opentvlauncher.home.Title
import app.opentvlauncher.tv.TvChannels

/**
 * The apps that a title's buttons open, in button order: Stremio first, then Netflix
 * (25 Oct 2026). Each button shows the app's mark (TitleAppIcons) and name.
 */
enum class TitleApp(val packageName: String, val label: String) {
    Stremio(TvChannels.STREMIO, "Stremio"),
    Netflix("com.netflix.ninja", "Netflix"),
    ;

    companion object {
        /**
         * The buttons for [title], given the [installed] packages. An app that is not installed has
         * no button, and Netflix shows only for a title with a Netflix id. Before the first read of
         * the installed apps ([installed] is null), Stremio shows and Netflix waits.
         */
        fun forTitle(title: Title, installed: Set<String>?): List<TitleApp> = buildList {
            if (installed == null || Stremio.packageName in installed) add(Stremio)
            if (title.netflixId != null && installed != null && Netflix.packageName in installed) add(Netflix)
        }
    }
}
