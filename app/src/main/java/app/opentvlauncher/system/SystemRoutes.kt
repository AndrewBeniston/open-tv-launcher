package app.opentvlauncher.system

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.provider.Settings
import androidx.core.net.toUri
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.netflix.NetflixRules

/**
 * Ways out of the launcher that work on both TVs: the TV's own home screen and system Settings.
 * This is the undo route the brief asks for. It must keep working after the launcher becomes Home.
 */
object SystemRoutes {
    const val GOOGLE_TV_HOME = "com.google.android.apps.tv.launcherx"

    /**
     * Home candidates that are not home screens: Google TV's setup recovery screen and the TV
     * settings fallback. Android 14 sends Home to the recovery screen when Google TV Home is off.
     */
    private val NOT_HOME_SCREENS = setOf("com.google.android.tungsten.setupwraith", "com.android.tv.settings")

    /**
     * The TV's own home screen: Google TV Home on the Chromecast, or another Home app on the
     * Philips. The launcher itself and the screens in [NOT_HOME_SCREENS] are never chosen.
     * Returns null if the TV has no other Home app.
     */
    fun stockHome(context: Context): Intent? {
        val home = stockHomeActivity(context) ?: return null
        return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setClassName(home.packageName, home.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** The package and the app's own label of the TV's home screen ([stockHome]), or null. */
    fun stockHomeApp(context: Context): Pair<String, String>? {
        val home = stockHomeActivity(context) ?: return null
        val pm = context.packageManager
        val label = runCatching { home.applicationInfo.loadLabel(pm).toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: home.loadLabel(pm).toString()
        return home.packageName to label
    }

    private fun stockHomeActivity(context: Context): ActivityInfo? {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val others = context.packageManager.queryIntentActivities(homeIntent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && it.packageName !in NOT_HOME_SCREENS }
        return others.firstOrNull { it.packageName == GOOGLE_TV_HOME } ?: others.firstOrNull()
    }

    fun settings(): Intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Stremio's page for a film or show: `stremio:///detail/{movie|series}/{imdb id}`. Gate 5 in
     * the technical plan checked that it opens the right title. Null without an IMDb id.
     */
    fun stremioTitle(title: Title): Intent? {
        val imdb = title.imdb ?: return null
        val type = if (title.kind == TitleKind.Movie) "movie" else "series"
        return Intent(Intent.ACTION_VIEW, "stremio:///detail/$type/$imdb".toUri())
    }

    /**
     * The title's page in Netflix's TV app (25 Oct 2026). Netflix's assetlinks.json gives
     * the app every netflix.com link. Tested on the TV, with Netflix running on another title and
     * with Netflix stopped: Netflix opens the page only with the extra source=30. Without it,
     * Netflix shows the page it was on, with or without a new task. No flags, so a running Netflix
     * takes the link without a restart. A /watch link starts playback, which is not wanted here.
     * Null for an id that is not a number.
     */
    fun netflixTitle(netflixId: String): Intent? {
        if (!NetflixRules.validId(netflixId)) return null
        return Intent(Intent.ACTION_VIEW, "https://www.netflix.com/title/$netflixId".toUri())
            .setPackage(TitleApp.Netflix.packageName)
            .putExtra("source", "30")
    }
}
